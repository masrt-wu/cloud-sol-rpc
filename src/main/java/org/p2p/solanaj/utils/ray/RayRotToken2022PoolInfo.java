package org.p2p.solanaj.utils.ray;


import lombok.Data;

@Data
public class RayRotToken2022PoolInfo {
    /**
     * 当前交易用户 / payer
     *
     * 也就是：
     * - 买入时的买家
     * - 卖出时的卖家
     *
     * 一般是 signer + writable
     */
    private String payer;

    /**
     * LaunchLab 池子统一 Authority
     *
     * PDA 生成规则：
     * seeds = ["vault_auth_seed"]
     * program = LaunchLab Program
     *
     * 典型地址：
     * WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh
     */
    private String authority;

    /**
     * LaunchLab Global Config
     *
     * 建议直接从 Initialize / InitializeWithToken2022
     * 初始化交易的账户列表中获取。
     *
     * 不建议在抢新币热路径里额外 RPC 查询。
     */
    private String globalConfig;

    /**
     * LaunchLab Platform Config
     *
     * 代表当前代币所属的平台配置。
     *
     * 建议直接从初始化交易中获取。
     *
     * 注意：
     * 不同平台可能使用不同的 Platform Config，
     * 不要写死。
     */
    private String platformConfig;

    /**
     * LaunchLab Pool State
     *
     * 当前代币对应的 LaunchLab 池子状态账户。
     *
     * PDA 生成规则：
     * seeds = [
     *   "pool",
     *   baseMint,
     *   quoteMint
     * ]
     * program = LaunchLab Program
     */
    private String poolState;

    /**
     * 当前用户的 Base Token ATA
     *
     * Base Token 一般就是新发行的目标代币。
     *
     * ATA 生成规则：
     * seeds = [
     *   payer,
     *   baseTokenProgram,
     *   baseMint
     * ]
     * program = Associated Token Program
     */
    private String userBaseToken;

    /**
     * 当前用户的 Quote Token ATA
     *
     * Quote Token 可能是：
     * - BONK
     * - USDC
     * - 其他 SPL Token
     * - Token2022 Token
     *
     * ATA 生成规则：
     * seeds = [
     *   payer,
     *   quoteTokenProgram,
     *   quoteMint
     * ]
     * program = Associated Token Program
     */
    private String userQuoteToken;

    /**
     * Pool 的 Base Vault
     *
     * 保存池子中的 Base Token。
     *
     * PDA 生成规则：
     * seeds = [
     *   "pool_vault",
     *   poolState,
     *   baseMint
     * ]
     * program = LaunchLab Program
     */
    private String baseVault;

    /**
     * Pool 的 Quote Vault
     *
     * 保存池子中的 Quote Token。
     *
     * PDA 生成规则：
     * seeds = [
     *   "pool_vault",
     *   poolState,
     *   quoteMint
     * ]
     * program = LaunchLab Program
     */
    private String quoteVault;

    /**
     * Base Mint
     *
     * 一般就是 LaunchLab 新创建的目标代币 Mint。
     *
     * 注意：
     * Mint 本身通常不是 PDA，
     * 初始化时一般由客户端生成 Keypair。
     */
    private String baseMint;

    /**
     * Quote Mint
     *
     * 当前 LaunchLab Pool 使用的计价 Token。
     *
     * 例如：
     * BONK:
     * DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263
     *
     * 不一定是 SOL / WSOL。
     */
    private String quoteMint;

    /**
     * Base Token 所属 Token Program
     *
     * 常见：
     *
     * 普通 SPL Token:
     * TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     *
     * Token2022:
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     *
     * 这个字段非常重要，
     * 因为生成 Base ATA 时必须使用正确的 Token Program。
     */
    private String baseTokenProgram;

    /**
     * Quote Token 所属 Token Program
     *
     * 常见：
     *
     * 普通 SPL Token:
     * TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     *
     * Token2022:
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     *
     * 生成 Quote ATA 时必须使用这个 Program。
     */
    private String quoteTokenProgram;

    /**
     * LaunchLab Event Authority
     *
     * Anchor Event CPI 使用的 PDA。
     *
     * PDA 生成规则：
     * seeds = ["__event_authority"]
     * program = LaunchLab Program
     */
    private String eventAuthority;

    /**
     * LaunchLab Program ID
     *
     * 当前主网：
     * LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj
     */
    private String program;

    /**
     * Solana System Program
     *
     * 固定：
     * 11111111111111111111111111111111
     */
    private String systemProgram;

    /**
     * Platform Fee Vault
     *
     * 用于接收平台手续费。
     *
     * PDA 生成规则：
     * seeds = [
     *   platformConfig,
     *   quoteMint
     * ]
     * program = LaunchLab Program
     */
    private String platformFeeVault;

    /**
     * Creator Fee Vault
     *
     * 用于接收代币创建者相关手续费。
     *
     * PDA 生成规则：
     * seeds = [
     *   creator,
     *   quoteMint
     * ]
     * program = LaunchLab Program
     */
    private String creatorFeeVault;

    /**
     * 当前 LaunchLab Token / Pool 的创建者
     *
     * 初始化交易中可以直接获取。
     *
     * 主要用途：
     * 生成 creatorFeeVault。
     */
    private String creator;

    /**
     * Share Fee Receiver
     *
     * 可选字段。
     *
     * 某些 LaunchLab / Router 场景可能会使用
     * share fee receiver。
     *
     * 当前你给的这些 Buy / Sell 样本中
     * 没有作为固定必需账户使用。
     *
     * 不需要时可以为 null。
     */
    private String shareFeeReceiver;

}
