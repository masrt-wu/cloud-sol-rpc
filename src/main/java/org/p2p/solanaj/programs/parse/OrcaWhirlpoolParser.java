package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Orca Whirlpool 池、两种 tick 数组和 adaptive-fee Oracle 解析；Vault 地址从池账户读取。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class OrcaWhirlpoolParser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "whirlpools_config whirlpool_bump tick_spacing fee_tier_index_seed token_mint_a token_mint_b token_vault_a token_vault_b".split(" ");

    private OrcaWhirlpoolParser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "Whirlpool", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "Whirlpool", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析固定布局的八十八个 tick；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseFixedTickArray(byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "FixedTickArray", null, data);
    }

    /**
     * 解析固定布局的八十八个 tick，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseFixedTickArray(PublicKey owner, byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "FixedTickArray", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析动态布局 tick 的未初始化与已初始化分支；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseDynamicTickArray(byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "DynamicTickArray", null, data);
    }

    /**
     * 解析动态布局 tick 的未初始化与已初始化分支，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseDynamicTickArray(PublicKey owner, byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "DynamicTickArray", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析交易启用时间和 adaptive-fee 动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseOracle(byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "Oracle", null, data);
    }

    /**
     * 解析交易启用时间和 adaptive-fee 动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseOracle(PublicKey owner, byte[] data) {
        return DexLayouts.parse("orca-whirlpool", "Oracle", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /**
     * 按升序 mint 和 feeTierIndex 推导 Whirlpool PDA
     * @param config WhirlpoolsConfig 地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @param feeTierIndex 池的 fee tier 索引；adaptive-fee 池不一定等于 tickSpacing
     * @return 本地计算结果
     */
    public static PublicKey derivePool(PublicKey config, PublicKey mintA, PublicKey mintB, int feeTierIndex) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("whirlpool"), DexPda.key(config),
                DexPda.key(mints.get(0)), DexPda.key(mints.get(1)), DexPda.u16(feeTierIndex, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 从池保存的两字节 fee_tier_index_seed 读取真实索引
     * @param pool 已解析的 Whirlpool
     * @return 本地计算结果
     */
    public static int feeTierIndex(DexAccountData pool) {
        byte[] seed = pool.bytes("fee_tier_index_seed");
        return Byte.toUnsignedInt(seed[0]) | (Byte.toUnsignedInt(seed[1]) << 8);
    }
    /**
     * 推导普通 FeeTier PDA；不代表 adaptive-fee 配置账户
     * @param config WhirlpoolsConfig 地址
     * @param index fee tier 索引，u16 小端
     * @return 本地计算结果
     */
    public static PublicKey deriveFeeTier(PublicKey config, int index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("fee_tier"), DexPda.key(config), DexPda.u16(index, ByteOrder.LITTLE_ENDIAN));
    }
    /**
     * 推导 Oracle PDA；adaptive-fee 池必须持续刷新其中动态状态
     * @param pool 实际 Whirlpool 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveOracle(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("oracle"), DexPda.key(pool));
    }
    /**
     * 计算当前 tick 所在数组起点；每个数组八十八个 tick，负数向下取整
     * @param tickCurrent 最新 tickCurrentIndex
     * @param tickSpacing tick 间距，必须大于零
     * @return 本地计算结果
     */
    public static int tickArrayStartIndex(int tickCurrent, int tickSpacing) {
        if (tickSpacing <= 0) throw new IllegalArgumentException("tickSpacing 必须大于零");
        int span = Math.multiplyExact(tickSpacing, 88);
        return Math.multiplyExact(Math.floorDiv(tickCurrent, span), span);
    }
    /**
     * 推导固定或动态 TickArray 共用的 PDA；Orca 使用十进制文本种子
     * @param pool 实际 Whirlpool 地址
     * @param startTickIndex 数组起始 tick
     * @return 本地计算结果
     */
    public static PublicKey deriveTickArray(PublicKey pool, int startTickIndex) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("tick_array"), DexPda.key(pool),
                Integer.toString(startTickIndex).getBytes(StandardCharsets.US_ASCII));
    }
    /**
     * 推导当前 tick 所在数组；不是整个交换路径的数组列表
     * @param poolAddress Whirlpool 账户自身地址
     * @param pool 包含最新 tickCurrentIndex 的池快照
     * @return 本地计算结果
     */
    public static PublicKey deriveCurrentTickArray(PublicKey poolAddress, DexAccountData pool) {
        return deriveTickArray(poolAddress, tickArrayStartIndex(pool.intValue("tick_current_index"), pool.intValue("tick_spacing")));
    }
    /**
     * 按判别符自动选择固定或动态 TickArray 布局
     * @param data 固定或动态 TickArray 原始字节
     * @return 本地计算结果
     */
    public static DexAccountData parseTickArray(byte[] data) {
        if (data != null && data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8),
                new byte[]{17, (byte) 216, (byte) 246, (byte) 142, (byte) 225, (byte) 199, (byte) 218, 56}))
            return parseDynamicTickArray(data);
        return parseFixedTickArray(data);
    }
    /**
     * 自动识别 TickArray 布局并验证 owner
     * @param owner TickArray 账户实际所属程序
     * @param data 固定或动态 TickArray 原始字节
     * @return 本地计算结果
     */
    public static DexAccountData parseTickArray(PublicKey owner, byte[] data) {
        if (!PROGRAM_ID.equals(owner)) throw new IllegalArgumentException("TickArray 所属程序不匹配");
        return parseTickArray(data);
    }
}
