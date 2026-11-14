package org.p2p.solanaj.utils.stonk;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class StonkTokenDecoder {

    // InitializeWithToken2022 discriminator
    public static final byte[] DISCRIMINATOR = {(byte) 0x25, (byte) 0xbe, (byte) 0x7e, (byte) 0xde, (byte) 0x2c, (byte) 0x9a, (byte) 0xab, (byte) 0x11};

    public record MintParams(int decimals, String name, String symbol, String uri) {
    }

    public record ConstantCurve(BigInteger supplyRaw, BigInteger totalBaseSellRaw, BigInteger totalQuoteFundRaisingRaw,
                                int migrateType) {
    }

    public record VestingParams(BigInteger totalLockedAmount, BigInteger cliffPeriod, BigInteger unlockPeriod) {
    }

    public record TransferFeeParams(int basisPoints, BigInteger maximumFeeRaw) {
    }

    public record InitializeWithToken2022(MintParams mint, int curveType, ConstantCurve curve, VestingParams vesting,
                                          int ammFeeOn, TransferFeeParams transferFee, int decodedLength) {
    }

    public static InitializeWithToken2022 decode(byte[] data) {
        Reader r = new Reader(data);

        // 1. discriminator
        byte[] discriminator = r.readBytes(8);

        if (!Arrays.equals(discriminator, DISCRIMINATOR)) return null;

        // 2. base_mint_param
        int decimals = r.readU8();

        String name = r.readBorshString();
        String symbol = r.readBorshString();
        String uri = r.readBorshString();

        MintParams mint = new MintParams(decimals, name, symbol, uri);

        // 3. CurveParams enum
        int curveType = r.readU8();

        // 当前这个样本：
        // 0 = Constant
        if (curveType != 0) {
            throw new IllegalArgumentException("当前解析器只支持 Constant Curve，curveType=" + curveType);
        }

        BigInteger supplyRaw = r.readU64();

        BigInteger totalBaseSellRaw = r.readU64();

        BigInteger totalQuoteFundRaisingRaw = r.readU64();

        int migrateType = r.readU8();

        ConstantCurve curve = new ConstantCurve(supplyRaw, totalBaseSellRaw, totalQuoteFundRaisingRaw, migrateType);

        // 4. VestingParam
        BigInteger totalLockedAmount = r.readU64();

        BigInteger cliffPeriod = r.readU64();

        BigInteger unlockPeriod = r.readU64();

        VestingParams vesting = new VestingParams(totalLockedAmount, cliffPeriod, unlockPeriod);

        // 5. amm_fee_on enum
        int ammFeeOn = r.readU8();

        // 6. Option<TransferFeeExtensionParam>
        int transferFeeOption = r.readU8();

        TransferFeeParams transferFee = null;

        if (transferFeeOption == 1) {

            int basisPoints = r.readU16();

            BigInteger maximumFeeRaw = r.readU64();

            transferFee = new TransferFeeParams(basisPoints, maximumFeeRaw);

        } else if (transferFeeOption != 0) {

            throw new IllegalArgumentException("非法 Option 标志: " + transferFeeOption);
        }

        if (r.remaining() != 0) {
            throw new IllegalArgumentException("解析结束后还有 " + r.remaining() + " bytes 未消费");
        }

        return new InitializeWithToken2022(mint, curveType, curve, vesting, ammFeeOn, transferFee, r.position());
    }

    /**
     * 直接解析 Base58 instruction data。
     * <p>
     * 这里自带纯 Java Base58 decoder。
     * 热路径建议替换成你项目里现成的高性能 Base58 实现。
     */
    public static InitializeWithToken2022 decodeBase58(String base58) {
        return decode(Base58.decode(base58));
    }

    /**
     * raw token amount -> UI amount
     * <p>
     * 为避免精度丢失，返回字符串。
     */
    public static String toUiAmount(BigInteger raw, int decimals) {
        if (decimals == 0) {
            return raw.toString();
        }

        String s = raw.toString();

        if (s.length() <= decimals) {
            return "0." + "0".repeat(decimals - s.length()) + s;
        }

        int pos = s.length() - decimals;

        return s.substring(0, pos) + "." + s.substring(pos);
    }

    private static String migrateTypeName(int value) {
        return switch (value) {
            case 0 -> "AMM";
            case 1 -> "CP-SWAP";
            default -> "UNKNOWN(" + value + ")";
        };
    }

    private static String ammFeeOnName(int value) {
        return switch (value) {
            case 0 -> "QUOTE_TOKEN";
            default -> "UNKNOWN(" + value + ")";
        };
    }

    public static void print(InitializeWithToken2022 x, int quoteDecimals) {
        int decimals = x.mint().decimals();

        System.out.println("InitializeWithToken2022 {");

        System.out.println("  decimals = " + decimals);

        System.out.println("  name = " + x.mint().name());

        System.out.println("  symbol = " + x.mint().symbol());

        System.out.println("  uri = " + x.mint().uri());

        System.out.println("  curveType = " + x.curveType() + " (CONSTANT)");

        System.out.println("  supplyRaw = " + x.curve().supplyRaw());

        System.out.println("  supply = " + toUiAmount(x.curve().supplyRaw(), decimals));

        System.out.println("  totalBaseSellRaw = " + x.curve().totalBaseSellRaw());

        System.out.println("  totalBaseSell = " + toUiAmount(x.curve().totalBaseSellRaw(), decimals));

        System.out.println("  totalQuoteFundRaisingRaw = " + x.curve().totalQuoteFundRaisingRaw());

        System.out.println("  totalQuoteFundRaising = " + toUiAmount(x.curve().totalQuoteFundRaisingRaw(), quoteDecimals));

        System.out.println("  migrateType = " + x.curve().migrateType() + " (" + migrateTypeName(x.curve().migrateType()) + ")");

        System.out.println("  totalLockedAmount = " + x.vesting().totalLockedAmount());

        System.out.println("  cliffPeriod = " + x.vesting().cliffPeriod());

        System.out.println("  unlockPeriod = " + x.vesting().unlockPeriod());

        System.out.println("  ammFeeOn = " + x.ammFeeOn() + " (" + ammFeeOnName(x.ammFeeOn()) + ")");

        if (x.transferFee() != null) {

            System.out.println("  transferFeeBasisPoints = " + x.transferFee().basisPoints());

            System.out.println("  transferFeePercent = " + (x.transferFee().basisPoints() / 100.0) + "%");

            System.out.println("  maximumFeeRaw = " + x.transferFee().maximumFeeRaw());

            System.out.println("  maximumFee = " + toUiAmount(x.transferFee().maximumFeeRaw(), decimals));

        } else {

            System.out.println("  transferFee = NONE");
        }

        System.out.println("  decodedLength = " + x.decodedLength());

        System.out.println("}");
    }

    // =========================================================
    // Borsh Reader
    // =========================================================

    private static final class Reader {

        private final byte[] data;
        private int offset;

        private Reader(byte[] data) {
            this.data = data;
        }

        int position() {
            return offset;
        }

        int remaining() {
            return data.length - offset;
        }

        void require(int size) {
            if (offset + size > data.length) {
                throw new IllegalArgumentException("数据越界: offset=" + offset + ", need=" + size + ", length=" + data.length);
            }
        }

        byte[] readBytes(int size) {
            require(size);

            byte[] result = Arrays.copyOfRange(data, offset, offset + size);

            offset += size;

            return result;
        }

        int readU8() {
            require(1);

            return data[offset++] & 0xff;
        }

        int readU16() {
            require(2);

            int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);

            offset += 2;

            return value;
        }

        long readU32() {
            require(4);

            long value = (data[offset] & 0xffL) | ((data[offset + 1] & 0xffL) << 8) | ((data[offset + 2] & 0xffL) << 16) | ((data[offset + 3] & 0xffL) << 24);

            offset += 4;

            return value;
        }

        BigInteger readU64() {
            require(8);

            byte[] littleEndian = Arrays.copyOfRange(data, offset, offset + 8);

            offset += 8;

            // BigInteger 接收大端，所以反转
            reverse(littleEndian);

            return new BigInteger(1, littleEndian);
        }

        String readBorshString() {

            long lengthLong = readU32();

            if (lengthLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("String 长度异常: " + lengthLong);
            }

            int length = (int) lengthLong;

            byte[] bytes = readBytes(length);

            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void reverse(byte[] bytes) {
        for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
            byte tmp = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = tmp;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }

    // =========================================================
    // Dependency-free Base58 decoder
    // =========================================================

    private static final class Base58 {

        private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

        private static final BigInteger BASE = BigInteger.valueOf(58);

        static byte[] decode(String input) {

            if (input == null || input.isEmpty()) {
                return new byte[0];
            }

            BigInteger value = BigInteger.ZERO;

            for (int i = 0; i < input.length(); i++) {

                char c = input.charAt(i);

                int digit = ALPHABET.indexOf(c);

                if (digit < 0) {
                    throw new IllegalArgumentException("非法 Base58 字符: " + c);
                }

                value = value.multiply(BASE).add(BigInteger.valueOf(digit));
            }

            byte[] encoded = value.toByteArray();

            // BigInteger 可能多一个 sign byte 00
            if (encoded.length > 1 && encoded[0] == 0) {

                encoded = Arrays.copyOfRange(encoded, 1, encoded.length);
            }

            int leadingZeros = 0;

            while (leadingZeros < input.length() && input.charAt(leadingZeros) == '1') {
                leadingZeros++;
            }

            byte[] result = new byte[leadingZeros + encoded.length];

            System.arraycopy(encoded, 0, result, leadingZeros, encoded.length);

            return result;
        }
    }

    public static void main(String[] args) {

        String data = "3XGTjP3hQyDFU8Y6q5r1cqSaN4q4uKQK3m9rZt6fQ1Q5UvGCEQbuX2q3e2Bo6n4sX4QgVxwMKTVWYkMYC8VQgkCDFPkfJ1EBLBhcCPSRb8SJNUdbuxzkDZwWaS1HvoKURhkmfwfWf6x7TyfmQrAUsqUVyfncs8SbGq3CkrF7ouzT2RnKrQRPdhWornVYk2Chvnh5ioX6v45DDHSX6SSZmgpftqKPwbu828o";

        InitializeWithToken2022 result = decodeBase58(data);


        String mint = "H8FCFy7sKfcxFiu6hJL8DWC2f7D4RqbEcqy7eLnoMUmq";
        String quoteMint = "6GmAFSYs4gk3FDao5FzzySQpPZaWsa4rUJHacpMpUNgx";
        // 这个样本 quote mint decimals = 9
        // 生成market
        print(result, 9);
        PublicKey.ProgramDerivedAddress market = PublicKey.findProgramAddress(Arrays.asList("pool".getBytes(), new PublicKey(mint).toByteArray(), new PublicKey(quoteMint).toByteArray()), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj"));
        System.out.println(market.getAddress());


        //Base Vault
        var baseVault = PublicKey.findProgramAddress(Arrays.asList("pool_vault".getBytes(), market.getAddress().toByteArray(), new PublicKey(mint).toByteArray()), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj"));
        System.out.println(baseVault.getAddress());

        var quoteVault = PublicKey.findProgramAddress(Arrays.asList("pool_vault".getBytes(), market.getAddress().toByteArray(), new PublicKey(quoteMint).toByteArray()), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj"));
        System.out.println(quoteVault.getAddress());

        // authority 地址
        var authority = PublicKey.findProgramAddress(List.of("vault_auth_seed".getBytes()), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj"));
        System.out.println(authority.getAddress());


        PublicKey.findProgramAddress(List.of("vault_auth_seed".getBytes()), new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj"));

        PublicKey programId = new PublicKey(
                "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C"
        );

        byte[] indexBytes = ByteBuffer.allocate(2)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort((short) 11)
                .array();
        var amm  = PublicKey.findProgramAddress(
                List.of(
                        "amm_config".getBytes(StandardCharsets.UTF_8),
                        indexBytes
                ),
                programId
        ).getAddress();
        System.out.println(amm.toBase58());
        System.out.println(1 << 12);
        var a  = new BigDecimal("3999.99");
        var b  = new BigDecimal("9999.99");
        System.out.println(b.compareTo(a));
    }
}
