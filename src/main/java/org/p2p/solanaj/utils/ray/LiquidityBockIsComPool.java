package org.p2p.solanaj.utils.ray;


import cn.hutool.core.codec.Base58;
import cn.hutool.core.codec.Base64;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Data
public class LiquidityBockIsComPool {
    public long epoch;
    public int authBump;
    public int status;
    public int baseDecimals;
    public int quoteDecimals;
    public int migrateType;
    public long supply;
    public long totalBaseSell;
    public long virtualBase;
    public long virtualQuote;
    public long realBase;
    public long realQuote;
    public long totalQuoteFundRaising;
    public long quoteProtocolFee;
    public long platformFee;
    public long migrateFee;
    public VestingSchedule vestingSchedule;
    public String globalConfig;
    public String platformConfig;
    public String baseMint;
    public String quoteMint;
    public String baseVault;
    public String quoteVault;
    public String creator;
    public List<Long> padding;

    // 辅助方法，用于从ByteBuffer读取一个32字节publicKey
    private static String  readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes, 0, 32);
        return new PublicKey(pubKeyBytes).toBase58();
    }



    public static LiquidityBockIsComPool decode(String base64Data) {
        byte[] raw = Base64.decode(base64Data);
        if (raw.length < 429) {
            throw new IllegalArgumentException("data length too short, expect at least 429 bytes");
        }
        VestingSchedule vestingSchedule = new VestingSchedule();
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Solana 使用小端字节序
        // 2) 读取 blob(8)
        buffer.get(new byte[8], 0, 8);
        LiquidityBockIsComPool pool = new LiquidityBockIsComPool();
        pool.epoch = buffer.getLong();
        pool.authBump = Byte.toUnsignedInt(buffer.get());
        pool.status = Byte.toUnsignedInt(buffer.get());
        pool.baseDecimals = Byte.toUnsignedInt(buffer.get());
        pool.quoteDecimals =Byte.toUnsignedInt(buffer.get());
        pool.migrateType = Byte.toUnsignedInt(buffer.get());
        pool.supply = buffer.getLong();
        pool.totalBaseSell =  buffer.getLong();
        pool.virtualBase = buffer.getLong();
        pool.virtualQuote =  buffer.getLong();
        pool.realBase =  buffer.getLong();
        pool.realQuote =  buffer.getLong();
        pool.totalQuoteFundRaising =  buffer.getLong();
        pool.quoteProtocolFee =  buffer.getLong();
        pool.platformFee =  buffer.getLong();
        pool.migrateFee =  buffer.getLong();

        vestingSchedule.totalLockedAmount = buffer.getLong();
        vestingSchedule.cliffPeriod =  buffer.getLong();
        vestingSchedule.unlockPeriod = buffer.getLong();
        vestingSchedule.startTime =  buffer.getLong();
        vestingSchedule.allocatedShareAmount =  buffer.getLong();

        pool.globalConfig = readPublicKey(buffer);
        pool.platformConfig = readPublicKey(buffer);
        pool.baseMint = readPublicKey(buffer);
        pool.quoteMint = readPublicKey(buffer);
        pool.baseVault = readPublicKey(buffer);
        pool.quoteVault = readPublicKey(buffer);
        pool.creator = readPublicKey(buffer);

        List<Long> padding = new ArrayList<>();
//        for (int i = 0; i < 8; i++) {
//            padding.add(buffer.getLong());
//        }
        pool.vestingSchedule = vestingSchedule;
        pool.padding = padding;
        return pool;
    }

    public static void main(String[] args) {
        String base64 = "9+3j9dfD3kYvAwAAAAAAAPoABgkBAIDGpH6NAwAAeMX7UdECAN50Dj7pzwMA168w/AYAAAAAAAAAAAAAABwAAAAAAAAAABJlyhMAAADJf58JAAAAAIT+fSYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVxqOAcjfeCD51ms8c2W40eSvqBt4VMwu91zvWL0Ihn7Z43muccQHrOyvERB3Ee6vHb7tmq6FmYSy3N5CMGKU7hFQwMR0zFRm2iVMkr+mcfHtZWNzehmrRyz/5ptPKp01BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAFL4YHEgGggooL7fImseP8N721MbWJWfnepW3ryY2LR26yVO+jlFWmaagiShYu04O3l4CeBUiLH04XK2szQDAXRPut9lw6Z16nhCm63vbdynz29YgCxRnGkECIPhMRvDPkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        LiquidityBockIsComPool pool = decode(base64);
        // 0.00000002762
        BigDecimal q = new BigDecimal(String.valueOf(pool.getVirtualQuote()));
        q = q.divide(BigDecimal.valueOf( Math.pow(10, pool.getQuoteDecimals())),15, RoundingMode.DOWN);

        BigDecimal b = new BigDecimal(String.valueOf(pool.getVirtualBase()));
        b = b.divide(BigDecimal.valueOf( Math.pow(10, pool.getBaseDecimals())),15, RoundingMode.DOWN);
        System.out.println(pool);

        //0.000000027959121
        System.out.println(q.divide(b,10, RoundingMode.DOWN).multiply(new BigDecimal("99")).toPlainString());
    }

}

class VestingSchedule {
    public long totalLockedAmount;
    public long cliffPeriod;
    public long unlockPeriod;
    public long startTime;
    public long allocatedShareAmount;
}

