package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Meteora DLMM 的 LbPair 链上账户解析器。
 *
 * <p>
 * 主要用途：
 * <ul>
 *     <li>解析 Solana RPC 返回的 Meteora DLMM LbPair 原始账户数据</li>
 *     <li>读取池子的静态手续费参数</li>
 *     <li>读取池子的动态手续费参数</li>
 *     <li>读取当前 activeId</li>
 *     <li>读取 X/Y Mint</li>
 *     <li>读取 X/Y Reserve</li>
 *     <li>读取 Oracle</li>
 *     <li>读取 BinArray Bitmap</li>
 *     <li>判断 Token Program / Token-2022 Program</li>
 *     <li>计算当前 Active BinArray Index</li>
 * </ul>
 *
 * <p>
 * 注意：
 * <ul>
 *     <li>Solana / Meteora 链上整数均按 Little Endian 读取</li>
 *     <li>Java 没有原生 u64 / u128，因此使用 BigInteger 保存</li>
 *     <li>PublicKey 链上固定为 32 字节</li>
 *     <li>activeId、VariableParameters 等属于动态状态，会随交易变化</li>
 * </ul>
 */
public final class MeteoraDlmmLbPairParser {

    /**
     * Meteora DLMM 主网程序地址。
     *
     * <p>
     * 所有 LbPair、BinArray、Oracle、Reserve 等 PDA
     * 都归属于该 Program。
     */
    public static final PublicKey DLMM_PROGRAM_ID = new PublicKey("LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo");

    /**
     * Solana 标准 SPL Token Program。
     *
     * <p>
     * 当：
     * <pre>
     * token_mint_x_program_flag = 0
     * </pre>
     * 或：
     * <pre>
     * token_mint_y_program_flag = 0
     * </pre>
     * 时对应此 Program。
     */
    public static final PublicKey TOKEN_PROGRAM = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    /**
     * Solana Token-2022 Program。
     *
     * <p>
     * 当：
     * <pre>
     * token_mint_x_program_flag = 1
     * </pre>
     * 或：
     * <pre>
     * token_mint_y_program_flag = 1
     * </pre>
     * 时对应此 Program。
     */
    public static final PublicKey TOKEN_2022_PROGRAM = new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");

    /**
     * Meteora DLMM LbPair 的 Anchor Account Discriminator。
     *
     * <p>
     * 计算方式：
     * <pre>
     * sha256("account:LbPair")[0..8]
     * </pre>
     *
     * <p>
     * 十六进制：
     * <pre>
     * 21 0b 31 62 b5 65 b1 0d
     * </pre>
     *
     * <p>
     * 用于判断传入的账户数据是否确实属于 LbPair。
     */
    private static final byte[] LB_PAIR_DISCRIMINATOR = {33, 11, 49, 98, (byte) 181, 101, (byte) 177, 13};
    /** BinArray 地址只由池地址与索引决定；每个线程保留近期结果，避免重复曲线检查。 */
    private static final int MAX_CACHED_BIN_ARRAYS = 512;
    private static final class BinArrayCacheKey {
        private final byte[] pool;
        private final long index;
        private final int hash;

