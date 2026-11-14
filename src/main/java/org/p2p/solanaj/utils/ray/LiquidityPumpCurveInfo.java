package org.p2p.solanaj.utils.ray;


import cn.hutool.core.codec.Base64;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
public class LiquidityPumpCurveInfo {
    private long discriminator;

    private long virtualTokenReserves;

    private long virtualSolReserves;

    private long realTokenReserves;

    private long realSolReserves;

    private long tokenTotalSupply;

    /**
     * true 完成 false 未完成
     */
    private boolean complete;


    private String creator;

    /**
     * 是否是 混沌模式
     */
    private boolean isMayhemMode;


    public static LiquidityPumpCurveInfo decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Solana 使用小端字节序
        LiquidityPumpCurveInfo account = new LiquidityPumpCurveInfo();

        account.discriminator = buffer.getLong();
        account.virtualTokenReserves = buffer.getLong();
        account.virtualSolReserves = buffer.getLong();
        account.realTokenReserves = buffer.getLong();
        account.realSolReserves = buffer.getLong();
        account.tokenTotalSupply = buffer.getLong();
        account.complete = buffer.get() != 0;  // 解析布尔值，0 为 false，非 0 为 true
        account.creator = readPublicKey(buffer).toBase58();
        account.isMayhemMode = buffer.get() != 0;  // 解析布尔值，0 为 false，非 0 为 true
        return account;
    }
    // 辅助方法，用于从ByteBuffer读取一个32字节publicKey
    private static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes, 0, 32);
        return new PublicKey(pubKeyBytes);
    }

    public static void main(String[] args) {

        LiquidityPumpCurveInfo info = decode(Base64.decode("F7f4N2DYrGCbzBsrvM4DADoNQf4GAAAAmzQJ3yrQAgA6YR0CAAAAAACAxqR+jQMAAC9Ii+kAoY/X6mPh0Rb8mMEhI2pK/VmJhSXpEuQIFY4rAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="));

        System.out.println(info);

        info = decode(Base64.decode("G3KpTd7rY3YTAAAAVGVjaG5vcHJlbmV1ciBBZ2VudAkAAABURUNIQUdFTlRDAAAAaHR0cHM6Ly9pcGZzLmlvL2lwZnMvUW1lZUxFWkhSeHBObzd0ZmdIRHdDOWE1ekhaQ0ttdXRHdDdINmE3Qjc5OHhVV83SNlg53ViGqouBU33y/o8ZGRgDMPcBXWSJT+LcytY/S3gG+5SIWAu/bDKYLbAg2YN/d1Gj0Ewhb52qLrmg0/XD7Bk1QE93tIbNSOs/sY5YkJVaat8ELtX+Xi2TXLefY8PsGTVAT3e0hs1I6z+xjliQlVpq3wQu1f5eLZNct59jsL22aQAAAAAAENhH488DAACsI/wGAAAAAHjF+1HRAgAAgMakfo0DAAbd9uHudY/eGEJdvORszdq2GvxNg7kNJ/69+SjYoYv8AAA="));

        System.out.println(info);

    }
}
