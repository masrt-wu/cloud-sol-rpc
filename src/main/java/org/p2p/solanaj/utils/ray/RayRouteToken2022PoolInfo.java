package org.p2p.solanaj.utils.ray;

import lombok.Data;

import java.util.List;


/**
 * Raydium Router + LaunchLab + CLMM
 * Token2022 路由交易所需账户信息。
 *
 * <p>
 * 当前对应两种路由：
 *
 * <pre>
 * BUY:
 *
 * SOL
 *   ↓ wrap
 * WSOL
 *   ↓ Raydium AMM V4
 * BONK
 *   ↓ Raydium LaunchLab BuyExactIn
 * Token2022
 *
 *
 * SELL:
 *
 * Token2022
 *   ↓ Raydium LaunchLab SellExactIn
 * BONK
 *   ↓ Raydium CLMM
 * USDC
 *   ↓ Raydium CLMM
 * WSOL
 *   ↓ close
 * SOL
 * </pre>
 *
 * <p>
 * 注意：
 *
 * 1. LaunchLab 相关 PDA / Pool / Vault 通常可以缓存。
 *
 * 2. AMM V4 Pool / Vault 通常也可以缓存。
 *
 * 3. CLMM 的 TickArray / TickArrayBitmapExtension
 *    不能长期硬编码，因为价格变化可能导致当前 TickArray 变化。
 *
 * 4. userBaseToken / userBonk / userUsdc / userWsol
 *    都是当前 payer 对应的 Token Account / ATA。
 */
@Data
public class RayRouteToken2022PoolInfo {


    /*
     * ============================================================
     * Common
     *
     * 整个 Raydium Router 交易公共账户
     * ============================================================
     */


    /**
     * 当前交易钱包地址。
     *
     * <p>
     * 作用：
     * - transaction payer
     * - swap authority
     * - signer
     * - 创建 ATA 时支付 rent
     * - 买入时支付 SOL
     * - 卖出时接收最终 SOL
     *
     * <p>
     * 示例：
     * B3s5jQff...
     */
    private String payer;


    /**
     * Raydium AMM Routing Program。
     *
     * <p>
     * 当前：
     *
     * routeUGWgWzqBWFcrCfv8tritsqukccJPu3q5GPP3xS
     *
     * <p>
     * 负责：
     * - create/sync WSOL
     * - 多跳 swap
     * - close WSOL
     *
     * <p>
     * BUY:
     * WSOL -> BONK -> Token2022
     *
     * SELL:
     * Token2022 -> BONK -> USDC -> WSOL
     */
    private String routeProgram;


    /**
     * 标准 SPL Token Program。
     *
     * <p>
     * 固定地址：
     *
     * TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     *
     * <p>
     * 本路由中主要处理：
     * - WSOL
     * - BONK
     * - USDC
     */
    private String tokenProgram;


    /**
     * SPL Token-2022 Program。
     *
     * <p>
     * 固定地址：
     *
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     *
     * <p>
     * 当前目标币 5HcMu... 属于 Token2022，
     * 所以 Base Token 的 transferChecked 等操作走这个 Program。
     */
    private String token2022Program;


    /**
     * Associated Token Account Program。
     *
     * <p>
     * 固定地址：
     *
     * ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL
     *
     * <p>
     * 用于：
     * - 创建 WSOL ATA
     * - 创建 Token2022 ATA
     * - 创建其它用户 ATA
     */
    private String associatedTokenProgram;


    /**
     * Solana System Program。
     *
     * <p>
     * 固定地址：
     *
     * 11111111111111111111111111111111
     *
     * <p>
     * 用于：
     * - 创建账户
     * - SOL transfer
     * - ATA 初始化过程
     */
    private String systemProgram;


    /**
     * SPL Memo Program。
     *
     * <p>
     * 当前：
     *
     * MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr
     *
     * <p>
     * Raydium CLMM SwapV2 的账户参数之一。
     */
    private String memoProgram;



    /*
     * ============================================================
     * User Token Accounts
     *
     * 当前 payer 对应的用户 Token Account / ATA
     * ============================================================
     */


