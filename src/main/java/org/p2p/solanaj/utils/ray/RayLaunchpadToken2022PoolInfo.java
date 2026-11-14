package org.p2p.solanaj.utils.ray;


import lombok.Data;

@Data
public class RayLaunchpadToken2022PoolInfo {

    /**
     * 0. payer / user
     */
    private String payer;

    /**
     * 1. LaunchLab authority
     * PDA: ["vault_auth_seed"]
     */
    private String authority;

    /**
     * 2. global config
     * PDA: ["global_config", quoteMint, curveType, configIndex]
     */
    private String globalConfig;

    /**
     * 3. platform config
     */
    private String platformConfig;

    /**
     * 4. pool state
     * PDA: ["pool", baseMint, quoteMint]
     */
    private String poolState;

    /**
     * 5. 用户 Base Token ATA
     * Token2022 ATA
     */
    private String userBaseToken;

    /**
     * 6. 用户 Quote Token ATA
     * 普通 SPL Token ATA
     */
    private String userQuoteToken;

    /**
     * 7. base vault
     * PDA: ["pool_vault", poolState, baseMint]
     */
    private String baseVault;

    /**
     * 8. quote vault
     * PDA: ["pool_vault", poolState, quoteMint]
     */
    private String quoteVault;

    /**
     * 9. base mint
     * Token2022
     */
    private String baseMint;

    /**
     * 10. quote mint
     */
    private String quoteMint;

    /**
     * 11. base token program
     * Token2022:
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     */
    private String baseTokenProgram;

    /**
     * 12. quote token program
     * Tokenkeg:
     * TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     */
    private String quoteTokenProgram;

    /**
     * 13. event authority
     * PDA: ["__event_authority"]
     */
    private String eventAuthority;

    /**
     * 14. Raydium LaunchLab Program
     */
    private String program;

    /**
     * 15. System Program
     */
    private String systemProgram;

    /**
     * 16. platform fee vault
     * PDA: [platformConfig, quoteMint]
     */
    private String platformFeeVault;

    /**
     * 17. creator fee vault
     * PDA: [creator, quoteMint]
     */
    private String creatorFeeVault;

    /**
     * 可选：
     * 如果 BuyExactIn 带 shareFeeReceiver，
     * 它会插入到 systemProgram 前面
     */
    private String shareFeeReceiver;


}
