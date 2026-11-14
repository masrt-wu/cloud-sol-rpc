package org.p2p.solanaj.utils.ray;


import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@Data
public class LiquidityPumpPoolInfo {


    // 1) 8字节 blob
    private byte[] bl8 = new byte[8];

    private byte poolBump;

    /**
     * U16 或者 u32
     */
    private int index;

    /**
     * 可能是创建地址
     */
    private PublicKey creator;

    /**
     * 6KHRmX8NypsWJmEhjr4nXPo4eaHMWmR9MNENh78Xpump
     */
    private PublicKey baseMint;

    /**
     * So11111111111111111111111111111111111111112
     */
    private PublicKey quoteMint;

    /**
     * CZjKCcgWRsybiQY669qo7SSKbiNQXhRgvpbTEGrDxkwW
     */
    private PublicKey lpMint;

    /**
     * D4ennWGtgLGNYhjq7NgTybJdG2rJDzAg3G3SG4eRzMQ7
     */
    private PublicKey poolBaseTokenAccount;


    /**
     * HBecPukcSTmhP7owaeXVeA92CiYovnD7hkHA821i3hT6
     */
    private PublicKey poolQuoteTokenAccount;

    private long lpSupply;

    // 辅助方法，用于从ByteBuffer读取一个32字节publicKey
    private static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes, 0, 32);
        return new PublicKey(pubKeyBytes);
    }

    public static LiquidityPumpPoolInfo decode(byte[] data){
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Solana 使用小端字节序
        LiquidityPumpPoolInfo poolInfo = new LiquidityPumpPoolInfo();
        //poolInfo.poolBump = buffer.get();
        buffer.get(poolInfo.getBl8(), 0, 8);
        poolInfo.poolBump = buffer.get();
        poolInfo.index =  Short.toUnsignedInt(buffer.getShort()); // u16 转换为无符号整数;
        poolInfo.creator = readPublicKey(buffer);
        poolInfo.baseMint = readPublicKey(buffer);
        poolInfo.quoteMint = readPublicKey(buffer);
        poolInfo.lpMint = readPublicKey(buffer);
        poolInfo.poolBaseTokenAccount = readPublicKey(buffer);
        poolInfo.poolQuoteTokenAccount = readPublicKey(buffer);
        poolInfo.lpSupply = buffer.getLong();
        return poolInfo;
    }

    public static void main(String[] args) {
        String hex  = "d6904cec5f8b31b4170000005468652057616e646572696e6720456c657068616e747301000000325000000068747470733a2f2f697066732e696f2f697066732f6261666b726569676566726b79733374627870776c6472756d6568706273326f346865653463746c3769376a7162616162786e3777786a637166656264e99954ea91fcba12ee448d2522cf2e72b6952b5161a6e488db910ffd1d9f0000";

        byte[] data  = HexUtil.decodeHex(hex);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Solana 使用小端字节序
        buffer.get(new byte[8],0,8);
        byte[] bytes = new byte[buffer.getInt()];
        buffer.get(bytes);
        String name = new String(bytes, StandardCharsets.UTF_8);

        System.out.println(name);


    }
}
