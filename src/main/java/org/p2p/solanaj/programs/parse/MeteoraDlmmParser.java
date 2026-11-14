package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/** Meteora DLMM 池和 bin 状态解析；PDA 地址不能代替实时 bin 内容。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class MeteoraDlmmParser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "parameters bump_seed bin_step_seed pair_type bin_step require_base_factor_seed base_factor_seed token_x_mint token_y_mint reserve_x reserve_y oracle base_key creator token_mint_x_program_flag token_mint_y_program_flag".split(" ");

    private MeteoraDlmmParser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "LbPair", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "LbPair", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析七十个 bin 的储备、价格、流动性和订单状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseBinArray(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "BinArray", null, data);
    }

    /**
     * 解析七十个 bin 的储备、价格、流动性和订单状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseBinArray(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "BinArray", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析默认位图范围之外的 BinArray 位图；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseBitmapExtension(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "BinArrayBitmapExtension", null, data);
    }

    /**
     * 解析默认位图范围之外的 BinArray 位图，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseBitmapExtension(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "BinArrayBitmapExtension", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析旧版预设手续费参数；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePresetParameter(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "PresetParameter", null, data);
    }

    /**
     * 解析旧版预设手续费参数，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePresetParameter(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "PresetParameter", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析新版预设手续费参数；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePresetParameter2(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "PresetParameter2", null, data);
    }

    /**
     * 解析新版预设手续费参数，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePresetParameter2(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "PresetParameter2", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析 Oracle 头部；变长观察记录需使用原始尾部数据；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseOracleHeader(byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "Oracle", null, data);
    }

    /**
     * 解析 Oracle 头部；变长观察记录需使用原始尾部数据，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseOracleHeader(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-dlmm", "Oracle", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /** 官方可定制 permissionless 池使用的固定 base 种子。 */
    public static final PublicKey CUSTOMIZABLE_BASE = new PublicKey("MFGQxwAmB91SwuYX36okv2Qmdc9aMuHTwWGUrp4AtB1");

    /**
     * 推导 Oracle PDA；仍需确认账户是否已创建
     * @param pool LbPair 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveOracle(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("oracle"), DexPda.key(pool));
    }
    /**
     * 推导 BinArray 扩展位图 PDA；仍需确认账户是否已创建
     * @param pool LbPair 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveBitmapExtension(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("bitmap"), DexPda.key(pool));
    }
    /**
     * 推导 reserveX 或 reserveY
     * @param pool LbPair 地址
     * @param mint X 或 Y 侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveReserve(PublicKey pool, PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(pool), DexPda.key(mint));
    }
    /**
     * 推导事件 authority
     * @return 本地计算结果
     */
    public static PublicKey deriveEventAuthority() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("__event_authority"));
    }
    /**
     * 计算 BinArray 索引；每个数组七十个 bin，负数向下取整
     * @param binId 当前或目标 bin ID，允许负数
     * @return 本地计算结果
     */
    public static long binArrayIndex(int binId) {
        return Math.floorDiv((long) binId, 70L);
    }
    /**
     * 推导 BinArray；不能用 active_id 直接充当数组索引
     * @param pool LbPair 地址
     * @param index 有符号 BinArray 索引，按 i64 小端编码
     * @return 本地计算结果
     */
    public static PublicKey deriveBinArray(PublicKey pool, long index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("bin_array"), DexPda.key(pool), DexPda.i64(index));
    }
    /**
     * 推导当前 bin 所在数组；后续所需数组仍由实时位图和交换方向决定
     * @param poolAddress LbPair 账户自身地址
     * @param pool 包含最新 active_id 的池快照
     * @return 本地计算结果
     */
    public static PublicKey deriveCurrentBinArray(PublicKey poolAddress, DexAccountData pool) {
        return deriveBinArray(poolAddress, binArrayIndex(pool.intValue("active_id")));
    }
    /**
     * 推导奖励 vault；该索引按 u64 小端编码
     * @param pool LbPair 地址
     * @param rewardIndex 奖励槽位，零或一
     * @return 本地计算结果
     */
    public static PublicKey deriveRewardVault(PublicKey pool, int rewardIndex) {
        if (rewardIndex < 0 || rewardIndex > 1) throw new IllegalArgumentException("奖励索引只能是零或一");
        return DexPda.derive(PROGRAM_ID, DexPda.key(pool), DexPda.u64(BigInteger.valueOf(rewardIndex)));
    }
    /**
     * 推导旧版 permissionless LbPair；不同 pair 类型不能混用本方法
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @param binStep binStep，u16 小端
     * @return 本地计算结果
     */
    public static PublicKey deriveLegacyLbPair(PublicKey mintA, PublicKey mintB, int binStep) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.key(mints.get(0)), DexPda.key(mints.get(1)),
                DexPda.u16(binStep, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 推导带 baseFactor 种子的 permissionless LbPair
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @param binStep binStep，u16 小端
     * @param baseFactor baseFactor，u16 小端
     * @return 本地计算结果
     */
    public static PublicKey deriveLbPair(PublicKey mintA, PublicKey mintB, int binStep, int baseFactor) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.key(mints.get(0)), DexPda.key(mints.get(1)),
                DexPda.u16(binStep, ByteOrder.LITTLE_ENDIAN), DexPda.u16(baseFactor, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 推导使用预设参数地址作为种子的 LbPair
     * @param presetParameter 预设参数地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveLbPairWithPreset(PublicKey presetParameter, PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.key(presetParameter), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导 permissioned LbPair
     * @param baseKey permissioned 池保存的 baseKey
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @param binStep binStep，u16 小端
     * @return 本地计算结果
     */
    public static PublicKey derivePermissionedLbPair(PublicKey baseKey, PublicKey mintA, PublicKey mintB, int binStep) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.key(baseKey), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)),
                DexPda.u16(binStep, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 按官方固定 base 推导可定制 permissionless LbPair
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveCustomizableLbPair(PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.key(CUSTOMIZABLE_BASE), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导旧版预设参数 PDA
     * @param binStep 旧版预设参数的 binStep
     * @return 本地计算结果
     */
    public static PublicKey derivePresetParameter(int binStep) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("preset_parameter"), DexPda.u16(binStep, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 推导带 baseFactor 的旧式预设参数 PDA；与按索引创建的 preset_parameter2 不同
     * @param binStep binStep，u16 小端
     * @param baseFactor baseFactor，u16 小端
     * @return 本地计算结果
     */
    public static PublicKey derivePresetParameter(int binStep, int baseFactor) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("preset_parameter"),
                DexPda.u16(binStep, ByteOrder.LITTLE_ENDIAN), DexPda.u16(baseFactor, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 推导按索引区分的预设参数 PDA
     * @param index 新版预设参数索引
     * @return 本地计算结果
     */
    public static PublicKey derivePresetParameterWithIndex(int index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("preset_parameter2"), DexPda.u16(index, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 将 X 侧 token program 标记转换成实际地址
     * @param pool 已解析的 LbPair
     * @return 本地计算结果
     */
    public static PublicKey tokenProgramX(DexAccountData pool) {
        return DexPda.tokenProgramFromFlag(pool.intValue("token_mint_x_program_flag"));
    }
    /**
     * 将 Y 侧 token program 标记转换成实际地址
     * @param pool 已解析的 LbPair
     * @return 本地计算结果
     */
    public static PublicKey tokenProgramY(DexAccountData pool) {
        return DexPda.tokenProgramFromFlag(pool.intValue("token_mint_y_program_flag"));
    }
}
