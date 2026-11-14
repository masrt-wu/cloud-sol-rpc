package org.p2p.solanaj.utils.ray;


import lombok.Data;

@Data
public class PumpApiIsComToken2022PoolInfo {

    /**
     * 固定值
     * 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf
     */
    private String global;

    /**
     * 暂时固定值 从 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf 解析
     *[
     *   "7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ",
     *   "7hTckgnGnLQR6sdH7YkqFTAA7VwTfYFaZ6EhEsU3saCX",
     *   "9rPYyANsfQZw3DnDmKE3YCQF5E8oD89UXoHn9JFEhJUz",
     *   "AVmoTthdrX6tKt4nDjco2D775W2YK3sDhxPcMmzUAmTY",
     *   "CebN5WGQ4jvEPvsVU4EoHEpgzq1VV7AbicfhtW4xC9iM",
     *   "FWsW1xNtWscwNmKv6wVsU1iTzRN6wmmk3MjxRP5tT7hz",
     *   "G5UZAVbAf46s7cKWoyKu8kYTip9DGTpbLZ2qa9Aq69dP"
     * ]
     */
    private String feeRecipient;


    /**
     * spl pump 地址
     */
    private String mint;

    /**
     * pump LP地址
     */
    private String bondingCurve;

    /**
     * LP 的 vault
     *
     */
    private String associatedBondingCurve;


    /**
     * 自己创建的 associate 地址
     */
    private String associateUser;

    /**
     * 需要签名的 地址
     */
    private String user;

    /**
     * 固定值
     * 11111111111111111111111111111111
     */
    private String systemProgram;

    /**
     * 固定值
     * TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
     */
    private String tokenProgram;


    /**
     * 需要生成的地址
     */
    private String creatorVault;

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

    /**
     * 固定值
     * Hq2wp8uJ9jCPsYgNHex8RtqdvMPfVGoYwjvF1ATiwn2Y
     */
    private String globalVolumeAccumulator;


    /**
     * PDA 生成地址
     */
    private String userVolumeAccumulator;

    /**
     * 固定地址
     * 8Wf5TiAheLUqBrKXeYg2JtAFFMWtKdG2BSFgqUcPVwTt
     */
    private String feeConfig;

    /**
     * pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ
     */
    private String feeProgram;

    /**
     *
     */
    private String sellAccount;


    /**
     * 最新生成规则
     */
    private String remainingAccount;


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
    private String buyFeeAccount;
}
