package org.p2p.solanaj.router.pda.ray;


import org.p2p.solanaj.core.PublicKey;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Raydium AMM V4 PDA 地址生成器。
 *
 * <p>
 * 用于根据：
 *
 * <pre>
 * Raydium AMM ProgramId
 * +
 * OpenBook / Serum MarketId
 * </pre>
 * <p>
 * 本地派生 Raydium AMM V4 的固定关联账户。
 *
 * <p>
 * 这些地址不需要通过 RPC 每次查询，
 * 只要 ProgramId 和 MarketId 确定，就可以直接本地计算。
 */
public final class RaydiumV4Pda {

    /**
     * Raydium AMM V4 主网 Program。
     */
    public static final PublicKey RAYDIUM_V4_PROGRAM = new PublicKey("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");


    // ============================================================
    // Raydium 官方 PDA Seed
    // ============================================================

    /**
     * AMM Pool Account 的 PDA Seed。
     */
    private static final byte[] AMM_ASSOCIATED_SEED = "amm_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * AMM Authority 的 PDA Seed。
     *
     * <p>
     * 注意：
     * Authority 和其他 Pool PDA 不一样，
     * 它只使用这个 Seed，不使用 MarketId。
     */
    private static final byte[] AMM_AUTHORITY_SEED = "amm authority".getBytes(StandardCharsets.UTF_8);

    /**
     * Base / Coin Vault 的 PDA Seed。
     */
    private static final byte[] COIN_VAULT_ASSOCIATED_SEED = "coin_vault_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * Quote / PC Vault 的 PDA Seed。
     */
    private static final byte[] PC_VAULT_ASSOCIATED_SEED = "pc_vault_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * LP Mint 的 PDA Seed。
     */
    private static final byte[] LP_MINT_ASSOCIATED_SEED = "lp_mint_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * OpenOrders Account 的 PDA Seed。
     */
    private static final byte[] OPEN_ORDER_ASSOCIATED_SEED = "open_order_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * TargetOrders Account 的 PDA Seed。
     */
    private static final byte[] TARGET_ASSOCIATED_SEED = "target_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * WithdrawQueue 的 PDA Seed。
     */
    private static final byte[] WITHDRAW_ASSOCIATED_SEED = "withdraw_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * 临时 LP Vault 的 PDA Seed。
     */
    private static final byte[] TEMP_LP_TOKEN_ASSOCIATED_SEED = "temp_lp_token_associated_seed".getBytes(StandardCharsets.UTF_8);

    /**
     * Raydium AMM Config PDA Seed。
     */
    private static final byte[] AMM_CONFIG_SEED = "amm_config_account_seed".getBytes(StandardCharsets.UTF_8);


    /**
     * 禁止实例化工具类。
     */
    private RaydiumV4Pda() {
    }


    // ============================================================
    // 一次性生成全部地址
    // ============================================================

