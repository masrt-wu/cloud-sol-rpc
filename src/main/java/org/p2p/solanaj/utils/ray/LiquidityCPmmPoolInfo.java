package org.p2p.solanaj.utils.ray;


import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
public class LiquidityCPmmPoolInfo {
    // 1) 8字节 blob
    private byte[] blob8 = new byte[8];

    // 2) 公钥字段
        private PublicKey configId;
    private PublicKey poolCreator;
    private PublicKey vaultA;
    private PublicKey vaultB;
    private PublicKey mintLp;
    private PublicKey mintA;
    private PublicKey mintB;
    private PublicKey mintProgramA;
    private PublicKey mintProgramB;
    private PublicKey observationId;

    // 3) u8字段
    private Long bump;
    private byte status;
    private byte lpDecimals;
    private byte mintDecimalA;
    private byte mintDecimalB;

    // 4) u64字段
    private long lpAmount;
    private long protocolFeesMintA;
    private long protocolFeesMintB;
    private long fundFeesMintA;
    private long fundFeesMintB;
    private long openTime;

    // 5) seq(u64(), 32) => 32个long
    private long[] seq64 = new long[32];

    public static LiquidityCPmmPoolInfo decode(byte[] data) {
        // 1) 先检查长度是否足够
        //    你可以根据实际布局计算所需最小长度
        //    blob(8) + 10个publicKey(10*32=320) + 5个u8(5) + 6个u64(6*8=48) + 32个u64(32*8=256)
        //    总长度 = 8 + 320 + 5 + 48 + 256 = 637
        if (data.length < 637) {
            throw new IllegalArgumentException("data length too short, expect at least 637 bytes");
        }

        LiquidityCPmmPoolInfo info = new LiquidityCPmmPoolInfo();

        ByteBuffer bb = ByteBuffer .wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // 2) 读取 blob(8)
        bb.get(info.getBlob8(), 0, 8);

        // 3) 读取 10个 publicKey(32字节 each)
        info.setConfigId(readPublicKey(bb));
        info.setPoolCreator(readPublicKey(bb));
        info.setVaultA(readPublicKey(bb));
        info.setVaultB(readPublicKey(bb));
        info.setMintLp(readPublicKey(bb));
        info.setMintA(readPublicKey(bb));
        info.setMintB(readPublicKey(bb));
        info.setMintProgramA(readPublicKey(bb));
        info.setMintProgramB(readPublicKey(bb));
        info.setObservationId(readPublicKey(bb));

        // 4) 读取 u8
        //info.setBump(bb.get());
        Long bump = Byte.toUnsignedLong(bb.get());
        info.setBump(bump);

        info.setStatus(bb.get());
        info.setLpDecimals(bb.get());
        info.setMintDecimalA(bb.get());
        info.setMintDecimalB(bb.get());

        // 5) 读取 u64
        info.setLpAmount(bb.getLong());           // le
        info.setProtocolFeesMintA(bb.getLong());
        info.setProtocolFeesMintB(bb.getLong());
        info.setFundFeesMintA(bb.getLong());
        info.setFundFeesMintB(bb.getLong());
        info.setOpenTime(bb.getLong());

        // 6) 读取 seq(u64(), 32)
        long[] seq64 = new long[32];
        for (int i = 0; i < 32; i++) {
            seq64[i] = bb.getLong();
        }
        info.setSeq64(seq64);

        return info;
    }

    // 辅助方法，用于从ByteBuffer读取一个32字节publicKey
    private static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes, 0, 32);
        return new PublicKey(pubKeyBytes);
    }

    public static void main(String[] args) {
        String url = Cluster.QUINET.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        String base64 = "9+3j9dfD3kazIT+6i/nIf6keR4GWKMOD4AvqfpjHoD4DuhBpz8P28yMy5Nona5hD/kSAaEvQ++w8KeLUlb788cdcHxN4UZCv1MR04oIyeD7C8jOL8r42oe0iwqu0O36nr5BDLyzrNkgC76yl0Ga/ue5vwz/KgqeykzfLQ1PjY0+2fIT5GPQGjT9fNYHfM9vPDRlFAKS3kmtrnnRe0+8xeNCIZaefBys6BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAHxXkcbKsz617fcfYUOeweMi+ps0ZnoMywb7I3l+8i/Sgbd9uHXZaGT2cvhRs7reawctIXtX1s3kTqM9YV+/wCpBt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKlA5z5PjjJwPbi4HL8ETDHIyBBmGn6Okpm+mx4M1Ktxqv0ACQkJ+zQZQUhWAADXRdAAAAAAAHMWOgAlPAEAhmxFAAAAAABbB76qYWkAAD7HemcAAAAA0gIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
        base64 = "9+3j9dfD3kazIT+6i/nIf6keR4GWKMOD4AvqfpjHoD4DuhBpz8P28weDqBUmIQ+UoOjoTSCHADs6QmDSnOedrP4hpOp3H6Yo6kvZ9B72leGUr9TadFy0AW3fRpeC3f1BVgVr3uetAXSN6weS7p2udHjD1aQDUeyVJhBBBbuzv7OuRQEYK6RMGTm5W6Ag1bKlRHBoGmgRRCDVDey4J6yEgs+EIcf/PNd5BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAGwEq/EdXshuRf6jTbvWKNQD6HqOkpv9aGxKfg2Wt6ldQbd9uHXZaGT2cvhRs7reawctIXtX1s3kTqM9YV+/wCpBt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKnryU3pxxuf8vmkueejinwCMNea8NiQlgQcS+yGVqCi1v0ACQkGchVrUM0DAADlVgAAAAAAAKxWtBQCAAAATZPvAQAAAACf7BRqOQAAAIeVbGgAAAAALgMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

        System.out.println(decode(Base64.decode(base64)));

        base64 = "9+3j9dfD3kapsh8XfpuXbo7hKfQa7zeEqsUUlJCVsALmEX1Z0DaWjz5PhCp9CwyeyOJOoDoxyfh5o13DBHWJGqruXZ6wCC/5AsgBtNcFBUb4JzQuBdFIJOrRRJrEfdFCr2ZoFNMrbHWLoPWD7zG4/oMvTQvKLGanq83W0mZUnqZp8M5ofkm4iYF+etof98/JYzqXADfwzOPgXzL13r6p0FU0niGw8kzuOVFxZS7+FnpBopPK7lyjeahZTjHBCsAci42UDgQ/8Kz0lk92ADDo1Nu9TBGXoWllQxnQgDrVh4MPqx/U1cfu6Qbd9uHudY/eGEJdvORszdq2GvxNg7kNJ/69+SjYoYv8Bt324e51j94YQl285GzN2rYa/E2DuQ0n/r35KNihi/wihoG4pjBIR1X7vDw+JAGKFJ5DCvyaOj0IE8NiwTSreP0ACQYG8ZiwE8EOAAAQgJ7GAAAAAEzsngkAAAAAjDxlCwEAAACcxlUPAAAAAP7yq2oAAAAAFQQAAAAAAAACAQAAAAAAAAAAAAAAAAAA0WePIgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
        System.out.println(decode(Base64.decode(base64)));
    }

}