    /**
     * 用户目标 Token2022 的 ATA。
     *
     * <p>
     * BUY:
     * 最终买到的 Token2022 进入这个账户。
     *
     * <p>
     * SELL:
     * 要卖出的 Token2022 从这个账户转出。
     *
     * <p>
     * 示例：
     * 9Ay3pXKE...
     */
    private String userBaseToken;


    /**
     * 用户 BONK ATA。
     *
     * <p>
     * BUY:
     *
     * WSOL -> BONK
     *
     * 第一跳 AMM V4 得到的 BONK
     * 暂时进入该账户，
     * 然后马上作为 LaunchLab BuyExactIn 的输入。
     *
     * <p>
     * SELL:
     *
     * Token2022 -> BONK
     *
     * LaunchLab SellExactIn 得到的 BONK
     * 进入该账户，
     * 然后作为 BONK -> USDC CLMM 的输入。
     *
     * <p>
     * 示例：
     * 25GZpYwr...
     */
    private String userBonk;


    /**
     * 用户 USDC ATA。
     *
     * <p>
     * 主要用于 SELL 路由：
     *
     * BONK
     *   ↓
     * CLMM
     *   ↓
     * USDC
     *
     * <p>
     * 第一段 CLMM 的输出，
     * 同时作为第二段 USDC -> WSOL CLMM 的输入。
     *
     * <p>
     * BUY 当前路线不使用该账户。
     */
    private String userUsdc;


    /**
     * 用户 WSOL ATA。
     *
     * <p>
     * BUY:
     *
     * SOL 先 wrap 成 WSOL 放入这里，
     * 然后作为 WSOL -> BONK 的输入。
     *
     * <p>
     * SELL:
     *
     * USDC -> WSOL 的最终输出进入这里，
     * 最后通过 close token account
     * 将 WSOL 转回 native SOL。
     *
     * <p>
     * 示例：
     * CHmPBhZM...
     */
    private String userWsol;



    /*
     * ============================================================
     * Mint
     * ============================================================
     */


    /**
     * 目标 Token2022 Mint。
     *
     * <p>
     * 即实际要买入 / 卖出的币。
     *
     * <p>
     * BUY:
     * 最终 output mint。
     *
     * <p>
     * SELL:
     * 最初 input mint。
     *
     * <p>
     * 示例：
     * 5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ
     */
    private String baseMint;


    /**
     * BONK Mint。
     *
     * <p>
     * 固定：
     *
     * DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263
     *
     * <p>
     * 是 LaunchLab 当前池子的 Quote Token。
     *
     * <p>
     * BUY:
     * WSOL -> BONK -> Base Token
     *
     * <p>
     * SELL:
     * Base Token -> BONK -> USDC
     */
    private String bonkMint;


    /**
     * USDC Mint。
     *
     * <p>
     * Solana 主网 USDC：
     *
     * EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
     *
     * <p>
     * SELL 路由中作为中间币：
     *
     * BONK -> USDC -> WSOL
     */
    private String usdcMint;


    /**
     * Wrapped SOL Mint。
     *
     * <p>
     * 固定：
     *
     * So11111111111111111111111111111111111111112
     *
     * <p>
     * BUY:
     * 第一跳输入。
     *
     * <p>
     * SELL:
     * 最终输出。
     */
    private String wsolMint;



    /*
     * ============================================================
     * LaunchLab
     *
     * Token2022 <-> BONK
     * ============================================================
     */


    /**
     * Raydium LaunchLab / Launchpad Program。
     *
     * <p>
     * 当前：
     *
     * LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj
     *
     * <p>
     * BUY 调用：
     * BuyExactIn
     *
     * <p>
     * SELL 调用：
     * SellExactIn
     */
    private String launchProgram;


    /**
     * LaunchLab Authority PDA。
     *
     * <p>
     * LaunchLab Program 用于控制 Pool Vault
     * 等账户的 PDA Authority。
     *
     * <p>
     * 示例：
     * WLHv2UAZ...
     */
    private String authority;


    /**
     * LaunchLab Global Config。
     *
     * <p>
     * 全局配置账户。
     *
     * <p>
     * 包含 LaunchLab 全局协议相关配置。
     *
     * <p>
     * 示例：
     * 7G6Gg6rC...
     */
    private String globalConfig;


