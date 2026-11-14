package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/** Raydium CPMM 池解析；协议也允许非 PDA 的随机 signer 池，必须以真实池地址为准。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class RaydiumCpmmParser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "amm_config pool_creator token_0_vault token_1_vault lp_mint token_0_mint token_1_mint token_0_program token_1_program observation_key auth_bump lp_mint_decimals mint_0_decimals mint_1_decimals".split(" ");

    private RaydiumCpmmParser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "PoolState", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "PoolState", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析费率配置；费率或管理员变更后必须刷新；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseAmmConfig(byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "AmmConfig", null, data);
    }

    /**
     * 解析费率配置；费率或管理员变更后必须刷新，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseAmmConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "AmmConfig", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }
    /**
     * 解析池的动态价格观察数据；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseObservation(byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "ObservationState", null, data);
    }

    /**
     * 解析池的动态价格观察数据，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseObservation(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-cpmm", "ObservationState", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /**
     * 推导 CPMM 的固定 vault 和 LP mint authority
     * @return 本地计算结果
     */
    public static PublicKey deriveAuthority() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("vault_and_lp_mint_auth_seed"));
    }
    /**
     * 推导 CPMM 配置 PDA
     * @param index 配置索引，u16 大端
     * @return 本地计算结果
     */
    public static PublicKey deriveAmmConfig(int index) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("amm_config"), DexPda.u16(index, ByteOrder.BIG_ENDIAN));
    }
    /**
     * 推导标准 PDA 池；随机 signer 池不能用此方法发现
     * @param ammConfig 配置地址
     * @param mintA 任意一侧 mint
     * @param mintB 另一侧 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveStandardPool(PublicKey ammConfig, PublicKey mintA, PublicKey mintB) {
        List<PublicKey> mints = DexPda.ordered(mintA, mintB, true);
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool"), DexPda.key(ammConfig),
                DexPda.key(mints.get(0)), DexPda.key(mints.get(1)));
    }
    /**
     * 推导 LP mint PDA
     * @param pool 真实池地址，可以是 PDA 或随机 signer 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveLpMint(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_lp_mint"), DexPda.key(pool));
    }
    /**
     * 推导 观察账户 PDA
     * @param pool 真实池地址，可以是 PDA 或随机 signer 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveObservation(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("observation"), DexPda.key(pool));
    }
    /**
     * 推导任意一侧的 token vault
     * @param pool 真实池地址
     * @param mint 对应代币 mint
     * @return 本地计算结果
     */
    public static PublicKey deriveVault(PublicKey pool, PublicKey mint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_vault"), DexPda.key(pool), DexPda.key(mint));
    }
    /**
     * 扣除协议费、基金费和 creator fee 累计；不是完整 swap 报价
     * @param pool 当前 PoolState 快照
     * @param vault0Amount 当前 token_0_vault 余额
     * @param vault1Amount 当前 token_1_vault 余额
     * @return 本地计算结果
     */
    public static DexReserves effectiveReserves(DexAccountData pool, BigInteger vault0Amount, BigInteger vault1Amount) {
        BigInteger fee0 = pool.integer("protocol_fees_token_0").add(pool.integer("fund_fees_token_0"))
                .add(pool.integer("creator_fees_token_0"));
        BigInteger fee1 = pool.integer("protocol_fees_token_1").add(pool.integer("fund_fees_token_1"))
                .add(pool.integer("creator_fees_token_1"));
        return new DexReserves(vault0Amount.subtract(fee0), vault1Amount.subtract(fee1));
    }
}