        private BinArrayCacheKey(PublicKey lbPair, long index) {
            pool = lbPair.toByteArray().clone();
            this.index = index;
            hash = 31 * Arrays.hashCode(pool) + Long.hashCode(index);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof BinArrayCacheKey key
                    && index == key.index && Arrays.equals(pool, key.pool);
        }
    }
    private static final ThreadLocal<Map<BinArrayCacheKey, byte[]>> BIN_ARRAY_CACHE = ThreadLocal.withInitial(() ->
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BinArrayCacheKey, byte[]> eldest) {
                    return size() > MAX_CACHED_BIN_ARRAYS;
                }
            });

    /**
     * 当前版本 Meteora LbPair 完整账户长度。
     *
     * <p>
     * 包含：
     * <pre>
     * 8   bytes Anchor discriminator
     * 896 bytes LbPair struct
     * -------------------------
     * 904 bytes
     * </pre>
     */
    public static final int LB_PAIR_ACCOUNT_SIZE = 904;

    /**
     * 一个 Meteora BinArray 包含的 Bin 数量。
     *
     * <p>
     * 当前一个 BinArray 包含：
     * <pre>
     * 70 个 Bin
     * </pre>
     */
    public static final int BIN_ARRAY_SIZE = 70;


    // ============================================================
    // 解析入口
    // ============================================================

    /**
     * 从 RPC 返回的 Base64 编码账户数据解析 LbPair。
     *
     * @param base64 RPC getAccountInfo 返回的 account.data Base64 字符串
     * @return 解析完成的 LbPair 对象
     * @throws IllegalArgumentException Base64 解码后的内容不是合法 LbPair 时抛出
     */
    public static LbPair parseBase64(String base64) {

        // 将 RPC Base64 数据解码成原始链上 byte[]
        byte[] data = Base64.getDecoder().decode(base64);

        // 调用统一的 byte[] 解析逻辑
        return parse(data);
    }

    /**
     * 从完整的 Meteora LbPair 链上原始账户数据解析。
     *
     * @param data 完整 Solana account.data 原始字节数组，
     *             必须包含 8 字节 Anchor discriminator
     * @return 解析完成的 LbPair
     * @throws IllegalArgumentException 数据为空、长度不足或 discriminator 不匹配时抛出
     * @throws IllegalStateException    最终解析 offset 与预期账户长度不一致时抛出
     */
    public static LbPair parse(byte[] data) {

        // 防止传入 null
        if (data == null) {
            throw new IllegalArgumentException("LbPair 数据不能为空");
        }

        // 检查账户数据长度
        if (data.length < LB_PAIR_ACCOUNT_SIZE) {
            throw new IllegalArgumentException("LbPair 数据长度错误，至少需要 " + LB_PAIR_ACCOUNT_SIZE + " 字节，实际只有 " + data.length);
        }

        // 创建 Little Endian Reader
        Reader r = new Reader(data);


        // ========================================================
        // 1. Anchor Account Discriminator
        // ========================================================

        /**
         * 前 8 字节为 Anchor discriminator。
         */
        byte[] discriminator = r.bytes(8);

        // 检查是否为 LbPair
        if (!Arrays.equals(discriminator, LB_PAIR_DISCRIMINATOR)) {
            throw new IllegalArgumentException("不是 Meteora DLMM LbPair account，" + "discriminator 不匹配");
        }


        // ========================================================
        // 2. StaticParameters
        // ========================================================

        /**
         * 静态手续费参数。
         *
         * <p>
         * 这些参数通常在池创建时确定，
         * 普通 swap 不会频繁修改。
         */
        StaticParameters parameters = parseStaticParameters(r);


        // ========================================================
        // 3. VariableParameters
        // ========================================================

        /**
         * 动态手续费参数。
         *
         * <p>
         * 这些参数可能随着 swap、时间和价格变化。
         */
        VariableParameters variableParameters = parseVariableParameters(r);


        // ========================================================
        // 4. bump_seed
        // ========================================================

        /**
         * LbPair PDA 对应的 bump。
         *
         * 范围：
         * 0 ~ 255
         */
        int bumpSeed = r.u8();


        // ========================================================
        // 5. bin_step_seed
        // ========================================================

        /**
         * bin_step 的原始 PDA seed。
         *
         * <p>
         * 长度固定 2 字节。
         *
         * <p>
         * 例如：
         * <pre>
         * binStep = 80
         *
         * u16 Little Endian:
         *
         * 50 00
         *
         * Java 十进制：
         *
         * [80, 0]
         * </pre>
         */
        byte[] binStepSeed = r.bytes(2);


        // ========================================================
        // 6. pair_type
        // ========================================================

        /**
         * DLMM Pool 类型。
         *
         * <p>
         * 保留 Meteora 链上的原始 u8 enum 值。
         */
        int pairType = r.u8();


        // ========================================================
        // 7. active_id
        // ========================================================

        /**
         * 当前正在成交的 Active Bin ID。
         *
         * <p>
         * 这是非常重要的动态状态。
         *
         * <p>
         * 例如：
         * <pre>
         * activeId = -623
         * </pre>
         *
         * 当前所在 BinArray：
         * <pre>
         * floorDiv(-623, 70) = -9
         * </pre>
         */
        int activeId = r.i32();


        // ========================================================
        // 8. bin_step
        // ========================================================

        /**
         * 每个相邻 Bin 之间的价格步长参数。
         */
        int binStep = r.u16();


        // ========================================================
        // 9. status
        // ========================================================

        /**
         * 当前池状态。
         *
         * <p>
         * 用于判断池当前是否允许相关操作。
         */
        int status = r.u8();


        // ========================================================
        // 10. require_base_factor_seed
        // ========================================================

        /**
         * 是否要求 baseFactor 参与池 PDA seed。
         */
        int requireBaseFactorSeed = r.u8();


        // ========================================================
        // 11. base_factor_seed
        // ========================================================

        /**
         * base_factor 对应的原始 Little Endian seed。
         *
         * <p>
         * 长度固定为 2 字节。
         *
         * <p>
         * 例如：
         * <pre>
         * baseFactor = 125
         *
         * seed = [125, 0]
         * </pre>
         */
        byte[] baseFactorSeed = r.bytes(2);


        // ========================================================
        // 12. activation_type
        // ========================================================

        /**
         * 池激活条件类型。
         *
         * <p>
         * 保留链上的原始 enum 数值。
         */
        int activationType = r.u8();


        // ========================================================
        // 13. creator_pool_on_off_control
        // ========================================================

        /**
         * 是否允许 Pool Creator 控制 Pool 开关状态。
         */
        int creatorPoolOnOffControl = r.u8();


        // ========================================================
        // 14. token_x_mint
        // ========================================================

        /**
         * X 侧 Token Mint。
         */
        PublicKey tokenXMint = r.pubkey();


        // ========================================================
        // 15. token_y_mint
        // ========================================================

        /**
         * Y 侧 Token Mint。
         */
        PublicKey tokenYMint = r.pubkey();


        // ========================================================
        // 16. reserve_x
        // ========================================================

        /**
         * 池子的 X Token Reserve / Vault。
         */
        PublicKey reserveX = r.pubkey();


        // ========================================================
        // 17. reserve_y
        // ========================================================

        /**
         * 池子的 Y Token Reserve / Vault。
         */
        PublicKey reserveY = r.pubkey();


        // ========================================================
        // 18. protocol_fee
        // ========================================================

        /**
         * 当前池已经累计、但尚未提取的 Protocol Fee。
         *
         * amountX = X Token 协议手续费
         * amountY = Y Token 协议手续费
         */
        ProtocolFee protocolFee = new ProtocolFee(r.u64(), r.u64());


        // ========================================================
        // 19. padding_1
        // ========================================================

        /**
         * Meteora 为结构升级 / 内存对齐保留的 32 字节。
         *
         * 不参与交易逻辑。
         */
        r.skip(32);


        // ========================================================
        // 20. reward_infos
        // ========================================================

        /**
         * LbPair 固定包含两个 RewardInfo。
         */
        List<RewardInfo> rewardInfos = new ArrayList<>(2);

        rewardInfos.add(parseRewardInfo(r));

        rewardInfos.add(parseRewardInfo(r));


        // ========================================================
        // 21. oracle
        // ========================================================

        /**
         * 当前 DLMM Pool 对应的 Oracle PDA。
         */
        PublicKey oracle = r.pubkey();


        // ========================================================
        // 22. bin_array_bitmap
        // ========================================================

        /**
         * LbPair 自带的 BinArray 初始化位图。
         *
         * <p>
         * 类型：
         * <pre>
         * u64[16]
         * </pre>
         *
         * <p>
         * 共：
         * <pre>
         * 16 * 64 = 1024 bit
         * </pre>
         *
         * 用于判断哪些 BinArray 已经初始化。
         *
         * <p>
         * 注意：
         * Java long 是 signed 64-bit，
         * 而链上是 unsigned u64，
         * 所以这里必须使用 BigInteger。
         */
        BigInteger[] binArrayBitmap = new BigInteger[16];

        for (int i = 0; i < 16; i++) {
            binArrayBitmap[i] = r.u64();
        }


        // ========================================================
        // 23. last_updated_at
        // ========================================================

        /**
         * LbPair 保存的最后更新时间字段。
         *
         * <p>
         * 原始类型为 i64。
         */
        long lastUpdatedAt = r.i64();


        // ========================================================
        // 24. padding_2
        // ========================================================

        /**
         * 预留 32 字节，不参与业务逻辑。
         */
        r.skip(32);


        // ========================================================
        // 25. pre_activation_swap_address
        // ========================================================

        /**
         * Pre-Activation 阶段允许执行 Swap 的地址。
         *
         * <p>
         * 如果是：
         * <pre>
         * 11111111111111111111111111111111
         * </pre>
         * 通常表示没有配置具体地址。
         */
        PublicKey preActivationSwapAddress = r.pubkey();


        // ========================================================
        // 26. base_key
        // ========================================================

        /**
         * 创建 / 派生当前池时使用的 Base Key。
         */
        PublicKey baseKey = r.pubkey();


        // ========================================================
        // 27. activation_point
        // ========================================================

        /**
         * Pool 激活点。
         *
         * <p>
         * 根据 activationType，
         * 可能表示 slot 或 Unix timestamp 等。
         *
         * <p>
         * 原始类型为 u64。
         */
        BigInteger activationPoint = r.u64();


        // ========================================================
        // 28. pre_activation_duration
        // ========================================================

        /**
         * Pool 正式激活之前的 Pre-Activation 时间范围。
         *
         * 原始类型为 u64。
         */
        BigInteger preActivationDuration = r.u64();


        // ========================================================
        // 29. padding_3
        // ========================================================

        /**
         * 预留 8 字节。
         */
        r.skip(8);


        // ========================================================
        // 30. padding_4
        // ========================================================

        /**
         * 预留 u64。
         *
         * 共 8 字节。
         */
        r.skip(8);


        // ========================================================
        // 31. creator
        // ========================================================

        /**
         * Pool Creator 地址。
         */
        PublicKey creator = r.pubkey();


        // ========================================================
        // 32. token_mint_x_program_flag
        // ========================================================

        /**
         * X Mint 使用的 Token Program 类型。
         *
         * <pre>
         * 0 = SPL Token
         * 1 = Token-2022
         * </pre>
         */
        int tokenMintXProgramFlag = r.u8();


        // ========================================================
        // 33. token_mint_y_program_flag
        // ========================================================

        /**
         * Y Mint 使用的 Token Program 类型。
         *
         * <pre>
         * 0 = SPL Token
         * 1 = Token-2022
         * </pre>
         */
        int tokenMintYProgramFlag = r.u8();


        // ========================================================
        // 34. version
        // ========================================================

        /**
         * 当前 LbPair 数据结构版本。
         */
        int version = r.u8();


        // ========================================================
        // 35. reserved
        // ========================================================

        /**
         * Meteora 预留的 21 字节。
         *
         * 当前业务逻辑不使用。
         */
        byte[] reserved = r.bytes(21);


        // ========================================================
        // 长度验证
        // ========================================================

        /**
         * 当前版本完整 LbPair 应该正好解析到 904 字节。
         *
         * 如果不是，则很可能：
         *
         * 1. 字段顺序错了
         * 2. 字段类型错了
         * 3. Meteora 升级了结构
         */
        if (r.position() != LB_PAIR_ACCOUNT_SIZE) {
            throw new IllegalStateException("LbPair 解析长度异常，当前 offset=" + r.position() + "，预期=" + LB_PAIR_ACCOUNT_SIZE);
        }

        return new LbPair(parameters, variableParameters,

                bumpSeed, binStepSeed, pairType,

                activeId, binStep, status,

                requireBaseFactorSeed, baseFactorSeed,

                activationType, creatorPoolOnOffControl,

                tokenXMint, tokenYMint,

                reserveX, reserveY,

                protocolFee, List.copyOf(rewardInfos),

                oracle, binArrayBitmap,

                lastUpdatedAt,

                preActivationSwapAddress, baseKey,

                activationPoint, preActivationDuration,

                creator,

                tokenMintXProgramFlag, tokenMintYProgramFlag,

                version, reserved);
    }

    /**
     * 将通用账户解析结果转换为交易构造器使用的 LbPair 对象。
     * 传入数据应来自 MeteoraDlmmParser.parsePool(owner, bytes) 或
     * DexParsers.parsePool(owner, bytes)；这一步只映射已解析字段，不读取 RPC，
     * 也不会再次解析原始字节或布局 JSON。生产环境应在初次解析时验证账户 owner。
     *
     * @param state 已解析的 Meteora DLMM LbPair 状态
     * @return 与现有 parse(byte[]) 返回类型相同的 LbPair 对象
     * @throws IllegalArgumentException 账户类型或固定数组长度不符合 LbPair 时抛出
     */
    public static LbPair fromDexAccountData(DexAccountData state) {
        if (state == null || !"LbPair".equals(state.accountType())) {
            throw new IllegalArgumentException("需要 Meteora DLMM 的 LbPair 解析结果");
        }

        // 静态和动态手续费参数保留原记录类型；u32 使用 long，避免有符号 int 溢出。
        StaticParameters parameters = new StaticParameters(state.intValue("parameters.base_factor"), state.intValue("parameters.filter_period"), state.intValue("parameters.decay_period"), state.intValue("parameters.reduction_factor"), state.longValueExact("parameters.variable_fee_control"), state.longValueExact("parameters.max_volatility_accumulator"), state.intValue("parameters.min_bin_id"), state.intValue("parameters.max_bin_id"), state.intValue("parameters.protocol_share"), state.intValue("parameters.base_fee_power_factor"), state.intValue("parameters.function_type"), state.intValue("parameters.collect_fee_mode"));

        VariableParameters variableParameters = new VariableParameters(state.longValueExact("v_parameters.volatility_accumulator"), state.longValueExact("v_parameters.volatility_reference"), state.intValue("v_parameters.index_reference"), state.longValueExact("v_parameters.last_update_timestamp"));

        byte[] bumpSeed = state.bytes("bump_seed");
        byte[] binStepSeed = state.bytes("bin_step_seed");
        byte[] baseFactorSeed = state.bytes("base_factor_seed");
        byte[] reserved = state.bytes("_reserved");
        if (bumpSeed.length != 1 || binStepSeed.length != 2 || baseFactorSeed.length != 2 || reserved.length != 21) {
            throw new IllegalArgumentException("LbPair 固定字节数组长度不匹配");
        }

        // 协议费和奖励金额保留 BigInteger，避免 u64/u128 被截断。
        ProtocolFee protocolFee = new ProtocolFee(state.integer("protocol_fee.amount_x"), state.integer("protocol_fee.amount_y"));
        if (state.list("reward_infos").size() != 2) {
            throw new IllegalArgumentException("LbPair 必须包含两个 RewardInfo");
        }
        List<RewardInfo> rewardInfos = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            String field = "reward_infos[" + i + "]";
            rewardInfos.add(new RewardInfo(state.publicKey(field + ".mint"), state.publicKey(field + ".vault"), state.publicKey(field + ".funder"), state.integer(field + ".reward_duration"), state.integer(field + ".reward_duration_end"), state.integer(field + ".reward_rate"), state.integer(field + ".last_update_time"), state.integer(field + ".cumulative_seconds_with_empty_liquidity_reward")));
        }

        if (state.list("bin_array_bitmap").size() != 16) {
            throw new IllegalArgumentException("LbPair 位图必须包含十六个 u64");
        }
        BigInteger[] bitmap = new BigInteger[16];
        for (int i = 0; i < bitmap.length; i++) {
            bitmap[i] = state.integer("bin_array_bitmap[" + i + "]");
        }

        return new LbPair(parameters, variableParameters, Byte.toUnsignedInt(bumpSeed[0]), binStepSeed, state.intValue("pair_type"), state.intValue("active_id"), state.intValue("bin_step"), state.intValue("status"), state.intValue("require_base_factor_seed"), baseFactorSeed, state.intValue("activation_type"), state.intValue("creator_pool_on_off_control"), state.publicKey("token_x_mint"), state.publicKey("token_y_mint"), state.publicKey("reserve_x"), state.publicKey("reserve_y"), protocolFee, List.copyOf(rewardInfos), state.publicKey("oracle"), bitmap, state.longValueExact("last_updated_at"), state.publicKey("pre_activation_swap_address"), state.publicKey("base_key"), state.integer("activation_point"), state.integer("pre_activation_duration"), state.publicKey("creator"), state.intValue("token_mint_x_program_flag"), state.intValue("token_mint_y_program_flag"), state.intValue("version"), reserved);
    }


    // ============================================================
    // StaticParameters
    // ============================================================

    /**
     * 解析 Meteora DLMM StaticParameters。
     *
     * <p>
     * 这些主要是池子的静态手续费配置。
     *
     * @param r 当前 LbPair 二进制数据读取器；
     *          调用时 Reader 必须已经位于 StaticParameters 起始位置
     * @return 解析完成的 StaticParameters
     */
    private static StaticParameters parseStaticParameters(Reader r) {

        // 基础手续费计算因子
        int baseFactor = r.u16();

        // 波动率过滤周期
        int filterPeriod = r.u16();

        // 动态波动率衰减周期
        int decayPeriod = r.u16();

        // volatilityReference 衰减时使用的降低系数
        int reductionFactor = r.u16();

        // 动态手续费控制参数
        long variableFeeControl = r.u32();

        // volatilityAccumulator 允许达到的最大值
        long maxVolatilityAccumulator = r.u32();

        // Pool 允许的最小 Bin ID
        int minBinId = r.i32();

        // Pool 允许的最大 Bin ID
        int maxBinId = r.i32();

        // Protocol 从手续费中获得的分成比例
        int protocolShare = r.u16();

        // Base Fee 计算使用的指数参数
        int baseFeePowerFactor = r.u8();

        // 手续费函数类型
        int functionType = r.u8();

        // 手续费收取模式
        int collectFeeMode = r.u8();

        // C Struct 对齐预留 3 字节
        r.skip(3);

        return new StaticParameters(baseFactor, filterPeriod, decayPeriod, reductionFactor, variableFeeControl, maxVolatilityAccumulator, minBinId, maxBinId, protocolShare, baseFeePowerFactor, functionType, collectFeeMode);
    }


    // ============================================================
    // VariableParameters
    // ============================================================

    /**
     * 解析 Meteora DLMM VariableParameters。
     *
     * <p>
     * 这些参数属于动态状态，
     * 会随着交易和时间发生变化。
     *
     * @param r 当前 LbPair 二进制读取器；
     *          Reader 必须位于 VariableParameters 起始位置
     * @return 解析完成的 VariableParameters
     */
    private static VariableParameters parseVariableParameters(Reader r) {

        // 当前累计波动率
        long volatilityAccumulator = r.u32();

        // 上一次计算后保存的波动率参考值
        long volatilityReference = r.u32();

        // 动态手续费计算使用的参考 Bin ID
        int indexReference = r.i32();

        // C Struct 对齐预留
        r.skip(4);

        // 动态参数最后更新时间
        long lastUpdateTimestamp = r.i64();

        // C Struct 尾部对齐预留
        r.skip(8);

        return new VariableParameters(volatilityAccumulator, volatilityReference, indexReference, lastUpdateTimestamp);
    }


    // ============================================================
    // RewardInfo
    // ============================================================

    /**
     * 解析一个 Meteora DLMM RewardInfo。
     *
     * @param r 当前 LbPair 二进制读取器；
     *          Reader 必须位于某个 RewardInfo 的起始位置
     * @return 解析完成的 RewardInfo
     */
    private static RewardInfo parseRewardInfo(Reader r) {

        // Reward Token Mint
        PublicKey mint = r.pubkey();

        // Reward Token Vault
        PublicKey vault = r.pubkey();

        // Reward 资金提供者
        PublicKey funder = r.pubkey();

        // Reward 发放持续时间
        BigInteger rewardDuration = r.u64();

        // Reward 发放结束时间
        BigInteger rewardDurationEnd = r.u64();

        // Reward 发放速率
        //
        // 链上是 u128，所以必须使用 BigInteger
        BigInteger rewardRate = r.u128();

        // Reward 状态最后更新时间
        BigInteger lastUpdateTime = r.u64();

        // 没有流动性期间累计经过的秒数
        BigInteger cumulativeSecondsWithEmptyLiquidityReward = r.u64();

        return new RewardInfo(mint, vault, funder, rewardDuration, rewardDurationEnd, rewardRate, lastUpdateTime, cumulativeSecondsWithEmptyLiquidityReward);
    }


    // ============================================================
    // Token Program 判断
    // ============================================================

    /**
     * 根据 LbPair 中的 Token Program Flag
     * 返回对应的 Solana Token Program。
     *
     * <p>
     * 当前映射：
     * <pre>
     * 0 -> SPL Token
     * 1 -> Token-2022
     * </pre>
     *
     * @param flag LbPair 中的 token_mint_x_program_flag
     *             或 token_mint_y_program_flag
     * @return 对应的 SPL Token Program 或 Token-2022 Program
     * @throws IllegalArgumentException 遇到未知 flag 时抛出
     */
    public static PublicKey tokenProgramFromFlag(int flag) {

        return switch (flag) {

            // 标准 SPL Token Program
            case 0 -> TOKEN_PROGRAM;

            // Token-2022 Program
            case 1 -> TOKEN_2022_PROGRAM;

            // 当前解析器不认识的 Program 类型
            default -> throw new IllegalArgumentException("未知 Token Program Flag: " + flag);
        };
    }


    // ============================================================
    // Active BinArray
    // ============================================================

    /**
     * 根据 activeId 计算当前所在的 BinArray Index。
     *
     * <p>
     * 一个 BinArray 固定包含 70 个 Bin。
     *
     * <p>
     * 特别注意：
     * Java 普通整数除法对负数是向 0 截断，
     * 不能直接写：
     *
     * <pre>
     * activeId / 70
     * </pre>
     * <p>
     * 必须使用：
     *
     * <pre>
     * Math.floorDiv(activeId, 70)
     * </pre>
     *
     * <p>
     * 例如：
     *
     * <pre>
     * activeId = -623
     *
     * -623 / 70 = -8.9
     *
     * floor(-8.9) = -9
     * </pre>
     *
     * @param activeId 当前 LbPair 的 active_id
     * @return activeId 所在的 BinArray Index
     */
    public static long activeBinArrayIndex(int activeId) {

        return Math.floorDiv((long) activeId, BIN_ARRAY_SIZE);
    }


    // ============================================================
    // 数据对象
    // ============================================================

    /**
     * Meteora DLMM 静态手续费参数。
     *
     * @param baseFactor               基础手续费计算因子
     * @param filterPeriod             波动率过滤周期
     * @param decayPeriod              波动率衰减周期
     * @param reductionFactor          波动率参考值的降低系数
     * @param variableFeeControl       动态手续费控制参数
     * @param maxVolatilityAccumulator 最大允许的 volatility accumulator
     * @param minBinId                 当前参数允许的最小 Bin ID
     * @param maxBinId                 当前参数允许的最大 Bin ID
     * @param protocolShare            Protocol 从交易手续费中获得的分成比例
     * @param baseFeePowerFactor       Base Fee 计算使用的指数参数
     * @param functionType             Meteora 手续费函数类型
     * @param collectFeeMode           手续费收取模式
     */
    public record StaticParameters(

            int baseFactor,

            int filterPeriod,

            int decayPeriod,

            int reductionFactor,

            long variableFeeControl,

            long maxVolatilityAccumulator,

            int minBinId,

            int maxBinId,

            int protocolShare,

            int baseFeePowerFactor,

            int functionType,

            int collectFeeMode) {
    }


    /**
     * Meteora DLMM 动态手续费参数。
     *
     * <p>
     * 这些字段不是静态配置，
     * 可能随着 Swap 和时间发生变化。
     *
     * @param volatilityAccumulator 当前累计波动率
     * @param volatilityReference   动态手续费计算使用的波动率参考值
     * @param indexReference        动态手续费计算使用的参考 Bin ID
     * @param lastUpdateTimestamp   动态参数最后更新时间戳
     */
    public record VariableParameters(

            long volatilityAccumulator,

            long volatilityReference,

            int indexReference,

            long lastUpdateTimestamp) {
    }


    /**
     * Meteora DLMM 当前累计的 Protocol Fee。
     *
     * @param amountX 当前累计的 X Token 协议手续费，链上类型 u64
     * @param amountY 当前累计的 Y Token 协议手续费，链上类型 u64
     */
    public record ProtocolFee(

            BigInteger amountX,

            BigInteger amountY) {
    }


    /**
     * Meteora DLMM Reward 配置和累计状态。
     *
     * @param mint                                      Reward Token Mint
     * @param vault                                     Reward Token Vault
     * @param funder                                    Reward 资金提供地址
     * @param rewardDuration                            Reward 发放持续时间
     * @param rewardDurationEnd                         Reward 发放结束时间
     * @param rewardRate                                Reward 发放速率，链上类型 u128
     * @param lastUpdateTime                            Reward 状态最后更新时间
     * @param cumulativeSecondsWithEmptyLiquidityReward 没有有效流动性期间累计经过的秒数
     */
    public record RewardInfo(

            PublicKey mint,

            PublicKey vault,

            PublicKey funder,

            BigInteger rewardDuration,

            BigInteger rewardDurationEnd,

            BigInteger rewardRate,

            BigInteger lastUpdateTime,

            BigInteger cumulativeSecondsWithEmptyLiquidityReward) {
    }


    /**
     * 完整 Meteora DLMM LbPair 链上状态。
     *
     * @param parameters               池子的静态手续费参数
     * @param variableParameters       池子的动态手续费参数
     * @param bumpSeed                 LbPair PDA bump
     * @param binStepSeed              binStep 对应的原始 2 字节 PDA Seed
     * @param pairType                 DLMM Pool 类型
     * @param activeId                 当前正在成交的 Active Bin ID，会随 Swap 动态变化
     * @param binStep                  相邻 Bin 之间的价格步长参数
     * @param status                   当前 Pool 状态
     * @param requireBaseFactorSeed    是否要求 baseFactor 参与 PDA Seed
     * @param baseFactorSeed           baseFactor 对应的原始 2 字节 Seed
     * @param activationType           Pool 激活条件类型
     * @param creatorPoolOnOffControl  Creator 是否具有 Pool 开关控制能力
     * @param tokenXMint               X 侧 Token Mint
     * @param tokenYMint               Y 侧 Token Mint
     * @param reserveX                 X Token 的池储备账户
     * @param reserveY                 Y Token 的池储备账户
     * @param protocolFee              当前累计但尚未提取的协议手续费
     * @param rewardInfos              Pool 的两个 Reward 配置
     * @param oracle                   当前 LbPair 对应的 Oracle PDA
     * @param binArrayBitmap           当前 LbPair 自带的 BinArray 初始化位图，
     *                                 类型为 u64[16]，总共 1024 bit
     * @param lastUpdatedAt            LbPair 保存的最后更新时间字段
     * @param preActivationSwapAddress Pre-Activation 阶段允许执行 Swap 的地址
     * @param baseKey                  创建或派生当前 Pool 使用的 Base Key
     * @param activationPoint          Pool 激活点，具体语义由 activationType 决定
     * @param preActivationDuration    正式激活前允许 Pre-Activation 的持续时间
     * @param creator                  Pool Creator
     * @param tokenMintXProgramFlag    X Mint 的 Token Program 类型：
     *                                 0 = SPL Token，
     *                                 1 = Token-2022
     * @param tokenMintYProgramFlag    Y Mint 的 Token Program 类型：
     *                                 0 = SPL Token，
     *                                 1 = Token-2022
     * @param version                  LbPair 数据结构版本
     * @param reserved                 Meteora 预留的 21 字节升级空间
     */
    public record LbPair(

            StaticParameters parameters,

            VariableParameters variableParameters,

            int bumpSeed,

            byte[] binStepSeed,

            int pairType,

            int activeId,

            int binStep,

            int status,

            int requireBaseFactorSeed,

            byte[] baseFactorSeed,

            int activationType,

            int creatorPoolOnOffControl,

            PublicKey tokenXMint,

            PublicKey tokenYMint,

            PublicKey reserveX,

            PublicKey reserveY,

            ProtocolFee protocolFee,

            List<RewardInfo> rewardInfos,

            PublicKey oracle,

            BigInteger[] binArrayBitmap,

            long lastUpdatedAt,

            PublicKey preActivationSwapAddress,

            PublicKey baseKey,

            BigInteger activationPoint,

            BigInteger preActivationDuration,

            PublicKey creator,

            int tokenMintXProgramFlag,

            int tokenMintYProgramFlag,

            int version,

            byte[] reserved) {

        /**
         * 返回 X Mint 对应的 Token Program。
         *
         * @return 如果 tokenMintXProgramFlag=0 返回 SPL Token；
         * 如果为 1 返回 Token-2022
         */
        public PublicKey tokenProgramX() {

            return tokenProgramFromFlag(tokenMintXProgramFlag);
        }

        /**
         * 返回 Y Mint 对应的 Token Program。
         *
         * @return 如果 tokenMintYProgramFlag=0 返回 SPL Token；
         * 如果为 1 返回 Token-2022
         */
        public PublicKey tokenProgramY() {

            return tokenProgramFromFlag(tokenMintYProgramFlag);
        }

        /**
         * 根据当前 activeId 计算所在 BinArray Index。
         *
         * @return 当前 Active Bin 所属的 BinArray Index
         */
        public long activeBinArrayIndex() {

            return MeteoraDlmmLbPairParser.activeBinArrayIndex(activeId);
        }
    }


    // ============================================================
    // Little Endian Reader
    // ============================================================

    /**
     * Solana 链上原始二进制 Little Endian 读取器。
     *
     * <p>
     * 用来统一读取：
     * <ul>
     *     <li>u8</li>
     *     <li>u16</li>
     *     <li>i32</li>
     *     <li>u32</li>
     *     <li>i64</li>
     *     <li>u64</li>
     *     <li>u128</li>
     *     <li>PublicKey</li>
     * </ul>
     */
    private static final class Reader {

        /**
         * 底层 ByteBuffer。
         * <p>
         * 固定使用 Little Endian。
         */
        private final ByteBuffer buffer;

        /**
         * 创建一个链上账户数据读取器。
         *
         * @param data 完整 Solana account.data 原始字节数组
         */
        private Reader(byte[] data) {

            this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }

        /**
         * 获取当前已经读取到的 byte offset。
         *
         * @return 当前 ByteBuffer position
         */
        int position() {
            return buffer.position();
        }

        /**
         * 跳过指定数量的字节。
         *
         * <p>
         * 主要用于跳过：
         * <ul>
         *     <li>padding</li>
         *     <li>reserved</li>
         *     <li>暂时不关心的字段</li>
         * </ul>
         *
         * @param length 需要跳过的字节数
         */
        void skip(int length) {

            buffer.position(buffer.position() + length);
        }

        /**
         * 从当前位置读取指定长度的原始字节。
         *
         * @param length 要读取的字节数量
         * @return 新的 byte[]，长度等于 length
         */
        byte[] bytes(int length) {

            byte[] result = new byte[length];

            buffer.get(result);

            return result;
        }

        /**
         * 读取一个 unsigned u8。
         *
         * @return 0 ~ 255 范围的 Java int
         */
        int u8() {

            return buffer.get() & 0xff;
        }

        /**
         * 读取一个 Little Endian unsigned u16。
         *
         * @return 0 ~ 65535 范围的 Java int
         */
        int u16() {

            return buffer.getShort() & 0xffff;
        }

        /**
         * 读取一个 Little Endian signed i32。
         *
         * @return Java int
         */
        int i32() {

            return buffer.getInt();
        }

        /**
         * 读取一个 Little Endian unsigned u32。
         *
         * <p>
         * Java int 无法保存完整 u32，
         * 因此转为 long。
         *
         * @return 0 ~ 4294967295 范围的 Java long
         */
        long u32() {

            return Integer.toUnsignedLong(buffer.getInt());
        }

        /**
         * 读取一个 Little Endian signed i64。
         *
         * @return Java long
         */
        long i64() {

            return buffer.getLong();
        }

        /**
         * 读取一个 Little Endian unsigned u64。
         *
         * <p>
         * Java long 最大只能到：
         * <pre>
         * 9223372036854775807
         * </pre>
         * <p>
         * 而 u64 最大为：
         * <pre>
         * 18446744073709551615
         * </pre>
         * <p>
         * 因此必须使用 BigInteger。
         *
         * @return 无符号 u64 对应的正 BigInteger
         */
        BigInteger u64() {

            return unsignedLittleEndian(8);
        }

        /**
         * 读取一个 Little Endian unsigned u128。
         *
         * @return 无符号 u128 对应的正 BigInteger
         */
        BigInteger u128() {

            return unsignedLittleEndian(16);
        }

        /**
         * 读取 Solana PublicKey。
         *
         * <p>
         * Solana PublicKey 链上固定占：
         * <pre>
         * 32 bytes
         * </pre>
         *
         * @return 解析完成的 PublicKey
         */
        PublicKey pubkey() {

            // 读取固定 32 字节
            byte[] pubkey = bytes(32);

            /*
             * 如果你使用的 solanaj PublicKey
             * 不支持 new PublicKey(byte[])，
             * 只需要修改这里即可。
             */
            return new PublicKey(pubkey);
        }

        /**
         * 读取指定长度的 Little Endian 无符号整数，
         * 并转换为 BigInteger。
         *
         * <p>
         * BigInteger(byte[]) 默认按照：
         * <pre>
         * Big Endian
         * </pre>
         * <p>
         * 解析。
         * <p>
         * Solana 数据却是：
         * <pre>
         * Little Endian
         * </pre>
         * <p>
         * 所以这里需要先将字节顺序反转。
         *
         * @param length 无符号整数占用的字节数；
         *               u64 传 8，
         *               u128 传 16
         * @return 对应的无符号正 BigInteger
         */
        private BigInteger unsignedLittleEndian(int length) {

            // 读取 Little Endian 原始数据
            byte[] littleEndian = bytes(length);

            // 创建 Big Endian 数组
            byte[] bigEndian = new byte[length];

            // 将 Little Endian 反转为 Big Endian
            for (int i = 0; i < length; i++) {

                bigEndian[length - 1 - i] = littleEndian[i];
            }

            /*
             * BigInteger(
             *     1,
             *     bytes
             * )
             *
             * 第一个参数 signum=1，
             * 强制按正数处理，
             * 从而实现 unsigned。
             */
            return new BigInteger(1, bigEndian);
        }
    }

    /**
     * Meteora 默认 LbPair Bitmap 一共包含 1024 个 bit。
     *
     * <p>
     * 对应 BinArray Index 范围：
     *
     * <pre>
     * -512 ~ 511
     * </pre>
     * <p>
     * 因为：
     *
     * <pre>
     * 16 个 u64
     * ×
     * 每个 u64 64 bit
     * =
     * 1024 bit
     * </pre>
     */
    public static final int DEFAULT_BITMAP_MIN_INDEX = -512;

    /**
     * Meteora 默认 Bitmap 能表示的最大 BinArray Index。
     */
    public static final int DEFAULT_BITMAP_MAX_INDEX = 511;


    /**
     * 判断指定 BinArray Index 是否已经在 LbPair 默认 Bitmap 中初始化。
     *
     * <p>
     * 默认 Bitmap 的映射方式：
     *
     * <pre>
     * BinArray Index -512 -> bitmap bit 0
     * BinArray Index -511 -> bitmap bit 1
     * ...
     * BinArray Index -1   -> bitmap bit 511
     * BinArray Index 0    -> bitmap bit 512
     * BinArray Index 1    -> bitmap bit 513
     * ...
     * BinArray Index 511  -> bitmap bit 1023
     * </pre>
     *
     * @param binArrayBitmap LbPair 中解析出来的 bin_array_bitmap，长度必须为 16
     * @param binArrayIndex  要检查的 BinArray Index
     * @return true 表示该 BinArray 已初始化；
     * false 表示没有初始化，或者已经超出默认 Bitmap 范围
     */
    public static boolean isBinArrayInitialized(BigInteger[] binArrayBitmap, long binArrayIndex) {

        /*
         * 超过默认 Bitmap 范围。
         *
         * 这种情况不能继续看 LbPair.binArrayBitmap，
         * 而是应该检查 BinArrayBitmapExtension。
         */
        if (binArrayIndex < DEFAULT_BITMAP_MIN_INDEX || binArrayIndex > DEFAULT_BITMAP_MAX_INDEX) {

            return false;
        }

        /*
         * 将真实 BinArray Index 转成 0 ~ 1023 的 Bitmap Bit Index。
         *
         * 例如：
         *
         * index = -512
         * bitIndex = 0
         *
         * index = -1
         * bitIndex = 511
         *
         * index = 0
         * bitIndex = 512
         */
        int bitIndex = Math.toIntExact(binArrayIndex - DEFAULT_BITMAP_MIN_INDEX);

        /*
         * 找到对应的 u64。
         *
         * 例如：
         *
         * bitIndex = 512
         *
         * wordIndex =
         * 512 / 64
         * =
         * 8
         */
        int wordIndex = bitIndex / 64;

        /*
         * 找到这个 u64 内部的 bit。
         *
         * 例如：
         *
         * bitIndex = 512
         *
         * bitOffset =
         * 512 % 64
         * =
         * 0
         */
        int bitOffset = bitIndex % 64;

        /*
         * BigInteger.testBit()
         * 可以直接检查对应 bit 是否为 1。
         */
        return binArrayBitmap[wordIndex].testBit(bitOffset);
    }

    /**
     * 从当前 Active BinArray 开始，
     * 按 Swap 方向寻找已经初始化的 BinArray。
     *
     * <p>
     * 方向规则：
     *
     * <pre>
     * swapForY = true
     * X -> Y
     * Bin ID 向更小的方向搜索
     *
     * 例如：
     * 0 -> -1 -> -2 -> -3
     *
     *
     * swapForY = false
     * Y -> X
     * Bin ID 向更大的方向搜索
     *
     * 例如：
     * 0 -> 1 -> 2 -> 3
     * </pre>
     *
     * @param activeId       当前 LbPair.active_id
     * @param binArrayBitmap 当前 LbPair.bin_array_bitmap
     * @param swapForY       true  = X -> Y
     *                       false = Y -> X
     * @param maxCount       最多返回几个已经初始化的 BinArray。
     *                       <p>
     *                       比如传 4：
     *                       <p>
     *                       最多返回 4 个 BinArray Index。
     * @return 按 Swap 执行顺序排列的 BinArray Index
     */
    public static List<Long> findBinArrayIndexesForSwap(int activeId, BigInteger[] binArrayBitmap, boolean swapForY, int maxCount) {

        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount 必须大于 0");
        }

        /*
         * 根据当前 activeId 得到所在 BinArray Index。
         *
         * 例如：
         *
         * activeId = -623
         *
         * activeBinArrayIndex = -9
         */
        long currentIndex = Math.floorDiv((long) activeId, BIN_ARRAY_SIZE);

        /*
         * 保存最终结果。
         *
         * 顺序就是后面传给 Swap 的顺序。
         */
        List<Long> result = new ArrayList<>(maxCount);

        /*
         * X -> Y：
         *
         * 向更小的 BinArray Index 搜索。
         *
         * Y -> X：
         *
         * 向更大的 BinArray Index 搜索。
         */
        long step = swapForY ? -1L : 1L;

        /*
         * 从 Active BinArray 开始搜索。
         */
        long index = currentIndex;

        while (result.size() < maxCount) {

            /*
             * 已经离开默认 Bitmap 范围。
             *
             * 如果发生这种情况，
             * 后面应该交给 BitmapExtension 继续找。
             */
            if (index < DEFAULT_BITMAP_MIN_INDEX || index > DEFAULT_BITMAP_MAX_INDEX) {

                break;
            }

            /*
             * 只有 Bitmap 中 bit=1 的 BinArray
             * 才加入结果。
             *
             * bit=0 直接跳过。
             */
            if (isBinArrayInitialized(binArrayBitmap, index)) {

                result.add(index);
            }

            /*
             * 按交易方向继续找下一个。
             */
            index += step;
        }

        return result;
    }


    /**
     * 根据 LbPair 和 BinArray Index
     * 派生 Meteora BinArray PDA。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "bin_array",
     *     lbPair,
     *     binArrayIndex 的 i64 Little Endian
     * ]
     * </pre>
     *
     * @param lbPair        Meteora DLMM LbPair 地址
     * @param binArrayIndex 要派生的 BinArray Index
     * @return 对应 BinArray PDA
     */
    public static PublicKey deriveBinArray(PublicKey lbPair, long binArrayIndex) {
        BinArrayCacheKey key = new BinArrayCacheKey(lbPair, binArrayIndex);
        Map<BinArrayCacheKey, byte[]> cache = BIN_ARRAY_CACHE.get();
        byte[] cached = cache.get(key);
        if (cached != null) return new PublicKey(cached);

        /*
         * Meteora 使用 signed i64 Little Endian
         * 保存 BinArray Index Seed。
         */
        byte[] indexBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(binArrayIndex).array();

        PublicKey address = PublicKey.findProgramAddress(List.of("bin_array".getBytes(StandardCharsets.UTF_8), lbPair.toByteArray(), indexBytes), DLMM_PROGRAM_ID).getAddress();
        cache.put(key, address.toByteArray().clone());
        return address;
    }

    /**
     * 根据当前 LbPair 状态生成 Swap 所需的 BinArray 地址列表。
     *
     * @param lbPair   LbPair 地址
     * @param state    已经解析完成的 LbPair 链上状态
     * @param swapForY true  = X -> Y
     *                 false = Y -> X
     * @param maxCount 最多提前携带多少个 BinArray。
     *                 <p>
     *                 低延迟交易一般可以先准备 3~4 个，
     *                 具体数量取决于你的交易量和账户限制。
     * @return 按 Swap 路径顺序排列的 BinArray PDA
     */
    public static List<PublicKey> buildBinArraysForSwap(PublicKey lbPair, LbPair state, boolean swapForY, int maxCount) {

        /*
         * 第一步：
         *
         * 根据 activeId + bitmap + 方向
         * 得到初始化过的 BinArray Index。
         */
        List<Long> indexes = findBinArrayIndexesForSwap(state.activeId(), state.binArrayBitmap(), swapForY, maxCount);

        /*
         * 第二步：
         *
         * 将每个 BinArray Index 转成 PDA。
         */
        List<PublicKey> result = new ArrayList<>(indexes.size());

        for (long index : indexes) {

            result.add(deriveBinArray(lbPair, index));
        }

        return List.copyOf(result);
    }

    public static List<PublicKey> buildBinArraysForSwap(PublicKey lbPair, int activeId, BigInteger[] binArrayBitmap, boolean swapForY, int maxCount) {

        /*
         * 第一步：
         *
         * 根据 activeId + bitmap + 方向
         * 得到初始化过的 BinArray Index。
         */
        List<Long> indexes = findBinArrayIndexesForSwap(activeId, binArrayBitmap, swapForY, maxCount);

        /*
         * 第二步：
         *
         * 将每个 BinArray Index 转成 PDA。
         */
        List<PublicKey> result = new ArrayList<>(indexes.size());

        for (long index : indexes) {

            result.add(deriveBinArray(lbPair, index));
        }

        return List.copyOf(result);
    }


    public static void main(String[] args) {
        byte[] curveTypeBytes = new byte[]{(byte) 0};

        // Raydium SDK 使用 u16 Big Endian
        byte[] indexBytes = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 0).array();

        var lp = PublicKey.findProgramAddress(List.of("global_config".getBytes(StandardCharsets.UTF_8),

                new PublicKey("Xs3oZwbHvqis4NYcf4YKWmEia2eC84wSiVrcYcTqpH8").toByteArray(),

                curveTypeBytes, indexBytes), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj")).getAddress();
        System.out.println(lp);
    }

}
