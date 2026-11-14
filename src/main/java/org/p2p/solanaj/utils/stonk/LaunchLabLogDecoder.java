package org.p2p.solanaj.utils.stonk;

import cn.hutool.core.codec.Base58;
import com.alibaba.fastjson2.JSONObject;
import org.p2p.solanaj.utils.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public final class LaunchLabLogDecoder {

    private static final String PROGRAM_DATA_PREFIX = "Program data: ";

    // PoolCreateEvent
    private static final byte[] POOL_CREATE_EVENT = {(byte) 0x97, (byte) 0xd7, (byte) 0xe2, (byte) 0x09, (byte) 0x76, (byte) 0xa1, (byte) 0x73, (byte) 0xae};

    // TradeEvent
    private static final byte[] TRADE_EVENT = {(byte) 0xbd, (byte) 0xdb, (byte) 0x7f, (byte) 0xd3, (byte) 0x4e, (byte) 0xe6, (byte) 0x61, (byte) 0xee};

    // ============================================================
    // Event models
    // ============================================================

    public sealed interface LaunchLabEvent permits PoolCreateEvent, TradeEvent {
    }

    public record MintParam(int decimals, String name, String symbol, String uri) {
    }

    public record ConstantCurve(BigInteger supplyRaw, BigInteger totalBaseSellRaw, BigInteger totalQuoteFundRaisingRaw,
                                int migrateType) {
    }

    public record Vesting(BigInteger totalLockedAmount, BigInteger cliffPeriod, BigInteger unlockPeriod) {
    }

    public record PoolCreateEvent(String poolState, String creator, String config,

                                  MintParam mintParam,

                                  int curveType, ConstantCurve curve,

                                  Vesting vesting, int ammFeeOn) implements LaunchLabEvent {
    }

    public record TradeEvent(String poolState,

                             BigInteger totalBaseSell,

                             BigInteger virtualBase, BigInteger virtualQuote,

                             BigInteger realBaseBefore, BigInteger realQuoteBefore,

                             BigInteger realBaseAfter, BigInteger realQuoteAfter,

                             BigInteger amountIn, BigInteger amountOut,

                             BigInteger protocolFee, BigInteger platformFee, BigInteger creatorFee, BigInteger shareFee,

                             int tradeDirection, int poolStatus, boolean exactIn) implements LaunchLabEvent {
    }

    // ============================================================
    // 入口：解析单条 Program data log
    // ============================================================

    public static LaunchLabEvent decodeLog(String log) {

        if (log == null || !log.startsWith(PROGRAM_DATA_PREFIX)) {
            return null;
        }

        String base64 = log.substring(PROGRAM_DATA_PREFIX.length());

        final byte[] data;

        try {
            data = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (startsWith(data, POOL_CREATE_EVENT)) {
            return decodePoolCreateEvent(data);
        }

        if (startsWith(data, TRADE_EVENT)) {
            return decodeTradeEvent(data);
        }

        // 不是我们当前认识的 LaunchLab event
        return null;
    }

    // ============================================================
    // PoolCreateEvent
    // ============================================================

    public static PoolCreateEvent decodePoolCreateEvent(byte[] data) {
        Reader r = new Reader(data);

        byte[] discriminator = r.readBytes(8);

        if (!Arrays.equals(discriminator, POOL_CREATE_EVENT)) return null;

        // 3 x Pubkey
        String poolState = Base58.encode(r.readBytes(32));

        String creator = Base58.encode(r.readBytes(32));

        String config = Base58.encode(r.readBytes(32));

        // BaseMintParam
        int decimals = r.readU8();

        String name = r.readBorshString();

        String symbol = r.readBorshString();

        String uri = r.readBorshString();

        MintParam mintParam = new MintParam(decimals, name, symbol, uri);

        // CurveParams enum
        int curveType = r.readU8();

        if (curveType != 0) {
            throw new IllegalArgumentException("暂不支持 curveType=" + curveType);
        }

        BigInteger supply = r.readU64();

        BigInteger totalBaseSell = r.readU64();

        BigInteger totalQuoteFundRaising = r.readU64();

        int migrateType = r.readU8();

        ConstantCurve curve = new ConstantCurve(supply, totalBaseSell, totalQuoteFundRaising, migrateType);

        // Vesting
        Vesting vesting = new Vesting(r.readU64(), r.readU64(), r.readU64());

        int ammFeeOn = r.readU8();

        if (r.remaining() != 0) {
            throw new IllegalArgumentException("PoolCreateEvent 剩余未解析字节: " + r.remaining());
        }

        return new PoolCreateEvent(poolState, creator, config, mintParam, curveType, curve, vesting, ammFeeOn);
    }

    // ============================================================
    // TradeEvent
    // ============================================================

    public static TradeEvent decodeTradeEvent(byte[] data) {
        Reader r = new Reader(data);

        byte[] discriminator = r.readBytes(8);

        if (!Arrays.equals(discriminator, TRADE_EVENT)) return null;

        String poolState = Base58.encode(r.readBytes(32));

        BigInteger totalBaseSell = r.readU64();

        BigInteger virtualBase = r.readU64();

        BigInteger virtualQuote = r.readU64();

        BigInteger realBaseBefore = r.readU64();

        BigInteger realQuoteBefore = r.readU64();

        BigInteger realBaseAfter = r.readU64();

        BigInteger realQuoteAfter = r.readU64();

        BigInteger amountIn = r.readU64();

        BigInteger amountOut = r.readU64();

        BigInteger protocolFee = r.readU64();

        BigInteger platformFee = r.readU64();

        BigInteger creatorFee = r.readU64();

        BigInteger shareFee = r.readU64();

        int tradeDirection = r.readU8();

        int poolStatus = r.readU8();

        boolean exactIn = r.readBool();

        if (r.remaining() != 0) {
            throw new IllegalArgumentException("TradeEvent 剩余未解析字节: " + r.remaining());
        }

        return new TradeEvent(poolState,

                totalBaseSell,

                virtualBase, virtualQuote,

                realBaseBefore, realQuoteBefore,

                realBaseAfter, realQuoteAfter,

                amountIn, amountOut,

                protocolFee, platformFee, creatorFee, shareFee,

                tradeDirection, poolStatus, exactIn);
    }

    // ============================================================
    // 遍历整组 logs
    // ============================================================

    public static void parseLogs(List<String> logs) {

        for (String log : logs) {

            LaunchLabEvent event;

            try {
                event = decodeLog(log);
            } catch (Exception e) {
                System.err.println("LaunchLab event decode error: " + e.getMessage());
                continue;
            }

            if (event == null) {
                continue;
            }

            if (event instanceof PoolCreateEvent create) {

                System.out.println("========== POOL CREATE ==========");

                System.out.println("pool = " + create.poolState());

                System.out.println("creator = " + create.creator());

                System.out.println("config = " + create.config());

                System.out.println("name = " + create.mintParam().name());

                System.out.println("symbol = " + create.mintParam().symbol());

                System.out.println("decimals = " + create.mintParam().decimals());

                System.out.println("uri = " + create.mintParam().uri());

                System.out.println("supplyRaw = " + create.curve().supplyRaw());

                System.out.println("totalBaseSellRaw = " + create.curve().totalBaseSellRaw());

                System.out.println("totalQuoteFundRaisingRaw = " + create.curve().totalQuoteFundRaisingRaw());

                System.out.println("migrateType = " + create.curve().migrateType());

            } else if (event instanceof TradeEvent trade) {

                System.out.println("========== TRADE ==========");

                System.out.println("pool = " + trade.poolState());

                System.out.println("amountIn = " + trade.amountIn());

                System.out.println("amountOut = " + trade.amountOut());

                System.out.println("protocolFee = " + trade.protocolFee());

                System.out.println("platformFee = " + trade.platformFee());

                System.out.println("creatorFee = " + trade.creatorFee());

                System.out.println("shareFee = " + trade.shareFee());

                System.out.println("virtualBase = " + trade.virtualBase());

                System.out.println("virtualQuote = " + trade.virtualQuote());

                System.out.println("realBaseBefore = " + trade.realBaseBefore());

                System.out.println("realBaseAfter = " + trade.realBaseAfter());

                System.out.println("realQuoteBefore = " + trade.realQuoteBefore());

                System.out.println("realQuoteAfter = " + trade.realQuoteAfter());

                System.out.println("tradeDirection = " + trade.tradeDirection());

                System.out.println("poolStatus = " + trade.poolStatus());

                System.out.println("exactIn = " + trade.exactIn());
            }
        }
    }

    // ============================================================
    // UI amount
    // ============================================================

    public static String toUiAmount(BigInteger raw, int decimals) {
        String s = raw.toString();

        if (decimals == 0) {
            return s;
        }

        if (s.length() <= decimals) {
            return "0." + "0".repeat(decimals - s.length()) + s;
        }

        int p = s.length() - decimals;

        return s.substring(0, p) + "." + s.substring(p);
    }

    // ============================================================
    // Binary Reader
    // ============================================================

    private static final class Reader {

        private final byte[] data;
        private int offset;

        Reader(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length - offset;
        }

        void require(int n) {

            if (offset + n > data.length) {
                throw new IllegalArgumentException("读取越界 offset=" + offset + ", need=" + n + ", length=" + data.length);
            }
        }

        byte[] readBytes(int n) {

            require(n);

            byte[] result = Arrays.copyOfRange(data, offset, offset + n);

            offset += n;

            return result;
        }

        int readU8() {

            require(1);

            return data[offset++] & 0xff;
        }

        /**
         * 读取 unsigned u16 little-endian。
         *
         * @return 0 ~ 65535
         */
        public int readU16() {

            int b0 = readU8();
            int b1 = readU8();

            return b0 | (b1 << 8);
        }

        boolean readBool() {

            int value = readU8();

            if (value == 0) {
                return false;
            }

            if (value == 1) {
                return true;
            }

            throw new IllegalArgumentException("非法 bool: " + value);
        }

        long readU32() {

            require(4);

            long value = (data[offset] & 0xffL) | ((data[offset + 1] & 0xffL) << 8) | ((data[offset + 2] & 0xffL) << 16) | ((data[offset + 3] & 0xffL) << 24);

            offset += 4;

            return value;
        }

        BigInteger readU64() {

            require(8);

            byte[] bigEndian = new byte[8];

            for (int i = 0; i < 8; i++) {
                bigEndian[7 - i] = data[offset + i];
            }

            offset += 8;

            return new BigInteger(1, bigEndian);
        }

        String readBorshString() {

            long lengthLong = readU32();

            if (lengthLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("字符串长度异常: " + lengthLong);
            }

            int length = (int) lengthLong;

            byte[] bytes = readBytes(length);

            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    // ============================================================
    // Helper
    // ============================================================

    private static boolean startsWith(byte[] data, byte[] prefix) {

        if (data.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {

            if (data[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    // ============================================================
    // Base58 encoder
    //
    // 如果你的项目已经有 Base58.encode，
    // 这一段直接删掉。
    // ============================================================

    private static final class Base58 {

        private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

        static String encode(byte[] input) {

            if (input.length == 0) {
                return "";
            }

            input = Arrays.copyOf(input, input.length);

            int zeros = 0;

            while (zeros < input.length && input[zeros] == 0) {
                zeros++;
            }

            char[] encoded = new char[input.length * 2];

            int outputStart = encoded.length;

            for (int inputStart = zeros; inputStart < input.length; ) {

                int remainder = divmod58(input, inputStart);

                if (input[inputStart] == 0) {
                    inputStart++;
                }

                encoded[--outputStart] = ALPHABET[remainder];
            }

            while (outputStart < encoded.length && encoded[outputStart] == ALPHABET[0]) {
                outputStart++;
            }

            while (--zeros >= 0) {
                encoded[--outputStart] = ALPHABET[0];
            }

            return new String(encoded, outputStart, encoded.length - outputStart);
        }

        private static int divmod58(byte[] number, int startAt) {

            int remainder = 0;

            for (int i = startAt; i < number.length; i++) {

                int digit256 = number[i] & 0xff;

                int temp = remainder * 256 + digit256;

                number[i] = (byte) (temp / 58);

                remainder = temp % 58;
            }

            return remainder;
        }
    }

    public static void main(String[] args) {


        String text = "Program data: l9fiCXahc67x1ix+ShQd8EzMpeY0CWde/atf6OeJkRrKMVlXXeFw09fzxJN2bH1WeD1vuvIFtEOb1ohR7wF8MmrenzM4wXgD+wfR5v3QQ5eDhvfm3hfXAZHoi8PvUL9N/PPSrInzkZIGBwAAAGJvYm1jYXQHAAAAYm9ibWNhdFAAAABodHRwczovL2lwZnMuaW8vaXBmcy9iYWZrcmVpYXlocWppN24ydDR4YWpnZXV3Y3JzdXllc25lNnJwYnltNjRoam5sczRsc2M2M3hpdGsyYQAAgMakfo0DAAB4xftR0QIAxs0ILwEAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

        text = text.substring("Program data: ".length()).trim();

        //List<String > text = List.of("Program ComputeBudget111111111111111111111111111111 invoke [1]", "Program ComputeBudget111111111111111111111111111111 success", "Program ComputeBudget111111111111111111111111111111 invoke [1]", "Program ComputeBudget111111111111111111111111111111 success", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj invoke [1]", "Program log: Instruction: InitializeWithToken2022", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [2]", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA consumed 233 of 577702 compute units", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA success", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj invoke [2]", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj consumed 2165 of 555490 compute units", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj success", "Program data: l9fiCXahc67xRmZBAISyMX4QXnfDG8HhRRa85y/ZGnDtkja66rbzuMmz3AaH3RZcQ92yjHXUWItGLwMwfwGF1+3hv/sHanEiMt96XjRzxngtu/t03R0TzqBrmFo2jO9Qo/QnyPc5HsEGCQAAAHN0b25rIGJlbgUAAABTVEJFTkUAAABodHRwczovL2dhdGV3YXkuaXJ5cy54eXovSHlqTFVMVHJZYjZ5NEVmRGJvVEFWTTlNSkFUcktpckx2aWhuRmtFNWhQMnAAAIDGpH6NAwAAeMX7UdECALtQx/azOgAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: MetadataPointerInstruction::Initialize", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 925 of 547241 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 1280 of 544804 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: InitializeMint2", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 1940 of 542105 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: TokenMetadataInstruction: Initialize", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 3643 of 534105 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: InitializeAccount3", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 2743 of 524516 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: MintTo", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 1876 of 505570 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: SetAuthority", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 1044 of 501881 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj consumed 101242 of 599700 compute units", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj success", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL invoke [1]", "Program log: CreateIdempotent", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: GetAccountDataSize", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 1570 of 493197 compute units", "Program return: TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb tgAAAAAAAAA=", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program log: Initialize the associated token account", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: InitializeImmutableOwner", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 736 of 486805 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: InitializeAccount3", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 2799 of 483735 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL consumed 17805 of 498458 compute units", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL success", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL invoke [1]", "Program log: CreateIdempotent", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL consumed 5838 of 480653 compute units", "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL success", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj invoke [1]", "Program log: Instruction: BuyExactIn", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj invoke [2]", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj consumed 2165 of 448073 compute units", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj success", "Program data: vdt/007mYe7xRmZBAISyMX4QXnfDG8HhRRa85y/ZGnDtkja66rbzuAB4xftR0QIA43AOPunPAwASvVkjuBQAAAAAAAAAAAAAAAAAAAAAAAC+08aMKw8AAHdon8BTAAAAsOEF0FQAAAC+08aMKw8AANmxRzYAAAAAYMce2QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [2]", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA consumed 105 of 442105 compute units", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA success", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb invoke [2]", "Program log: Instruction: TransferChecked", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb consumed 3432 of 439623 compute units", "Program TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb success", "Program 11111111111111111111111111111111 invoke [2]", "Program 11111111111111111111111111111111 success", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [2]", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA consumed 233 of 421004 compute units", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA success", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [2]", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA consumed 118 of 414355 compute units", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA success", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA invoke [2]", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA consumed 105 of 411808 compute units", "Program TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA success", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj consumed 65981 of 474815 compute units", "Program LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj success");
        PoolCreateEvent poolCreateEvent = decodePoolCreateEvent(cn.hutool.core.codec.Base64.decode(text));

        System.out.println(JSONObject.toJSONString(poolCreateEvent));

        var tran = decodeTradeEvent(cn.hutool.core.codec.Base64.decode("vdt/007mYe7xRmZBAISyMX4QXnfDG8HhRRa85y/ZGnDtkja66rbzuAB4xftR0QIA43AOPunPAwASvVkjuBQAAAAAAAAAAAAAAAAAAAAAAAC+08aMKw8AAHdon8BTAAAAsOEF0FQAAAC+08aMKw8AANmxRzYAAAAAYMce2QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"));

        System.out.println(JSONObject.toJSONString(tran));

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println(format.format(new Date(new Date().getTime())));
        System.out.println(format.format(new Date(new Date().getTime() + 5000L)));
        String v1 = """
                MSG unifiedTradeEvent.processed.9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump 35 2533
                "{\\"slotIndexId\\":\\"00044686079800096200000502\\",\\"tx\\":\\"2X2X38nBtmAeoQmMDghMCt2EWn24Y8XCTS6DXitz1KRkbpKDMyQF892yCxCPYVfLWg2uQ7Q2nQj7zsJF3cZeyeqf\\",\\"timestamp\\":\\"2026-09-14T02:19:39+00:00\\",\\"program\\":\\"pump_amm\\",\\"isBondingCurve\\":false,\\"mintAddress\\":\\"9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump\\",\\"quoteMintAddress\\":\\"So11111111111111111111111111111111111111112\\",\\"poolAddress\\":\\"FnzKY6x7entQ1eR3D225dQyT7ybfka4PskBMQhb8L3CC\\",\\"userAddress\\":\\"Bapcdwy318WjUcQhUiZWgUksdZpRSWWWsXfvWKf31nH9\\",\\"type\\":\\"sell\\",\\"marketCap\\":\\"144661847.15432393881574385254\\",\\"marketCapUsd\\":\\"144661847.15432393881574385254\\",\\"marketCapNative\\":\\"1439631.4929169312848540472000\\",\\"baseAmount\\":\\"648.378695\\",\\"quoteAmount\\":\\"0.930705460\\",\\"lpFee\\":\\"0.001867012\\",\\"protocolFee\\":\\"0.000466753\\",\\"lpFeeUsd\\":\\"0.187607318892455588\\",\\"protocolFeeUsd\\":\\"0.046901829723113897\\",\\"priceBasePerQuote\\":\\"694.62220361256113057673006086\\",\\"priceQuotePerBase\\":\\"0.0014396314929169312848540472\\",\\"priceUsd\\":\\"0.1446618471543239388157438525\\",\\"priceSol\\":\\"0.0014396314929169312848540472\\",\\"amountSol\\":\\"0.930705460\\",\\"amountUsd\\":\\"93.522246257211827540\\",\\"coinMeta\\":{\\"bondingCurve\\":\\"FxC6pJJSiu6efSn8PmBMQjQaYm6dkSqmQYohui6NPnrf\\",\\"createdTs\\":1781643948000,\\"creator\\":\\"yHCxHBEaJW5tbndqC8JciSThr7U1cqLpdcsvHcx6PRe\\",\\"isCashbackEnabled\\":false,\\"isMayhemMode\\":false,\\"mint\\":\\"9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump\\",\\"name\\":\\"The Black Bull\\",\\"symbol\\":\\"ANSEM\\",\\"uri\\":\\"https://meta.uxento.io/data/7f4c8448-c38e-4c34-aa37-a28cff9806d0\\",\\"user\\":\\"yHCxHBEaJW5tbndqC8JciSThr7U1cqLpdcsvHcx6PRe\\"},\\"creatorAddress\\":\\"9ENSWnedBEAvVB7jJKZrLRZ9vuVuJdBE7uJt2oAsG1jr\\",\\"creatorFee\\":\\"0.000466753\\",\\"creatorFeeUsd\\":\\"0.046901829723113897\\",\\"baseReserves\\":\\"7589597.622574\\",\\"quoteReserves\\":\\"10926.223756025\\",\\"solPriceUsd\\":\\"100.485331049\\",\\"virtualSolReserves\\":null,\\"virtualQuoteReserves\\":null,\\"virtualTokenReserves\\":null,\\"platform\\":null,\\"platformFee\\":null,\\"platformFeeUsd\\":null,\\"supply\\":\\"1000000000.000000\\",\\"txFee\\":\\"0.00010016\\",\\"txFeeUsd\\":\\"0.01006461075786784\\",\\"priorityFee\\":\\"0.00009516\\",\\"priorityFeeUsd\\":\\"0.00956218410262284\\",\\"relayerFee\\":\\"0\\",\\"cashback\\":\\"0\\",\\"cashbackUsd\\":\\"0\\",\\"cashbackFeeBasisPoints\\":\\"0\\",\\"buybackFee\\":\\"0.000233376\\",\\"buybackFeeBasisPoints\\":\\"5000\\",\\"relayerFeeUsd\\":\\"0\\",\\"relayer\\":\\"default\\",\\"app\\":\\"axiom\\",\\"appFee\\":\\"0.007212967\\",\\"appFeeUsd\\":\\"0.724797376840512383\\",\\"surface\\":null,\\"surfaceAttribution\\":null}"
                MSG unifiedTradeEvent.processed.9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump 35 2532
                "{\\"slotIndexId\\":\\"00044686079800096900000502\\",\\"tx\\":\\"tCtjrHEqd1HKGP7XNRAyu2pWYBsHhndadGqLwtebZJxccatnrRNERqgt71emTGqpXCQCVUZp4DknvjNgkfdEygC\\",\\"timestamp\\":\\"2026-09-14T02:19:39+00:00\\",\\"program\\":\\"pump_amm\\",\\"isBondingCurve\\":false,\\"mintAddress\\":\\"9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump\\",\\"quoteMintAddress\\":\\"So11111111111111111111111111111111111111112\\",\\"poolAddress\\":\\"FnzKY6x7entQ1eR3D225dQyT7ybfka4PskBMQhb8L3CC\\",\\"userAddress\\":\\"G8mikf4gH6NrMMCQJqbCKRyoemPwGJT2RVHT9Z3v94Pc\\",\\"type\\":\\"sell\\",\\"marketCap\\":\\"144637562.01770283111780346569\\",\\"marketCapUsd\\":\\"144637562.01770283111780346569\\",\\"marketCapNative\\":\\"1439389.8144911592141517319000\\",\\"baseAmount\\":\\"637.770657\\",\\"quoteAmount\\":\\"0.915323340\\",\\"lpFee\\":\\"0.001836156\\",\\"protocolFee\\":\\"0.000459039\\",\\"lpFeeUsd\\":\\"0.184506743517607644\\",\\"protocolFeeUsd\\":\\"0.046126685879401911\\",\\"priceBasePerQuote\\":\\"694.73883303357364228682714945\\",\\"priceQuotePerBase\\":\\"0.0014393898144911592141517319\\",\\"priceUsd\\":\\"0.1446375620177028311178034657\\",\\"priceSol\\":\\"0.0014393898144911592141517319\\",\\"amountSol\\":\\"0.915323340\\",\\"amountUsd\\":\\"91.976568836776383660\\",\\"coinMeta\\":{\\"bondingCurve\\":\\"FxC6pJJSiu6efSn8PmBMQjQaYm6dkSqmQYohui6NPnrf\\",\\"createdTs\\":1781643948000,\\"creator\\":\\"yHCxHBEaJW5tbndqC8JciSThr7U1cqLpdcsvHcx6PRe\\",\\"isCashbackEnabled\\":false,\\"isMayhemMode\\":false,\\"mint\\":\\"9cRCn9rGT8V2imeM2BaKs13yhMEais3ruM3rPvTGpump\\",\\"name\\":\\"The Black Bull\\",\\"symbol\\":\\"ANSEM\\",\\"uri\\":\\"https://meta.uxento.io/data/7f4c8448-c38e-4c34-aa37-a28cff9806d0\\",\\"user\\":\\"yHCxHBEaJW5tbndqC8JciSThr7U1cqLpdcsvHcx6PRe\\"},\\"creatorAddress\\":\\"9ENSWnedBEAvVB7jJKZrLRZ9vuVuJdBE7uJt2oAsG1jr\\",\\"creatorFee\\":\\"0.000459039\\",\\"creatorFeeUsd\\":\\"0.046126685879401911\\",\\"baseReserves\\":\\"7590235.393231\\",\\"quoteReserves\\":\\"10925.307514607\\",\\"solPriceUsd\\":\\"100.485331049\\",\\"virtualSolReserves\\":null,\\"virtualQuoteReserves\\":null,\\"virtualTokenReserves\\":null,\\"platform\\":null,\\"platformFee\\":null,\\"platformFeeUsd\\":null,\\"supply\\":\\"1000000000.000000\\",\\"txFee\\":\\"0.00010016\\",\\"txFeeUsd\\":\\"0.01006461075786784\\",\\"priorityFee\\":\\"0.00009516\\",\\"priorityFeeUsd\\":\\"0.00956218410262284\\",\\"relayerFee\\":\\"0\\",\\"cashback\\":\\"0\\",\\"cashbackUsd\\":\\"0\\",\\"cashbackFeeBasisPoints\\":\\"0\\",\\"buybackFee\\":\\"0.000229519\\",\\"buybackFeeBasisPoints\\":\\"5000\\",\\"relayerFeeUsd\\":\\"0\\",\\"relayer\\":\\"default\\",\\"app\\":\\"axiom\\",\\"appFee\\":\\"0.007093756\\",\\"appFeeUsd\\":\\"0.712818420040830044\\",\\"surface\\":null,\\"surfaceAttribution\\":null}"
                """;

        for (String s : v1.split("\n")) {
            if (StringUtils.isEmpty(s)) continue;
            if (s.contains("MSG unifiedTradeEvent.processed")) continue;
            String payload = s.replace("\"{", "{").replace("}\"", "}").replace("\\\"", "\"");
            var json = JSONObject.parseObject(payload);
            System.out.println(json.toJSONString());
        }


        var b = "GC3i2jSx8awwe9xBfbR9LBTrzgFPUE9b56kGnez3cWMeYXCyD6h2GfXoh8SwhF9Wr3zpRM36W3eVRKxsPRcG1ebY2w";
        var item = decodeInitializeTransferFeeConfig(cn.hutool.core.codec.Base58.decode(b));
        System.out.println(item);

        tran = decodeTradeEvent(cn.hutool.core.codec.Base64.decode("vdt/007mYe5uwvcVsi1R5FgYUwE9JCWqL//lT1CbcWEIt3TDPC2kf6pfKQAAAAAAc+HwkxYAAAABDW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CJcFslqAAAAAKoLTfwGAAAAjS7ns8zPAwCqXykAAAAAAI2W1Gc70QIArRHmpPwpRKT6glG++BVCbhv7KMa2ZGZ3YHxq2fVmpkZfAAAAAAAAAJ9kAAAAAAAADW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CIeAAAAAAAAAMcfAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAGJ1eQAAAAAAAAAAAAAAAAAAAAAAiBMAAAAAAABPMgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKpfKQAAAAAAqgtN/AYAAACqXykAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="));

        System.out.println(JSONObject.toJSONString(tran));

    }

    /**
     * Token-2022 TransferFeeExtension 指令类型。
     * <p>
     * 这段解析针对：
     * <p>
     * TokenInstruction::TransferFeeExtension
     * -> TransferFeeInstruction::InitializeTransferFeeConfig
     * <p>
     * 当前示例：
     * <p>
     * 1a 00
     * 01 [32 bytes config authority]
     * 01 [32 bytes withdraw authority]
     * 2c 01
     * 00 80 c6 a4 7e 8d 03 00
     */
    public static TransferFeeConfig decodeInitializeTransferFeeConfig(byte[] data) {

        Reader r = new Reader(data);

        // ============================================================
        // 1. Token-2022 顶层指令
        //
        // 0x1A = TransferFeeExtension
        // ============================================================
        int tokenInstruction = r.readU8();

        if (tokenInstruction != 0x1A) {
            return null;
        }

        // ============================================================
        // 2. TransferFee 子指令
        //
        // 0x00 = InitializeTransferFeeConfig
        // ============================================================
        int transferFeeInstruction = r.readU8();

        if (transferFeeInstruction != 0x00) {
            return null;
        }

        // ============================================================
        // 3. transfer_fee_config_authority
        //
        // Option<Pubkey>
        //
        // 0 = None
        // 1 = Some(Pubkey)
        // ============================================================
        String transferFeeConfigAuthority = readOptionalPubkey(r);

        // ============================================================
        // 4. withdraw_withheld_authority
        //
        // Option<Pubkey>
        // ============================================================
        String withdrawWithheldAuthority = readOptionalPubkey(r);

        // ============================================================
        // 5. transfer_fee_basis_points
        //
        // u16 little-endian
        //
        // 示例：
        //
        // 2c 01
        // = 0x012c
        // = 300
        // = 3%
        // ============================================================
        int transferFeeBasisPoints = r.readU16();

        // ============================================================
        // 6. maximum_fee
        //
        // u64 little-endian
        // 建议使用 BigInteger，避免 Java signed long 问题。
        // ============================================================
        BigInteger maximumFee = r.readU64();

        if (r.remaining() != 0) {
            throw new IllegalArgumentException("InitializeTransferFeeConfig 剩余未解析字节: " + r.remaining());
        }

        return new TransferFeeConfig(transferFeeConfigAuthority, withdrawWithheldAuthority, transferFeeBasisPoints, maximumFee);
    }


    /**
     * 解析 Option<Pubkey>。
     *
     * @param r 二进制 Reader
     * @return Some 时返回 Base58 Pubkey；None 时返回 null
     */
    private static String readOptionalPubkey(Reader r) {

        int option = r.readU8();

        if (option == 0) {
            return null;
        }

        if (option != 1) {
            throw new IllegalArgumentException("非法 Option<Pubkey> tag: " + option);
        }

        return Base58.encode(r.readBytes(32));
    }


    /**
     * Token-2022 Transfer Fee 初始化参数。
     *
     * @param transferFeeConfigAuthority Transfer Fee 配置修改权限；null 表示无权限
     * @param withdrawWithheldAuthority  提取 withheld fee 的权限；null 表示无权限
     * @param transferFeeBasisPoints     手续费基点，300 = 3%
     * @param maximumFee                 单次 Transfer Fee 最大值，raw token amount
     */
    public record TransferFeeConfig(String transferFeeConfigAuthority, String withdrawWithheldAuthority,
                                    int transferFeeBasisPoints, BigInteger maximumFee) {

        /**
         * 转换成人类可读的百分比。
         * <p>
         * 例如：
         * 300 -> 3.0
         *
         * @return 百分比数值
         */
        public double feePercent() {
            return transferFeeBasisPoints / 100.0;
        }
    }

}