    /**
     * 根据 Raydium AMM ProgramId 和 MarketId，
     * 一次性生成 Raydium AMM V4 所有可以确定性派生的地址。
     *
     * <p>
     * 主要生成：
     *
     * <pre>
     * poolId
     * authority
     * baseVault
     * quoteVault
     * lpMint
     * openOrders
     * targetOrders
     * withdrawQueue
     * lpVault
     * configId
     * </pre>
     *
     * @param programId Raydium AMM Program 地址。
     *                  <p>
     *                  主网 AMM V4 通常为：
     *                  <p>
     *                  675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8
     * @param marketId  当前 Raydium Pool 绑定的 OpenBook / Serum Market 地址。
     *                  <p>
     *                  该地址一般从 Raydium Pool Account 链上数据中的
     *                  marketId 字段读取。
     * @return 本地派生完成的 RaydiumV4AssociatedKeys。
     * <p>
     * 整个过程不需要 RPC。
     */
    public static RaydiumV4AssociatedKeys generate(PublicKey programId, PublicKey marketId) {

        if (programId == null) {
            throw new IllegalArgumentException("programId 不能为空");
        }

        if (marketId == null) {
            throw new IllegalArgumentException("marketId 不能为空");
        }


        // --------------------------------------------------------
        // AMM Pool ID
        // --------------------------------------------------------

        PublicKey poolId = deriveAssociatedAddress(programId, marketId, AMM_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // AMM Authority
        //
        // 整个 Raydium AMM Program 共用。
        //
        // 不包含 marketId。
        // --------------------------------------------------------

        PublicKey authority = deriveAuthority(programId);


        // --------------------------------------------------------
        // Base / Coin Vault
        // --------------------------------------------------------

        PublicKey baseVault = deriveAssociatedAddress(programId, marketId, COIN_VAULT_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // Quote / PC Vault
        // --------------------------------------------------------

        PublicKey quoteVault = deriveAssociatedAddress(programId, marketId, PC_VAULT_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // LP Mint
        // --------------------------------------------------------

        PublicKey lpMint = deriveAssociatedAddress(programId, marketId, LP_MINT_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // OpenOrders
        // --------------------------------------------------------

        PublicKey openOrders = deriveAssociatedAddress(programId, marketId, OPEN_ORDER_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // TargetOrders
        // --------------------------------------------------------

        PublicKey targetOrders = deriveAssociatedAddress(programId, marketId, TARGET_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // WithdrawQueue
        //
        // 注意：
        //
        // 老池链上实际字段可能直接是：
        //
        // 11111111111111111111111111111111
        //
        // 所以对于已经存在的池，
        // 实际使用时仍然应该优先相信 Pool Account 中保存的值。
        // --------------------------------------------------------

        PublicKey withdrawQueue = deriveAssociatedAddress(programId, marketId, WITHDRAW_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // 临时 LP Vault
        //
        // 与 WithdrawQueue 类似：
        //
        // 对已经存在的 Pool，
        // 链上 Pool Account 中实际存储的值优先。
        // --------------------------------------------------------

        PublicKey lpVault = deriveAssociatedAddress(programId, marketId, TEMP_LP_TOKEN_ASSOCIATED_SEED);


        // --------------------------------------------------------
        // Raydium AMM Config
        //
        // Config 是 Program 级 PDA，
        // 不依赖 MarketId。
        // --------------------------------------------------------

        PublicKey configId = deriveConfigId(programId);


        return new RaydiumV4AssociatedKeys(poolId, authority,

                baseVault, quoteVault,

                lpMint, openOrders, targetOrders,

                withdrawQueue, lpVault,

                configId);
    }


    /**
     * 使用 Raydium AMM V4 主网 ProgramId，
     * 根据 MarketId 直接生成关联地址。
     *
     * <p>
     * 等价于：
     *
     * <pre>
     * generate(
     *     RAYDIUM_V4_PROGRAM,
     *     marketId
     * )
     * </pre>
     *
     * @param marketId Raydium Pool 对应的 OpenBook / Serum Market 地址
     * @return Raydium AMM V4 关联 PDA
     */
    public static RaydiumV4AssociatedKeys generate(PublicKey marketId) {

        return generate(RAYDIUM_V4_PROGRAM, marketId);
    }


    // ============================================================
    // 单独生成 Pool ID
    // ============================================================

    /**
     * 根据 ProgramId 和 MarketId 派生 Raydium Pool ID。
     *
     * <p>
     * Seed：
     *
     * <pre>
     * [
     *     programId,
     *     marketId,
     *     "amm_associated_seed"
     * ]
     * </pre>
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  OpenBook / Serum Market 地址
     * @return Raydium AMM Pool Account PDA
     */
    public static PublicKey derivePoolId(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, AMM_ASSOCIATED_SEED);
    }


    // ============================================================
    // AMM Authority
    // ============================================================

    /**
     * 派生 Raydium AMM Authority。
     *
     * <p>
     * Seed：
     *
     * <pre>
     * [
     *     "amm authority"
     * ]
     * </pre>
     *
     * <p>
     * 注意：
     * <p>
     * 该 PDA 不依赖 MarketId，
     * 所以同一个 Raydium Program 下所有 Pool
     * 使用同一个 Authority。
     *
     * @param programId Raydium AMM Program 地址
     * @return Raydium AMM Authority PDA
     */
    public static PublicKey deriveAuthority(PublicKey programId) {

        return PublicKey.findProgramAddress(List.of(AMM_AUTHORITY_SEED), programId).getAddress();
    }


    // ============================================================
    // Base Vault
    // ============================================================

    /**
     * 派生 Raydium Base / Coin Vault。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return Base Token Vault PDA
     */
    public static PublicKey deriveBaseVault(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, COIN_VAULT_ASSOCIATED_SEED);
    }


    // ============================================================
    // Quote Vault
    // ============================================================

    /**
     * 派生 Raydium Quote / PC Vault。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return Quote Token Vault PDA
     */
    public static PublicKey deriveQuoteVault(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, PC_VAULT_ASSOCIATED_SEED);
    }


    // ============================================================
    // LP Mint
    // ============================================================

    /**
     * 派生 Raydium Pool 的 LP Token Mint。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return LP Mint PDA
     */
    public static PublicKey deriveLpMint(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, LP_MINT_ASSOCIATED_SEED);
    }


    // ============================================================
    // OpenOrders
    // ============================================================

    /**
     * 派生 Raydium Pool 对应的 OpenOrders Account。
     *
     * <p>
     * 该账户用于 Raydium AMM 与 OpenBook / Serum
     * OrderBook 之间的订单和资金状态。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return OpenOrders PDA
     */
    public static PublicKey deriveOpenOrders(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, OPEN_ORDER_ASSOCIATED_SEED);
    }


    // ============================================================
    // TargetOrders
    // ============================================================

    /**
     * 派生 Raydium TargetOrders Account。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return TargetOrders PDA
     */
    public static PublicKey deriveTargetOrders(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, TARGET_ASSOCIATED_SEED);
    }


    // ============================================================
    // WithdrawQueue
    // ============================================================

    /**
     * 派生 Raydium WithdrawQueue 关联 PDA。
     *
     * <p>
     * 注意：
     * <p>
     * 对于已经存在的老 Pool，
     * Pool Account 中保存的 withdrawQueue
     * 可能是 System Program 默认地址：
     *
     * <pre>
     * 11111111111111111111111111111111
     * </pre>
     * <p>
     * 所以生成出来的关联 PDA
     * 不应该无条件覆盖链上 Pool State 中的实际值。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return WithdrawQueue 关联 PDA
     */
    public static PublicKey deriveWithdrawQueue(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, WITHDRAW_ASSOCIATED_SEED);
    }


