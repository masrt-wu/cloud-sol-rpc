package org.p2p.solanaj.utils;

import org.bitcoinj.core.Base58;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Arrays;

public class SPLAccountParser {

    private static final int PUBLIC_KEY_LENGTH = 32;
    private static final int ACCOUNT_LENGTH = 165;

    public static void main(String[] args) {
        // 示例 Base64 数据 (替换为实际返回值)
        System.out.println(cn.hutool.core.codec.Base64.encode(cn.hutool.core.codec.Base58.decode("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")));
        System.out.println(Base58.encode(Base64.getDecoder().decode("BqfVFxh19ynHPZNAjyFhIAZ+2Ix24Iwof8GUYAAAAAA=")));
        String base64Data = "BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAG8z6z8bYcmdMuHbEMvuKwjIKrWyTIXme015lr+tb0pir/bQL4EAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEAAADwHR8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        byte[] decodedData = Base64.getDecoder().decode(base64Data);

        if (decodedData.length != ACCOUNT_LENGTH) {
            throw new IllegalArgumentException("Invalid SPL Account data length.");
        }

        SPLAccount account = parseAccount(decodedData);
        System.out.println(account);

        base64Data = "BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAEDhtPvfKsiwJgRN49HkN2DBi850TcsPPR5txlnsyCeLqK/3D0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEAAADwHR8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        decodedData = Base64.getDecoder().decode(base64Data);

        if (decodedData.length != ACCOUNT_LENGTH) {
            throw new IllegalArgumentException("Invalid SPL Account data length.");
        }

        account = parseAccount(decodedData);
        System.out.println(account);

        base64Data = "NIbon9VO3mzfKFin3iEMgw1V/5pua2LuLgFPi3kL3K+8z6z8bYcmdMuHbEMvuKwjIKrWyTIXme015lr+tb0pirX0xyrRSwMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        decodedData = Base64.getDecoder().decode(base64Data);

        if (decodedData.length != ACCOUNT_LENGTH) {
            throw new IllegalArgumentException("Invalid SPL Account data length.");
        }

        account = parseAccount(decodedData);
        System.out.println(account);

    }

    public static SPLAccount parseAccount(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);  // Solana 使用小端序

        SPLAccount account = new SPLAccount();

        // 解析 mint
        account.mint = readPublicKey(buffer);
        // 解析 owner
        account.owner = readPublicKey(buffer);
        // 解析 amount
        account.amount = buffer.getLong();
        // 解析 delegateOption
        account.delegateOption = buffer.getInt();
        // 解析 delegate
        account.delegate = readPublicKey(buffer);
        // 解析 state
        account.state = buffer.get();
        // 解析 isNativeOption
        account.isNativeOption = buffer.getInt();
        // 解析 isNative
        account.isNative = buffer.getLong();
        // 解析 delegatedAmount
        account.delegatedAmount = buffer.getLong();
        // 解析 closeAuthorityOption
        account.closeAuthorityOption = buffer.getInt();
        // 解析 closeAuthority
        account.closeAuthority = readPublicKey(buffer);

        return account;
    }

    // 读取32字节的公钥
    private static String readPublicKey(ByteBuffer buffer) {
        byte[] publicKeyBytes = new byte[PUBLIC_KEY_LENGTH];
        buffer.get(publicKeyBytes);
        //return bytesToHex(publicKeyBytes);
        return Base58.encode(publicKeyBytes);
    }

    // 将字节数组转换为16进制字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // SPL 账户类，保存解析结果
    public static class SPLAccount {
        public String mint;
        public String owner;
        public long amount;
        public int delegateOption;
        public String delegate;
        public byte state;
        public int isNativeOption;
        public long isNative;
        public long delegatedAmount;
        public int closeAuthorityOption;
        public String closeAuthority;

        @Override
        public String toString() {
            return "SPLAccount{" +
                    "mint='" + mint + '\'' +
                    ", owner='" + owner + '\'' +
                    ", amount=" + amount +
                    ", delegateOption=" + delegateOption +
                    ", delegate='" + delegate + '\'' +
                    ", state=" + state +
                    ", isNativeOption=" + isNativeOption +
                    ", isNative=" + isNative +
                    ", delegatedAmount=" + delegatedAmount +
                    ", closeAuthorityOption=" + closeAuthorityOption +
                    ", closeAuthority='" + closeAuthority + '\'' +
                    '}';
        }
    }
}

