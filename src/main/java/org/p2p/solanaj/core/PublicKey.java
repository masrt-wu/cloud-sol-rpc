package org.p2p.solanaj.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Sha256Hash;
import org.p2p.solanaj.utils.ByteUtils;
import org.p2p.solanaj.utils.PublicKeySerializer;
import org.p2p.solanaj.utils.TweetNaclFast;

@JsonSerialize(using = PublicKeySerializer.class)
public class PublicKey {

    public static final int PUBLIC_KEY_LENGTH = 32;

    private final byte[] pubkey;

    public PublicKey(String pubkey) {
        if (pubkey.length() < PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid public key input: length must be at least " + PUBLIC_KEY_LENGTH);
        }
        this.pubkey = Base58.decode(pubkey);
    }

    public PublicKey(byte[] pubkey) {
        if (pubkey.length != PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid public key input: length must be exactly " + PUBLIC_KEY_LENGTH);
        }
        this.pubkey = Arrays.copyOf(pubkey, PUBLIC_KEY_LENGTH);
    }

    public static PublicKey readPubkey(byte[] bytes, int offset) {
        byte[] buf = ByteUtils.readBytes(bytes, offset, PUBLIC_KEY_LENGTH);
        return new PublicKey(buf);
    }

    public byte[] toByteArray() {
        return pubkey;
    }

    public String toBase58() {
        return Base58.encode(pubkey);
    }

    public boolean equals(PublicKey pubkey) {
        return Arrays.equals(this.pubkey, pubkey.toByteArray());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pubkey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PublicKey)) return false;
        PublicKey other = (PublicKey) o;
        return Arrays.equals(this.pubkey, other.pubkey);
    }

    @Override
    public String toString() {
        return toBase58();
    }

    public static PublicKey createProgramAddress(List<byte[]> seeds, PublicKey programId) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (byte[] seed : seeds) {
                if (seed.length > 32) {
                    throw new IllegalArgumentException("Max seed length exceeded: " + seed.length);
                }
                buffer.write(seed);
            }
            buffer.write(programId.toByteArray());
            buffer.write("ProgramDerivedAddress".getBytes());

            byte[] hash = Sha256Hash.hash(buffer.toByteArray());

            if (TweetNaclFast.is_on_curve(hash) != 0) {
                throw new IllegalStateException("Invalid seeds, address must fall off the curve");
            }

            return new PublicKey(hash);
        } catch (IOException e) {
            throw new RuntimeException("Error creating program address", e);
        }
    }

    public static class ProgramDerivedAddress {
        private PublicKey address;
        private int nonce;

        public ProgramDerivedAddress(PublicKey address, int nonce) {
            this.address = address;
            this.nonce = nonce;
        }

        public PublicKey getAddress() {
            return address;
        }

        public int getNonce() {
            return nonce;
        }

    }

    public static ProgramDerivedAddress findProgramAddress(List<byte[]> seeds, PublicKey programId) {
        for (int nonce = 255; nonce >= 0; nonce--) {
            try {
                List<byte[]> seedsWithNonce = new ArrayList<>(seeds);
                seedsWithNonce.add(new byte[] { (byte) nonce });
                PublicKey address = createProgramAddress(seedsWithNonce, programId);
                return new ProgramDerivedAddress(address, nonce);
            } catch (IllegalStateException e) {
                // Address was on the curve, try next nonce
            }
        }
        throw new IllegalStateException("Unable to find a viable program address nonce");
    }
    public static ProgramDerivedAddress getAssociatedAuthority(PublicKey programId){
        byte[] bytes = new byte[]{97, 109, 109, 32, 97, 117, 116, 104, 111, 114, 105, 116, 121};
        List<byte[]> seeds  = new ArrayList<>();
        seeds.add(bytes);

        return findProgramAddress(seeds,programId);
    }

    public static ProgramDerivedAddress getAssociatedAuthority(PublicKey programId, PublicKey marketId){
        byte[] marketIdBytes = marketId.toByteArray();
        int nonce = 0;
        while (nonce <100){
            try {
                // 构建 seedsWithNonce
                // seedsWithNonce = marketIdBytes + [nonce] + 7字节空填充
                // 最终是 32 + 1 + 7 = 40字节的数组作为种子
                byte[] nonceByte = new byte[] {(byte) nonce};
                byte[] padding = new byte[7]; // all zero by default

                // 合并数组
                //byte[] seedsWithNonce = concatByteArrays(marketIdBytes, nonceByte, padding);
                List<byte[]> list = new ArrayList<>();
                list.add(marketIdBytes);
                list.add(nonceByte);
                list.add(padding);
                // 调用 createProgramAddress
                PublicKey publicKey = PublicKey.createProgramAddress(list, programId);
                return new ProgramDerivedAddress(publicKey, nonce);
            }catch (Exception ignored){
                nonce++;
            }
        }
        // 如果100次尝试都失败，抛出异常
        throw new IllegalArgumentException("unable to find a viable program address nonce: " +
                "programId=" + programId.toString() + ", marketId=" + marketId.toString());
    }
    public static void main(String[] args) {

        ProgramDerivedAddress address = getAssociatedAuthority(new PublicKey("srmqPvymJeFKQ4zGQed1GFppgkRHL9kaELCbyksJtPX"),new PublicKey("ETESgT1pxMSavLLfBott6ArWtRVoTVd2o5KM6oYeUgm6"));

        //System.out.println(address.getAddress().toBase58());
    }

    public static PublicKey valueOf(String publicKey) {
        return new PublicKey(publicKey);
    }
    // 辅助方法：连接多个字节数组
    private static byte[] concatByteArrays(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] arr : arrays) {
            totalLength += arr.length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

}