    // ============================================================
    // LP Vault
    // ============================================================

    /**
     * 派生 Raydium 临时 LP Token Vault。
     *
     * <p>
     * 同样需要注意：
     * <p>
     * 对现存 Pool，
     * 实际值优先读取 Pool Account 中的 lpVault 字段。
     *
     * @param programId Raydium AMM Program 地址
     * @param marketId  当前 Pool 对应的 Market 地址
     * @return 临时 LP Token Vault PDA
     */
    public static PublicKey deriveLpVault(PublicKey programId, PublicKey marketId) {

        return deriveAssociatedAddress(programId, marketId, TEMP_LP_TOKEN_ASSOCIATED_SEED);
    }


    // ============================================================
    // AMM Config
    // ============================================================

    /**
     * 派生 Raydium AMM Config PDA。
     *
     * <p>
     * Seed：
     *
     * <pre>
     * [
     *     "amm_config_account_seed"
     * ]
     * </pre>
     *
     * <p>
     * Config 是 Program 级 PDA，
     * 不依赖 MarketId。
     *
     * @param programId Raydium AMM Program 地址
     * @return Raydium AMM Config PDA
     */
    public static PublicKey deriveConfigId(PublicKey programId) {

        return PublicKey.findProgramAddress(List.of(AMM_CONFIG_SEED), programId).getAddress();
    }


