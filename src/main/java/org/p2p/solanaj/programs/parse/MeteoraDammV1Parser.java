package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Meteora DAMM v1 池解析；精确报价还需要 Dynamic Vault、LP supply 和池持有的 vault LP。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class MeteoraDammV1Parser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "lpMint tokenAMint tokenBMint aVault bVault aVaultLp bVaultLp aVaultLpBump protocolTokenAFee protocolTokenBFee poolType stake".split(" ");

    private MeteoraDammV1Parser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("meteora-damm-v1", "pool", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-damm-v1", "pool", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析池配置和费用参数；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseConfig(byte[] data) {
        return DexLayouts.parse("meteora-damm-v1", "config", null, data);
    }

    /**
     * 解析池配置和费用参数，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-damm-v1", "config", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /** Dynamic Vault 主网程序，和 DAMM v1 池程序不同。 */
    public static final PublicKey VAULT_PROGRAM_ID = new PublicKey("24Uqj9JCLxUeoC3hGfh5W3s9FM9uCHDS2SG3LYwBpyTi");
    /** 默认 Dynamic Vault base 种子；其他部署可以显式传入自己的 base。 */
    public static final PublicKey DEFAULT_VAULT_BASE = new PublicKey("HWzXGcGHy4tcpYfaRDCyLNzXqBTv3E6BttpCH2vJxArv");
    /** 官方锁定利润衰减分母。 */
    private static final BigInteger PROFIT_DENOMINATOR = new BigInteger("1000000000000");

    /**
     * 解析动态 Vault 的总资产、地址、策略和锁定利润，不验证 owner
     * @param data Dynamic Vault 账户原始字节
     * @return 本地计算结果
     */
    public static DexAccountData parseDynamicVault(byte[] data) {
        return DexLayouts.parse("meteora-vault", "vault", null, data,
                "bumps", "tokenVault", "feeVault", "tokenMint", "lpMint", "base");
    }
    /**
     * 解析 Dynamic Vault 并验证其程序；不能传 DAMM 池程序
     * @param owner Dynamic Vault 的链上所属程序
     * @param data 账户原始字节
     * @return 本地计算结果
     */
    public static DexAccountData parseDynamicVault(PublicKey owner, byte[] data) {
        return DexLayouts.parse("meteora-vault", "vault", Objects.requireNonNull(owner, "Vault owner 不能为空"), data,
                "bumps", "tokenVault", "feeVault", "tokenMint", "lpMint", "base");
    }
    /**
     * 推导旧版 permissionless 池；permissioned 任意 signer 池不适用
     * @param curveType 零为恒定乘积，一为稳定曲线
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey derivePermissionlessPool(int curveType, PublicKey mintA, PublicKey mintB) {
        if (curveType < 0 || curveType > 1) throw new IllegalArgumentException("曲线类型只能是零或一");
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.u8(curveType), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导带手续费分档的 permissionless 池
     * @param curveType 零为恒定乘积，一为稳定曲线
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @param tradeFeeBps 完整 u64 手续费基点；默认二十五基点使用空种子
     * @return 本地计算结果
     */
    public static PublicKey deriveFeeTierPool(int curveType, PublicKey mintA, PublicKey mintB, BigInteger tradeFeeBps) {
        if (curveType < 0 || curveType > 1) throw new IllegalArgumentException("曲线类型只能是零或一");
        byte[] fee = tradeFeeBps.equals(BigInteger.valueOf(25)) ? new byte[0] : DexPda.u64(tradeFeeBps);
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.u8(curveType), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)), fee);
    }
    /**
     * 推导使用配置地址的 permissionless 恒定乘积池
     * @param config 池的实际配置地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveConfigPool(PublicKey config, PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.key(mints.get(0)), DexPda.key(mints.get(1)), DexPda.key(config));
    }
    /**
     * 推导可定制 permissionless 恒定乘积池
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveCustomizablePool(PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, false);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool"), DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 仅推导新版 PDA LP mint；历史非 PDA LP mint 必须读取 Pool.lpMint
     * @param pool 真实池地址
     * @return 本地计算结果
     */
    public static PublicKey deriveModernPoolLpMint(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("lp_mint"), DexPda.key(pool));
    }
    /**
     * 推导新版 Dynamic Vault；发现后的链上 aVault/bVault 地址优先
     * @param mint 代币 mint
     * @param base Vault 使用的 base 种子
     * @return 本地计算结果
     */
    public static PublicKey deriveDynamicVault(PublicKey mint, PublicKey base) {
        return DexPda.derive(VAULT_PROGRAM_ID, DexPda.seed("vault"), DexPda.key(mint), DexPda.key(base));
    }
    /**
     * 按主网默认 base 推导 Dynamic Vault
     * @param mint 代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveDynamicVault(PublicKey mint) {
        return deriveDynamicVault(mint, DEFAULT_VAULT_BASE);
    }
    /**
     * 推导 Dynamic Vault 的底层 token reserve PDA
     * @param vault Dynamic Vault 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveTokenVault(PublicKey vault) {
        return DexPda.derive(VAULT_PROGRAM_ID, DexPda.seed("token_vault"), DexPda.key(vault));
    }
    /**
     * 仅推导新版 Vault LP mint；历史账户以 Vault.lpMint 为准
     * @param vault Dynamic Vault 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveModernVaultLpMint(PublicKey vault) {
        return DexPda.derive(VAULT_PROGRAM_ID, DexPda.seed("lp_mint"), DexPda.key(vault));
    }
    /**
     * 推导池持有的 vault LP 代币账户
     * @param vault Dynamic Vault 地址
     * @param pool 真实 DAMM v1 池地址
     * @return 本地计算结果
     */
    public static PublicKey derivePoolVaultLp(PublicKey vault, PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(vault), DexPda.key(pool));
    }
    /**
     * 推导协议费账户 PDA
     * @param mint 对应 mint
     * @param pool 真实 DAMM v1 池地址
     * @return 本地计算结果
     */
    public static PublicKey deriveProtocolFee(PublicKey mint, PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("fee"), DexPda.key(mint), DexPda.key(pool));
    }
    /**
     * 按官方规则扣除尚未解锁利润；保留整数向下取整
     * @param vault 最新 Dynamic Vault 快照
     * @param unixSeconds 与快照一致的链上 Unix 秒时间
     * @return Vault 可用于计算份额的未锁定总资产
     */
    public static BigInteger unlockedVaultAmount(DexAccountData vault, long unixSeconds) {
        if (unixSeconds < 0) throw new IllegalArgumentException("链上时间不能为负数");
        BigInteger elapsed = BigInteger.valueOf(unixSeconds).subtract(vault.integer("lockedProfitTracker.lastReport"));
        if (elapsed.signum() < 0) throw new IllegalArgumentException("链上时间早于 Vault 报告时间，快照不一致");
        BigInteger ratio = elapsed.multiply(vault.integer("lockedProfitTracker.lockedProfitDegradation"));
        BigInteger locked = ratio.compareTo(PROFIT_DENOMINATOR) >= 0 ? BigInteger.ZERO
                : vault.integer("lockedProfitTracker.lastUpdatedLockedProfit")
                    .multiply(PROFIT_DENOMINATOR.subtract(ratio)).divide(PROFIT_DENOMINATOR);
        BigInteger unlocked = vault.integer("totalAmount").subtract(locked);
        if (unlocked.signum() < 0) throw new IllegalArgumentException("Vault 锁定利润超过总资产");
        return unlocked;
    }
    /**
     * 将池持有的 vault LP 转为底层金额；仍需同步 token reserve 检查实际可支付额度
     * @param vault 最新 Dynamic Vault 快照
     * @param poolVaultLpAmount 池持有的该侧 vault LP 账户实际余额
     * @param vaultLpSupply 该 vault LP mint 的最新 supply
     * @param unixSeconds 与 Vault 快照一致的链上 Unix 秒时间
     * @return 本地计算结果
     */
    public static BigInteger poolUnderlyingAmount(DexAccountData vault, BigInteger poolVaultLpAmount, BigInteger vaultLpSupply, long unixSeconds) {
        if (poolVaultLpAmount.signum() < 0 || vaultLpSupply.signum() <= 0
                || poolVaultLpAmount.compareTo(vaultLpSupply) > 0)
            throw new IllegalArgumentException("vault LP 持仓或 supply 无效");
        return poolVaultLpAmount.multiply(unlockedVaultAmount(vault, unixSeconds)).divide(vaultLpSupply);
    }
}
