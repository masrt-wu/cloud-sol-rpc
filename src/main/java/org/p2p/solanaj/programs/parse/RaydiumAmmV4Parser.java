package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Raydium AMM V4 布局和标准初始化 PDA；本类不代表 CLMM 或 OpenBook v2。
 * 本类不访问 RPC；只传原始字节时无法校验 owner，生产环境建议使用带 owner 的重载。
 */
public final class RaydiumAmmV4Parser {
    /** 该协议的主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
    /** 可缓存的发现字段；账户更新时仍需检查配置是否改变。 */
    private static final String[] STATIC_FIELDS = "nonce base_decimal quote_decimal base_vault quote_vault base_mint quote_mint lp_mint open_orders market_id market_program_id target_orders withdraw_queue lp_vault owner".split(" ");

    private RaydiumAmmV4Parser() { }

    /**
     * 解析池的静态发现信息和当前动态状态；不验证账户 owner
     * @param data RPC 或订阅返回的账户原始字节
     * @return 完整解析结果，可按字段路径读取
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("raydium-amm-v4", "AmmInfo", null, data, STATIC_FIELDS);
    }

    /**
     * 解析池的静态发现信息和当前动态状态，同时验证 owner
     * @param owner 该账户在链上的所属程序
     * @param data 账户原始字节
     * @return 完整解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-amm-v4", "AmmInfo", Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }

    /**
     * 推导固定 AMM authority，不使用 market 作为种子
     * @return 本地计算结果
     */
    public static PublicKey deriveAuthority() {
        return DexPda.derive(PROGRAM_ID, DexPda.seed("amm authority"));
    }
    /**
     * 推导标准初始化模型的池地址；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveStandardPool(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("amm_associated_seed"));
    }
    /**
     * 推导标准模型的 base vault；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveBaseVault(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("coin_vault_associated_seed"));
    }
    /**
     * 推导标准模型的 quote vault；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveQuoteVault(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("pc_vault_associated_seed"));
    }
    /**
     * 推导标准模型的 LP mint；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveLpMint(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("lp_mint_associated_seed"));
    }
    /**
     * 推导标准模型的 OpenOrders 地址；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveOpenOrders(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("open_order_associated_seed"));
    }
    /**
     * 推导标准模型的 TargetOrders 地址；真实池账户里保存的地址优先
     * @param market 已发现的 OpenBook/Serum market 地址
     * @return 本地计算结果
     */
    public static PublicKey deriveTargetOrders(PublicKey market) {
        return DexPda.derive(PROGRAM_ID, DexPda.key(PROGRAM_ID), DexPda.key(market), DexPda.seed("target_associated_seed"));
    }
    /**
     * 按 market 和其实际 nonce 推导市场 vault signer，不猜测 nonce
     * @param market market 地址
     * @param marketProgram 该 market 的所属程序
     * @param nonce market 保存的完整 u64 vaultSignerNonce
     * @return 本地计算结果
     */
    public static PublicKey deriveMarketVaultSigner(PublicKey market, PublicKey marketProgram, BigInteger nonce) {
        return PublicKey.createProgramAddress(List.of(DexPda.key(market), DexPda.u64(nonce)), marketProgram);
    }
    /**
     * 解析 AMM V4 使用的旧版 OpenOrders 余额；只验证布局，不验证 owner
     * @param data 原始账户字节
     * @return 余额和市场字段
     */
    public static DexAccountData parseOpenOrders(byte[] data) {
        checkMarketEnvelope(data, 3228);
        DexAccountData parsed = DexLayouts.parse("raydium-amm-v4", "OpenOrders", null, data, "market", "owner");
        if (!parsed.integer("account_flags").and(BigInteger.valueOf(5)).equals(BigInteger.valueOf(5)))
            throw new IllegalArgumentException("账户类型标记不匹配：OpenOrders");
        return parsed;
    }
    /**
     * 解析 AMM V4 使用的旧版 OpenOrders 余额，同时验证 market 程序
     * @param owner 该账户实际所属程序
     * @param marketProgram 池账户保存的 market_program_id
     * @param data 原始账户字节
     * @return 已验证程序所属关系的解析结果
     */
    public static DexAccountData parseOpenOrders(PublicKey owner, PublicKey marketProgram, byte[] data) {
        if (!Objects.requireNonNull(marketProgram, "market 程序不能为空").equals(owner))
            throw new IllegalArgumentException("OpenOrders/market 所属程序不匹配");
        return parseOpenOrders(data);
    }
    /**
     * 解析旧版 Serum/OpenBook market；不是 OpenBook v2 布局；只验证布局，不验证 owner
     * @param data 原始账户字节
     * @return 余额和市场字段
     */
    public static DexAccountData parseMarket(byte[] data) {
        checkMarketEnvelope(data, 388);
        DexAccountData parsed = DexLayouts.parse("raydium-amm-v4", "MarketState", null, data, "own_address", "base_mint", "quote_mint", "base_vault", "quote_vault", "request_queue", "event_queue", "bids", "asks", "vault_signer_nonce");
        if (!parsed.integer("account_flags").and(BigInteger.valueOf(3)).equals(BigInteger.valueOf(3)))
            throw new IllegalArgumentException("账户类型标记不匹配：MarketState");
        return parsed;
    }
    /**
     * 解析旧版 Serum/OpenBook market；不是 OpenBook v2 布局，同时验证 market 程序
     * @param owner 该账户实际所属程序
     * @param marketProgram 池账户保存的 market_program_id
     * @param data 原始账户字节
     * @return 已验证程序所属关系的解析结果
     */
    public static DexAccountData parseMarket(PublicKey owner, PublicKey marketProgram, byte[] data) {
        if (!Objects.requireNonNull(marketProgram, "market 程序不能为空").equals(owner))
            throw new IllegalArgumentException("OpenOrders/market 所属程序不匹配");
        return parseMarket(data);
    }
    /**
     * 计算 vault 加 OpenOrders 总余额再减 needTakePnl；不是完整 swap 报价
     * @param pool 当前 AmmInfo 快照
     * @param baseVaultAmount 当前 base vault 余额
     * @param quoteVaultAmount 当前 quote vault 余额
     * @param openOrders 传统订单簿模型的 OpenOrders；无订单簿模式传 null
     * @return 扣除待领取 PNL 的两侧储备
     */
    public static DexReserves effectiveReserves(DexAccountData pool, BigInteger baseVaultAmount, BigInteger quoteVaultAmount, DexAccountData openOrders) {
        BigInteger base = baseVaultAmount;
        BigInteger quote = quoteVaultAmount;
        if (openOrders != null) {
            if (!pool.publicKey("market_id").equals(openOrders.publicKey("market")))
                throw new IllegalArgumentException("OpenOrders 的 market 与池不匹配");
            base = base.add(openOrders.integer("base_token_total"));
            quote = quote.add(openOrders.integer("quote_token_total"));
        }
        return new DexReserves(base.subtract(pool.integer("base_need_take_pnl")),
                quote.subtract(pool.integer("quote_need_take_pnl")));
    }
    /** 校验旧市场布局的固定头尾标记，防止把另一种账户误读成余额。 */
    private static void checkMarketEnvelope(byte[] data, int minimumLength) {
        if (data == null || data.length < minimumLength
                || !Arrays.equals(Arrays.copyOfRange(data, 0, 5), "serum".getBytes(StandardCharsets.US_ASCII))
                || !Arrays.equals(Arrays.copyOfRange(data, data.length - 7, data.length), "padding".getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("旧版市场账户布局或长度不正确");
    }
}
