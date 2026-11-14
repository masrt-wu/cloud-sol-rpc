package org.p2p.solanaj.utils.ray;
import cn.hutool.core.codec.Base58;
import cn.hutool.core.codec.Base64;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


@Data
public class SplMintLayout {
    public int mintAuthorityOption;
    public PublicKey mintAuthority;
    public long supply;
    public byte decimals;
    public byte isInitialized;
    public int freezeAuthorityOption;
    public PublicKey freezeAuthority;

    public static SplMintLayout decode(byte[] data) {
        SplMintLayout layout = new SplMintLayout();
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        layout.mintAuthorityOption = bb.getInt(); // u32
        layout.mintAuthority = readPublicKey(bb); // publicKey (32 bytes)
        layout.supply = bb.getLong(); // u64
        layout.decimals = bb.get(); // u8
        layout.isInitialized = bb.get(); // u8
        layout.freezeAuthorityOption = bb.getInt(); // u32
        layout.freezeAuthority = readPublicKey(bb); // publicKey

        return layout;
    }

    private static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes);
        return new PublicKey(pubKeyBytes);
    }

    public static void main(String[] args) {
        System.out.println(Base58.encode(Base64.decode("uW2ugV3VETi21Gew7nVlaSIwoLZ2ljsiQQbGEY3JPb69B/G7Fqw8uhRX7J5s8QLdqJi5vsiphXlUVOsVOMfDAg==")));
    }
}