    /**
     * LaunchLab Platform Config。
     *
     * <p>
     * 当前 LaunchLab 平台对应配置账户。
     *
     * <p>
     * 通常和：
     * - fee
     * - platform
     * - claim
     * 等配置有关。
     *
     * <p>
     * 示例：
     * 6BwHHDg3...
     */
    private String platformConfig;


    /**
     * LaunchLab Pool State。
     *
     * <p>
     * 当前 Token2022/BONK LaunchLab 池状态账户。
     *
     * <p>
     * BUY / SELL 都会修改，
     * 因此必须 writable。
     *
     * <p>
     * 示例：
     * Bv3dcBWq...
     */
    private String poolState;


    /**
     * LaunchLab Base Token Vault。
     *
     * <p>
     * Base = 当前目标 Token2022。
     *
     * <p>
     * BUY:
     * Token2022 从该 vault 支付给用户。
     *
     * <p>
     * SELL:
     * 用户 Token2022 转入该 vault。
     *
     * <p>
     * 示例：
     * 8Ty7vwg4...
     */
    private String baseVault;


    /**
     * LaunchLab Quote Token Vault。
     *
     * <p>
     * Quote = BONK。
     *
     * <p>
     * BUY:
     * 用户 BONK 转入该 vault。
     *
     * <p>
     * SELL:
     * BONK 从该 vault 支付给用户。
     *
     * <p>
     * 示例：
     * F2i1ksJk...
     */
    private String quoteVault;


    /**
     * LaunchLab Event Authority PDA。
     *
     * <p>
     * Anchor event CPI 相关账户。
     *
     * <p>
     * 示例：
     * 2DPAtwB8...
     */
    private String eventAuthority;


    /**
     * Platform Fee Vault。
     *
     * <p>
     * LaunchLab 平台手续费接收账户。
     *
     * <p>
     * 你的实际交易中，
     * BONK 手续费有转入该账户。
     *
     * <p>
     * 示例：
     * EoHk1RAM...
     */
    private String platformFeeVault;


    /**
     * Creator Fee Vault。
     *
     * <p>
     * LaunchLab creator fee 对应账户。
     *
     * <p>
     * 某些交易可能没有实际转账，
     * 但 instruction account 仍然必须传入。
     *
     * <p>
     * 示例：
     * CsDoqYxi...
     */
    private String creatorFeeVault;



    /*
     * ============================================================
     * BUY ROUTE
     *
     * WSOL -> BONK
     *
     * Raydium AMM V4
     * ============================================================
     */


    /**
     * Raydium AMM V4 Program。
     *
     * <p>
     * 当前固定：
     *
     * 675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8
     *
     * <p>
     * BUY 路由第一跳：
     *
     * WSOL -> BONK
     */
    private String ammV4Program;


    /**
     * WSOL/BONK AMM V4 Pool State。
     *
     * <p>
     * 当前买入路径使用的 Raydium AMM V4 池。
     *
     * <p>
     * Router 对 AMM V4 构造 remaining accounts 时，
     * 某些 legacy market account slot
     * 也会重复使用这个地址。
     *
     * <p>
     * 示例：
     * HVNwzt7P...
     */
    private String ammV4PoolState;


    /**
     * Raydium AMM V4 Authority。
     *
     * <p>
     * 控制 AMM Vault 的 Authority PDA。
     *
     * <p>
     * 示例：
     * 5Q544fKr...
     */
    private String ammV4Authority;


    /**
     * WSOL/BONK AMM V4 的 BONK Vault。
     *
     * <p>
     * BUY 时：
     *
     * BONK 从该 Vault 转给 userBonk。
     *
     * <p>
     * 示例：
     * 7KFdXKA5...
     */
    private String ammV4BonkVault;


    /**
     * WSOL/BONK AMM V4 的 WSOL Vault。
     *
     * <p>
     * BUY 时：
     *
     * 用户 WSOL 转入该 Vault。
     *
     * <p>
     * 示例：
     * GehmCo7E...
     */
    private String ammV4WsolVault;



