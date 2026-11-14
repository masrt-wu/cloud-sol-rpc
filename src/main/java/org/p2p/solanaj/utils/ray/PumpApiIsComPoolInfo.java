package org.p2p.solanaj.utils.ray;


import lombok.Data;

/**
 * 未打满的 数据
 */

@Data
public class PumpApiIsComPoolInfo {

    /**
     *  可能是固定的 6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P
     */
    private String programId;

    /**
     * 固定值 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf
     */
    private String globalConfig;

    /**
     *
     * 从这里解析过来 4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf
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

    private String mint;

    /**
     *  未打满的 虚拟地址
     */
    private String  bondingCurve;

    /**
     * 关系曲线地址
     */
    private String associatedBondingCurve;

    /**
     * 固定值 11111111111111111111111111111111
     */
    private String systemProgram;


    /**
     * TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
     */
    private String tokenProgram;


    /**
     * SysvarRent111111111111111111111111111111111
     */
    private String rentProgram;

    /**
     * 固定值
     * Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1
     */
    private String eventAuthority;
}
