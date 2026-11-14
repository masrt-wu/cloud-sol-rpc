package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/** Raydium CLMM 池、tick 数组、扩展位图和动态费用状态解析。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class RaydiumClmmParser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "bump amm_config owner token_mint_0 token_mint_1 token_vault_0 token_vault_1 observation_key mint_decimals_0 mint_decimals_1 tick_spacing".split(" ");

    private RaydiumClmmParser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "PoolState", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "PoolState", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析普通费率配置；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseAmmConfig(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "AmmConfig", null, data);
    }

    /**
     * 解析普通费率配置，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseAmmConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "AmmConfig", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析动态费用配置；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseDynamicFeeConfig(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "DynamicFeeConfig", null, data);
    }

    /**
     * 解析动态费用配置，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseDynamicFeeConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "DynamicFeeConfig", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析六十个 tick 的流动性、手续费和订单状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseTickArray(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "TickArrayState", null, data);
    }

    /**
     * 解析六十个 tick 的流动性、手续费和订单状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseTickArray(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "TickArrayState", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析超出默认范围的 tick 数组位图；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseBitmapExtension(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "TickArrayBitmapExtension", null, data);
    }

    /**
     * 解析超出默认范围的 tick 数组位图，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseBitmapExtension(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "TickArrayBitmapExtension", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析池的观察状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseObservation(byte[] data) {
        return DexLayouts.parse("raydium-clmm", "ObservationState", null, data);
    }

    /**
     * 解析池的观察状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseObservation(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-clmm", "ObservationState", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /**
     * 推导 CLMM 配置 PDA
     * @param index 配置索引，u16 大端
     * @return 本地计算结果
     */
    public static PublicKey deriveAmmConfig(int index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("amm_config"), DexPda.u16(index, ByteOrder.BIG_ENDIAN));
    }
    /**
     * 将 mint 按原始字节升序排列后推导 PoolState
     * @param ammConfig 费率配置地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey derivePool(PublicKey ammConfig, PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool"), DexPda.key(ammConfig),
                DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导 代币 vault PDA
     * @param pool 实际池地址
     * @param mint 对应代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveVault(PublicKey pool, PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_vault"), DexPda.key(pool), DexPda.key(mint));
    }
    /**
     * 推导 奖励 vault PDA
     * @param pool 实际池地址
     * @param mint 对应代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveRewardVault(PublicKey pool, PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_reward_vault"), DexPda.key(pool), DexPda.key(mint));
    }
    /**
     * 推导 观察账户 PDA
     * @param pool 实际池地址
     * @return 本地计算结果
     */
    public static PublicKey deriveObservation(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("observation"), DexPda.key(pool));
    }
    /**
     * 推导 扩展位图账户 PDA
     * @param pool 实际池地址
     * @return 本地计算结果
     */
    public static PublicKey deriveBitmapExtension(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_tick_array_bitmap_extension"), DexPda.key(pool));
    }
    /**
     * 计算当前 tick 所在数组起点；负数必须向下取整
     * @param tickCurrent 最新的当前 tick
     * @param tickSpacing 池的 tick 间距，必须大于零
     * @return 本地计算结果
     */
    public static int tickArrayStartIndex(int tickCurrent, int tickSpacing) {
        if (tickSpacing <= 0) throw new IllegalArgumentException("tickSpacing 必须大于零");
        int span = Math.multiplyExact(tickSpacing, 60);
        return Math.multiplyExact(Math.floorDiv(tickCurrent, span), span);
    }
    /**
     * 推导指定 TickArray；CLMM 使用大端数值种子，不能套用 Orca 的文本种子
     * @param pool 实际池地址
     * @param startTickIndex 目标数组起点，i32 大端
     * @return 本地计算结果
     */
    public static PublicKey deriveTickArray(PublicKey pool, int startTickIndex) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("tick_array"), DexPda.key(pool),
                DexPda.i32(startTickIndex, ByteOrder.BIG_ENDIAN));
    }
    /**
     * 推导当前 tick 所在数组；不代表交易跨越的全部数组
     * @param poolAddress 池账户自身地址
     * @param pool 包含最新 tick_current 的池快照
     * @return 本地计算结果
     */
    public static PublicKey deriveCurrentTickArray(PublicKey poolAddress, DexAccountData pool) {
        return deriveTickArray(poolAddress, tickArrayStartIndex(pool.intValue("tick_current"), pool.intValue("tick_spacing")));
    }
}