    // ============================================================
    // 通用 Pool 关联 PDA 方法
    // ============================================================

    /**
     * 派生一个 Raydium AMM V4 Market 关联 PDA。
     *
     * <p>
     * Raydium 大部分 Pool 账户都使用统一格式：
     *
     * <pre>
     * PDA(
     *     [
     *         programId,
     *         marketId,
     *         associatedSeed
     *     ],
     *     programId
     * )
     * </pre>
     *
     * @param programId      Raydium AMM Program 地址，
     *                       同时也作为 PDA ProgramId
     * @param marketId       当前 Pool 绑定的 OpenBook / Serum Market
     * @param associatedSeed Raydium 官方定义的关联 Seed，例如：
     *                       <p>
     *                       amm_associated_seed
     *                       <p>
     *                       coin_vault_associated_seed
     *                       <p>
     *                       pc_vault_associated_seed
     *                       <p>
     *                       open_order_associated_seed
     * @return 根据三个 Seed 计算出来的 PDA 地址
     */
    private static PublicKey deriveAssociatedAddress(PublicKey programId, PublicKey marketId, byte[] associatedSeed) {

        return PublicKey.findProgramAddress(List.of(programId.toByteArray(), marketId.toByteArray(), associatedSeed), programId).getAddress();
    }


    // ============================================================
    // 返回对象
    // ============================================================

    /**
     * Raydium AMM V4 可以本地确定性生成的关联账户集合。
     *
     * @param poolId        Raydium AMM Pool Account，
     *                      即通常所说的 AMM ID / Pool ID
     * @param authority     Raydium AMM Authority，
     *                      用于控制 AMM Vault 等账户
     * @param baseVault     Pool 的 Base / Coin Token Vault
     * @param quoteVault    Pool 的 Quote / PC Token Vault
     * @param lpMint        当前 Pool 的 LP Token Mint
     * @param openOrders    Raydium 在 OpenBook / Serum Market 上使用的
     *                      OpenOrders Account
     * @param targetOrders  Raydium AMM TargetOrders Account
     * @param withdrawQueue Raydium WithdrawQueue 的关联 PDA。
     *                      对已存在池实际使用时，
     *                      应优先使用 Pool State 中存储的地址
     * @param lpVault       Raydium 临时 LP Token Vault 的关联 PDA。
     *                      对已存在池实际使用时，
     *                      应优先使用 Pool State 中存储的地址
     * @param configId      当前 Raydium AMM Program 的 Config PDA
     */
    public record RaydiumV4AssociatedKeys(

            PublicKey poolId,

            PublicKey authority,

            PublicKey baseVault,

            PublicKey quoteVault,

            PublicKey lpMint,

            PublicKey openOrders,

            PublicKey targetOrders,

            PublicKey withdrawQueue,

            PublicKey lpVault,

            PublicKey configId) {
    }

    public static void main(String[] args) {
        RaydiumV4AssociatedKeys associatedKeys = generate(new PublicKey("8BnEgHoWFysVcuFFX7QztDmzuH8r5ZFvyP3sYwn1XTh6"));
        System.out.println(associatedKeys);

        //PublicKey.findProgramAddress(List.of(programId.toByteArray(), marketId.toByteArray(), associatedSeed));
        var baseValue = deriveBaseVault(RAYDIUM_V4_PROGRAM, new PublicKey("A9prkHaq97VWDGRP2uh29gmmqXKEjoXCUXx9vorGczKr"));
        System.out.println(baseValue);
    }
}
