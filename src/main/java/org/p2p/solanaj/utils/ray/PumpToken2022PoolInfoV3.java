package org.p2p.solanaj.utils.ray;


import lombok.Data;

/**
 * V3版本 buy or sell
 */
@Data
public class PumpToken2022PoolInfoV3 {

    /**
     * 固定值
     * 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf
     */
    private String global;

    /**
     * mint 地址
     *
     */
    private String baseMint;

    /**
     * 交易对地址
     */
    private String quoteMint;

    /**
     * mint token归属 可能是token2022
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     *
     */
    private String baseTokenProgram;

    /**
     * quote 归属 TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     */
    private String quoteTokenProgram;

    /**
     * LP 地址
     */
    private String bondingCurve;

    /**
     *        // 3. Bonding Curve 的 Token-2022 ATA
     *         String associatedBaseBondingCurve = findAta(
     *                 bondingCurve.getAddress(),
     *                 mintKey,
     *                 TOKEN_2022
     *         );
     *         生成规则
     */
    private String associatedBaseBondingCurve;

    /**
     *         // SOL 交易对不使用，但 V3 仍要求传入地址
     *         String associatedQuoteBondingCurve = findAta(
     *                 bondingCurve.getAddress(),
     *                 WSOL_MINT,
     *                 TOKEN_PROGRAM
     *         );
     *         生成规则
     */
    private String associatedQuoteBondingCurve;

    /**
     * 签名地址
     */
    private String user;

    /**
     * 用户 Token-2022 账户
     * 需要自己 create
     */
    private String associatedBaseUser;


    /**
     *         // SOL 交易对不需要账户真实存在
     *         String associatedQuoteUser = findAta(
     *                 userKey,
     *                 WSOL_MINT,
     *                 TOKEN_PROGRAM
     *         );
     */
    private String associatedQuoteUser;


    /**
     *         // 7. User Volume Accumulator PDA
     *         PublicKey.ProgramDerivedAddress userVolumeAccumulator =
     *                 PublicKey.findProgramAddress(
     *                         Arrays.asList(
     *                                 bytes("user_volume_accumulator"),
     *                                 userKey.toByteArray()
     *                         ),
     *                         PROGRAM_PUMP
     *                 );
     */
    private String userVolumeAccumulator;


    /**
     * 固定地址
     * 8Wf5TiAheLUqBrKXeYg2JtAFFMWtKdG2BSFgqUcPVwTt
     */
    private String feeConfig;

    /**
     * 从这里解析 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf
     * [
     *   "5YxQFdt3Tr9zJLvkFccqXVUwhdTWJQc1fFg2YPbxvxeD",
     *   "9M4giFFMxmFGXtc3feFzRai56WbBqehoSeRE5GK7gf7",
     *   "GXPFM2caqTtQYC2cJ5yJRi9VDkpsYZXzYdwYpGnLmtDL",
     *   "3BpXnfJaUTiwXnJNe7Ej1rcbzqTTQUvLShZaWazebsVR",
     *   "5cjcW9wExnJJiqgLjq7DEG75Pm6JBgE1hNv4B2vHXUW6",
     *   "EHAAiTxcdDwQ3U4bU6YcMsQGaekdzLS3B5SmYo46kJtL",
     *   "5eHhjP8JaYkz83CWwvGU2uMUXefd3AazWGx4gpcuEEYD",
     *   "A7hAgCzFw14fejgCp387JUJRMNyz4j89JKnhtKU8piqW"
     * ]
     *
     * buy 买入规则
     */
    private String buybackFeeRecipient;

    /**
     * 固定值
     * 11111111111111111111111111111111
     */
    private String systemProgram;

    /**
     * 固定值
     * Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1
     */
    private String eventAuthority;

    /**
     * 固定值
     * 6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P
     *
     */
    private String program;

}
