package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.Objects;

/** PumpSwap 池和全局配置解析；兼容官方允许缺失的追加尾部字段。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class PumpSwapParser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "pool_bump index creator base_mint quote_mint lp_mint pool_base_token_account pool_quote_token_account coin_creator".split(" ");

    private PumpSwapParser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("pumpswap", "Pool", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("pumpswap", "Pool", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
    /**
     * 解析全局费率、禁用标记和接收方；配置变更后必须刷新；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parseGlobalConfig(byte[] data) {
        return DexLayouts.parse("pumpswap", "GlobalConfig", null, data);
    }

    /**
     * 解析全局费率、禁用标记和接收方；配置变更后必须刷新，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parseGlobalConfig(PublicKey owner, byte[] data) {
        return DexLayouts.parse("pumpswap", "GlobalConfig", Objects.requireNonNull(owner, "账户 owner 不能为空"), data);
    }

    /**
     * 按官方 pool、索引、创建者和两个 mint 推导池 PDA；不交换 mint 顺序
     * @param index 池索引，u16 小端
     * @param creator 创建者种子，不是 coinCreator
     * @param baseMint 基础代币 mint
     * @param quoteMint 支付代币 mint
     * @return 本地计算结果
     */
    public static PublicKey derivePool(int index, PublicKey creator, PublicKey baseMint, PublicKey quoteMint) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool"), DexPda.u16(index, ByteOrder.LITTLE_ENDIAN),
                DexPda.key(creator), DexPda.key(baseMint), DexPda.key(quoteMint));
    }
    /**
     * 推导池的 LP mint PDA
     * @param pool 真实池地址
     * @return 本地计算结果
     */
    public static PublicKey deriveLpMint(PublicKey pool) {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("pool_lp_mint"), DexPda.key(pool));
    }
    /**
     * 推导池的代币 ATA，必须正确区分普通 Token 与 Token-2022
     * @param pool 真实池地址，作为 ATA 持有者
     * @param mint base 或 quote mint
     * @param tokenProgram 该 mint 实际所属的 Token 程序
     * @return 本地计算结果
     */
    public static PublicKey deriveVault(PublicKey pool, PublicKey mint, PublicKey tokenProgram) {
        return DexPda.associatedTokenAccount(pool, mint, tokenProgram);
    }
    /**
     * 推导固定全局配置 PDA
     * @return 本地计算结果
     */
    public static PublicKey deriveGlobalConfig() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("global_config"));
    }
    /**
     * 加入有符号 virtual_quote_reserves；这只是储备计算，不是完整成交报价
     * @param pool 当前 Pool 快照
     * @param baseVaultAmount 当前 base vault 的实际余额
     * @param quoteVaultAmount 当前 quote vault 的实际余额
     * @return base 储备与有效 quote 储备
     */
    public static DexReserves effectiveReserves(DexAccountData pool, BigInteger baseVaultAmount, BigInteger quoteVaultAmount) {
        return new DexReserves(baseVaultAmount, quoteVaultAmount.add(pool.integer("virtual_quote_reserves")));
    }
}
