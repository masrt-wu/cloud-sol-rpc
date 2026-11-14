package org.p2p.solanaj.utils.ray;


import lombok.Data;
import org.p2p.solanaj.core.PublicKey;


/**
 * cPmm 购买 指令
 */
@Data
public class RayCPmmPoolInfo {

    /**
     * 购买钱包 地址
     */
    private String payer;


    /**
     * 固定地址  GpMZbSM2GgvTKHJirzeGfMFoaZ8UR2X7F4v8vHTvxFbL
     */
    private String authority;



    /**
     * 从 getAccountInfo 里面拿 配置
     */
    private String configId;

    /**
     * 池子Id
     */
    private String poolId;


    /**
     * wsol 创建地址
     */
    private String  tokenAccountIn;

    /**
     * SPL 用户创建地址
     */
    private String  tokenAccountOut;

    /**
     *poll A 地址
     */
    private String vaultA;

    /**
     * poll B 地址
     */
    private String vaultB;

    /**
     * mint 源地址 A
     */
    private String mintProgramA;

    /**
     * mint 源地址 B
     */
    private String  mintProgramB;

    /**
     * 交易地址 A
     */
    private String mintA;

    /**
     * 交易 地址 B
     */
    private String mintB;

    /**
     * 未知 地址
     */
    private String observationId;


    /**
     * CPmm 地址  CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C 写死
     */
    private String programId;

    private Long bump;

    private Integer status;

    private Integer lpDecimals;

    private Integer mintDecimalA;

    private Integer mintDecimalB;

    /**
     * lp 的 数量
     */
    private Long lpAmount;

    private Long protocolFeesMintA;

    private Long protocolFeesMintB;


    private Long fundFeesMintA;

    private Long fundFeesMintB;

    /**
     * 开放交易时间
     */
    private Long openTime;


    /**
     * pool 创建地址
     */
    private String poolCreator;

    /**
     * mint LP地址
     */
    private String mintLp;
}
