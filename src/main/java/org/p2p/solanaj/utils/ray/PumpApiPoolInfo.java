package org.p2p.solanaj.utils.ray;


import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

@Data
public class PumpApiPoolInfo {

    /**
     *  可能是固定的 pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA
     */
    private String programId;

    /**
     * LP 地址
     */
    private String poolAddress;

    /**
     * token 地址
     */
    private String tokenAddress;


    /**
     * 固定值 ADyA8hdefvWN2dbGGWFotbzWxrAvLW83WG6QCVXvJKqw
     */
    private String globalConfig;

    /**
     * token 地址
     */
    private String baseMint;

    /**
     *  卖出 地址 So11111111111111111111111111111111111111112
     */
    private String QuoteMint;


    /**
     * 3jLPMoftyxjW8FpWc1huHtMVFGUUnx7e3cEpJt2BsUwBzHv66Nq1bQ1FGL3aMmBCKWYxMxxwQsUztuqdxaeeKPXW
     * 签名地址 实例
     * Aqz2fezzRaBzxNkzhTvrhtWaxjsK88hi3H2yCgsFeosw
     */
    private String  poolBaseTokenAccount;


    /**
     * Aqz2fezzRaBzxNkzhTvrhtWaxjsK88hi3H2yCgsFeosw
     */
    private String poolQuoteTokenAccount;


    /**
     *
     * 从这里解析过来 ADyA8hdefvWN2dbGGWFotbzWxrAvLW83WG6QCVXvJKqw
     * 62qc2CNXwrYqQScmEdiZFFAnJR262PxWEuNQtxfafNgV
     * 7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ
     * 7hTckgnGnLQR6sdH7YkqFTAA7VwTfYFaZ6EhEsU3saCX
     * 9rPYyANsfQZw3DnDmKE3YCQF5E8oD89UXoHn9JFEhJUz
     * AVmoTthdrX6tKt4nDjco2D775W2YK3sDhxPcMmzUAmTY
     * FWsW1xNtWscwNmKv6wVsU1iTzRN6wmmk3MjxRP5tT7hz
     * G5UZAVbAf46s7cKWoyKu8kYTip9DGTpbLZ2qa9Aq69dP
     * JCRGumoE9Qi5BBgULTgdgTLjSgkCMSbF62ZZfGs84JeU
     */
    private String protocolFeeRecipient;

    /**
     * 可能是随机生成的
     */
    private String  protocolFeeRecipientTokenAccount;

    /**
     * 固定值 TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     */
    private String  baseTokenProgram;

    /**
     * 固定值 TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     */
    private String  quoteTokenProgram;

    /**
     * 固定值 11111111111111111111111111111111
     */
    private String systemProgram;

    /**
     * 固定值 ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL
     */
    private String associatedTokenProgram;

    /**
     * 固定值
     * GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR
     */
    private String eventAuthority;

    /**
     * pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA
     * 可能是固定值
     */
    private String program;

}
