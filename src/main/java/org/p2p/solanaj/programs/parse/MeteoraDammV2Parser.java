package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Meteora DAMM v2 池解析；同时保留布局版本、内部储备、动态费用和激活状态。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class MeteoraDammV2Parser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("cpamdpZCGKUy5JxQXB4dcpGPiikHawvSWAd6mEn1sGG");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "token_a_mint token_b_mint token_a_vault token_b_vault whitelisted_vault creator token_a_flag token_b_flag pool_type sqrt_min_price sqrt_max_price".split(" ");

    private MeteoraDammV2Parser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("meteora-damm-v2", "Pool", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-damm-v2", "Pool", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析费用、价格范围和权限配置；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseConfig(byte[] data) {
        return DexLayouts.parse("meteora-damm-v2", "Config", null, data);
    }

    /**
     * 解析费用、价格范围和权限配置，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-damm-v2", "Config", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /**
     * 推导固定 poolAuthority，不需要读取池账户
     * @return 本地计算结果
     */
    public static PublicKey deriveAuthority() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_authority"));
    }
    /**
     * 推导 DAMM v2 配置 PDA
     * @param index 配置索引，完整 u64 小端
     * @return 本地计算结果
     */
    public static PublicKey deriveConfig(BigInteger index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("config"), DexPda.u64(index));
    }
    /**
     * 按官方降序 mint 推导 permissionless 池
     * @param config 创建池时使用的配置；Pool 布局本身没有保存此地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey derivePool(PublicKey config, PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool"), DexPda.key(config),
                DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导可定制 permissionless 池
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveCustomizablePool(PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("cpool"), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导任意一侧的 token vault；该协议种子顺序是 mint 再 pool
     * @param pool 真实池地址
     * @param mint 对应代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveVault(PublicKey pool, PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("token_vault"), DexPda.key(mint), DexPda.key(pool));
    }
    /**
     * 推导 token badge PDA
     * @param mint 代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveTokenBadge(PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("token_badge"), DexPda.key(mint));
    }
    /**
     * 推导事件 authority
     * @return 本地计算结果
     */
    public static PublicKey deriveEventAuthority() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("__event_authority"));
    }
    /**
     * 推导奖励 vault；该协议使用单字节奖励索引，不是 DLMM 的 u64 索引
     * @param pool 真实池地址
     * @param rewardIndex 奖励槽位，零或一
     * @return 本地计算结果
     */
    public static PublicKey deriveRewardVault(PublicKey pool, int rewardIndex) {
        if (rewardIndex < 0 || rewardIndex > 1) throw new IllegalArgumentException("奖励索引只能是零或一");
        return DexPda.derive(PROGRAM_ID, DexPda.seed("reward_vault"), DexPda.key(pool), DexPda.u8(rewardIndex));
    }
    /**
     * 读取 A 侧代币程序标记
     * @param pool 已解析 Pool
     * @return 本地计算结果
     */
    public static PublicKey tokenProgramA(DexAccountData pool) {
        return DexPda.tokenProgramFromFlag(pool.intValue("token_a_flag"));
    }
    /**
     * 读取 B 侧代币程序标记
     * @param pool 已解析 Pool
     * @return 本地计算结果
     */
    public static PublicKey tokenProgramB(DexAccountData pool) {
        return DexPda.tokenProgramFromFlag(pool.intValue("token_b_flag"));
    }
    /**
     * 解析三十二字节基础费信息，自动区分时间、价格与旧版限速模式
     * @param pool 包含最新 pool_fees 的 Pool 快照
     * @return 费用调度器参数；本方法不计算最终动态手续费
     */
    public static DexAccountData parseFeeScheduler(DexAccountData pool) {
        byte[] data = pool.bytes("pool_fees.base_fee.base_fee_info.data");
        int mode = Byte.toUnsignedInt(data[8]);
        String type = switch (mode) {
            case 0, 1 -> "PodAlignedFeeTimeScheduler";
            case 2 -> "PodAlignedFeeRateLimiter";
            case 3, 4 -> "PodAlignedFeeMarketCapScheduler";
            default -> throw new IllegalArgumentException("未知基础费用模式：" + mode);
        };
        return DexLayouts.parseType("meteora-damm-v2", type, data);
    }
    /**
     * 读取新版池内部维护的两侧金额；旧版零填充不能当作真实储备
     * @param pool 最新 Pool 快照
     * @return 协议内部金额，不代表可以直接套用恒定乘积报价
     */
    public static DexReserves trackedAmounts(DexAccountData pool) {
        if (pool.intValue("layout_version") != 1)
            throw new IllegalArgumentException("只有已验证的布局版本一支持内部金额；其他版本请核对官方布局和 vault");
        return new DexReserves(pool.integer("token_a_amount"), pool.integer("token_b_amount"));
    }
}
