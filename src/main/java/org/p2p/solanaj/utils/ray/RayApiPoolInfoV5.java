package org.p2p.solanaj.utils.ray;


import lombok.Data;

@Data
public class RayApiPoolInfoV5 {

    /**
     * 可能 ray id
     */
    private String id;

    /**
     * 应该是 地址
     */
    private String baseMint;


    /**
     * 对于池子的 ray mint
     */
    private String quoteMint;

    /**
     * lp 的mint 地址
     */
    private String lpMint;


    private Long baseDecimals;

    private Long quoteDecimals;


    private Long lpDecimals;

    private int version  = 5 ;

    private String programId;

    private String authority;

    private String openOrders;

    private String targetOrders;

    private String baseVault;

    private String quoteVault;

    private String withdrawQueue;

    private String lpVault;


    private int marketVersion = 5 ;

    private String marketProgramId;

    private String marketId;

    private String marketAuthority;

    private String marketBaseVault;

    private String marketQuoteVault;

    private String marketBids;

    private String marketAsks;

    private String marketEventQueue;

    private String modelDataAccount;

    private String lookupTableAccount;


}