    /*
     * ============================================================
     * SELL ROUTE
     *
     * BONK -> USDC
     *
     * Raydium CLMM
     * ============================================================
     */


    /**
     * Raydium CLMM Program。
     *
     * <p>
     * 当前：
     *
     * CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK
     *
     * <p>
     * SELL 中两跳都会使用：
     *
     * BONK -> USDC
     * USDC -> WSOL
     */
    private String clmmProgram;


    /**
     * BONK/USDC CLMM AMM Config。
     *
     * <p>
     * 当前 CLMM Pool 对应的配置账户。
     *
     * <p>
     * 包含：
     * - tick spacing
     * - trade fee
     * - protocol fee
     * 等配置。
     */
    private String bonkUsdcAmmConfig;


    /**
     * BONK/USDC CLMM Pool State。
     *
     * <p>
     * SELL 第二跳：
     *
     * BONK -> USDC
     *
     * 对应的 CLMM Pool。
     */
    private String bonkUsdcPoolState;


    /**
     * BONK/USDC CLMM 的 BONK Vault。
     *
     * <p>
     * SELL 时：
     *
     * userBonk
     *   -> BONK vault
     */
    private String bonkUsdcBonkVault;


    /**
     * BONK/USDC CLMM 的 USDC Vault。
     *
     * <p>
     * SELL 时：
     *
     * USDC vault
     *   -> userUsdc
     */
    private String bonkUsdcUsdcVault;


    /**
     * BONK/USDC CLMM Observation State。
     *
     * <p>
     * Raydium CLMM SwapV2 必需账户之一。
     *
     * <p>
     * 与 oracle / observation 数据相关。
     */
    private String bonkUsdcObservationState;


    /**
     * BONK -> USDC CLMM 的 remaining accounts。
     *
     * <p>
     * 通常包括：
     *
     * - TickArrayBitmapExtension
     * - TickArray
     * - 可能多个 TickArray
     *
     * <p>
     * 非常重要：
     *
     * 这些账户不能认为永久固定。
     *
     * 当前价格/current tick 发生变化后，
     * swap 所需要访问的 TickArray 可能变化。
     *
     * <p>
     * 所以策略程序最好根据最新 Pool State
     * 动态计算或者定期更新。
     */
    private List<String> bonkUsdcRemainingAccounts;



    /*
     * ============================================================
     * SELL ROUTE
     *
     * USDC -> WSOL
     *
     * Raydium CLMM
     * ============================================================
     */


    /**
     * USDC/WSOL CLMM AMM Config。
     *
     * <p>
     * SELL 最后一跳：
     *
     * USDC -> WSOL
     *
     * 所使用 CLMM Pool 的配置账户。
     */
    private String usdcWsolAmmConfig;


    /**
     * USDC/WSOL CLMM Pool State。
     *
     * <p>
     * SELL 最后一跳：
     *
     * USDC -> WSOL
     */
    private String usdcWsolPoolState;


    /**
     * USDC/WSOL CLMM 的 USDC Vault。
     *
     * <p>
     * SELL 时：
     *
     * userUsdc
     *   -> USDC Vault
     */
    private String usdcWsolUsdcVault;


    /**
     * USDC/WSOL CLMM 的 WSOL Vault。
     *
     * <p>
     * SELL 时：
     *
     * WSOL Vault
     *   -> userWsol
     *
     * <p>
     * 随后 userWsol 会被 close，
     * WSOL 转回 native SOL。
     */
    private String usdcWsolWsolVault;


    /**
     * USDC/WSOL CLMM Observation State。
     *
     * <p>
     * SwapV2 必需账户。
     */
    private String usdcWsolObservationState;


    /**
     * USDC -> WSOL CLMM 的 remaining accounts。
     *
     * <p>
     * 一般包括：
     *
     * - TickArrayBitmapExtension
     * - TickArray
     * - 其它 swap 所需 TickArray
     *
     * <p>
     * 注意：
     *
     * 与 bonkUsdcRemainingAccounts 一样，
     * 这些账户可能随价格 / current tick 变化。
     *
     * 不建议永久写死。
     */
    private List<String> usdcWsolRemainingAccounts;
}