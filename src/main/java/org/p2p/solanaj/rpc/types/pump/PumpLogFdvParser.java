package org.p2p.solanaj.rpc.types.pump;

import cn.hutool.core.codec.Base58;

import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 从 Pump.fun 交易 logMessages 中解析 CreateEvent、TradeEvent，
 * 并计算买入后的 FDV。
 * <p>
 * Base58 使用你项目现有的 Base58.encode(byte[])。
 */
public class PumpLogFdvParser {

    // Pump.fun CreateEvent
    private static final byte[] CREATE_EVENT = {0x1b, 0x72, (byte) 0xa9, 0x4d, (byte) 0xde, (byte) 0xeb, 0x63, 0x76};

    // Pump.fun TradeEvent
    private static final byte[] TRADE_EVENT = {(byte) 0xbd, (byte) 0xdb, 0x7f, (byte) 0xd3, 0x4e, (byte) 0xe6, 0x61, (byte) 0xee};
    private static final byte[] PUMP_TRADE_EVENT = {(byte) 0xbd, (byte) 0xdb, 0x7f, (byte) 0xd3, 0x4e, (byte) 0xe6, 0x61, (byte) 0xee};

    /**
     * 从交易日志计算 FDV。
     *
     * @param logs        meta.logMessages
     * @param decimals    代币精度，这笔为 6
     * @param solPriceUsd SOL 美元价格，这里为 110
     * @return FDV 解析结果；无法匹配 CreateEvent 和 TradeEvent 时返回 null
     */
    public static FdvResult parseFdv(List<String> logs, int decimals, BigDecimal solPriceUsd) {

        // 创建事件按 mint 缓存，保存供应量
        Map<String, CreateEvent> creates = new HashMap<>();

        // 买卖事件按日志顺序保存
        List<TradeEvent> trades = new ArrayList<>();

        for (String log : logs) {

            if (!log.startsWith("Program data: ")) {
                continue;
            }

            // Anchor emit_cpi 事件日志是 Base64
            byte[] data;
            try {
                data = Base64.getDecoder().decode(log.substring("Program data: ".length()));
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (hasDiscriminator(data, CREATE_EVENT)) {

                CreateEvent event = decodeCreateEvent(data);
                creates.put(event.mint(), event);

            } else if (hasDiscriminator(data, TRADE_EVENT)) {

                trades.add(decodeTradeEvent(data));
            }
        }

        // 使用日志中最后一笔、且能匹配 CreateEvent 的交易
        for (int i = trades.size() - 1; i >= 0; i--) {

            TradeEvent trade = trades.get(i);
            CreateEvent create = creates.get(trade.mint());

            if (create == null) {
                continue;
            }

            // lamports -> SOL
            BigDecimal virtualSol = new BigDecimal(trade.virtualSolReserves()).movePointLeft(9);

            // raw token -> 实际 Token 数量
            BigDecimal virtualToken = new BigDecimal(trade.virtualTokenReserves()).movePointLeft(decimals);

            BigDecimal supply = new BigDecimal(create.tokenTotalSupply()).movePointLeft(decimals);

            // FDV = (虚拟 SOL / 虚拟 Token) * 总供应量 * SOL 美元价格
            BigDecimal fdvUsd = virtualSol.divide(virtualToken, MathContext.DECIMAL128).multiply(supply).multiply(solPriceUsd);

            return new FdvResult(trade.mint(), trade.virtualSolReserves(), trade.virtualTokenReserves(), create.tokenTotalSupply(), fdvUsd);
        }

        return null;
    }

    /**
     * 解析 Pump.fun CreateEvent。
     * <p>
     * CreateEvent 前三个字段为 Borsh String：
     * name、symbol、uri。
     * <p>
     * 后面是 mint、bondingCurve、user、creator、timestamp、
     * virtualToken、virtualSol、realToken、totalSupply。
     */
    public static CreateEvent decodeCreateEvent(byte[] data) {

        if (!hasDiscriminator(data, CREATE_EVENT)) {
            return null;
        }

        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        b.position(8); // 跳过 discriminator

        skipBorshString(b); // name
        skipBorshString(b); // symbol
        skipBorshString(b); // uri

        String mint = readPubkey(b);

        b.position(b.position() + 32); // bondingCurve
        b.position(b.position() + 32); // user
        b.position(b.position() + 32); // creator

        b.getLong(); // timestamp，i64

        readU64(b); // virtualTokenReserves
        readU64(b); // virtualSolReserves
        readU64(b); // realTokenReserves

        BigInteger totalSupply = readU64(b);

        return new CreateEvent(mint, totalSupply);
    }

    /**
     * 解析 Pump.fun TradeEvent。
     * <p>
     * 支持：
     * 1. logMessages 的 Program data（Base64 解码后的 382 bytes）
     * 2. innerInstructions 的 Event CPI（Base58 解码后的 390 bytes）
     *
     * @param data 原始解码字节
     * @return 交易事件，非 Pump TradeEvent 返回 null
     */
    public static TradeEvent decodeTradeEvent(byte[] data) {

        if (data == null) {
            return null;
        }

        // Pump TradeEvent discriminator
        byte[] TRADE_EVENT = {(byte) 0xbd, (byte) 0xdb, 0x7f, (byte) 0xd3, 0x4e, (byte) 0xe6, 0x61, (byte) 0xee};

        // Pump Event CPI instruction discriminator
        byte[] EVENT_CPI = {(byte) 0xe4, 0x45, (byte) 0xa5, 0x2e, 0x51, (byte) 0xcb, (byte) 0x9a, 0x1d};

        int offset;

        if (matches(data, 0, TRADE_EVENT)) {

            // 来自 logMessages：事件从 offset 0 开始
            offset = 0;

        } else if (matches(data, 0, EVENT_CPI) && matches(data, 8, TRADE_EVENT)) {

            // 来自 innerInstructions：跳过前 8 bytes Event CPI discriminator
            offset = 8;

        } else {
            return null;
        }

        // TradeEvent 至少需要 129 bytes
        if (data.length < offset + 129) {
            throw new IllegalArgumentException("Pump TradeEvent 数据长度不足: " + data.length);
        }

        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 跳过事件 discriminator
        b.position(offset + 8);

        String mint = readPubkey(b);
        BigInteger solAmount = readU64(b);
        BigInteger tokenAmount = readU64(b);
        boolean isBuy = b.get() != 0;

        // 跳过 user pubkey
        b.position(b.position() + 32);

        // 跳过 timestamp
        b.getLong();

        // 买卖执行后的储备
        BigInteger virtualSol = readU64(b);
        BigInteger virtualToken = readU64(b);
        BigInteger realSol = readU64(b);
        BigInteger realToken = readU64(b);

        return new TradeEvent(mint, solAmount, tokenAmount, isBuy, virtualSol, virtualToken, realSol, realToken);
    }

    /**
     * 判断 data 指定偏移是否匹配 discriminator。
     */
    private static boolean matches(byte[] data, int offset, byte[] discriminator) {
        if (data == null || offset < 0 || data.length - offset < discriminator.length) {
            return false;
        }

        for (int i = 0; i < discriminator.length; i++) {
            if (data[offset + i] != discriminator[i]) {
                return false;
            }
        }

        return true;
    }


    public record PumpTradeEvent(String mint, BigInteger solAmount, BigInteger tokenAmount, boolean isBuy, String user,
                                 BigInteger timestamp, BigInteger virtualSolReserves, BigInteger virtualTokenReserves,
                                 BigInteger realSolReserves, BigInteger realTokenReserves) {
    }

    /**
     * 校验 Anchor 事件 discriminator。
     */
    private static boolean hasDiscriminator(byte[] data, byte[] expected) {
        if (data.length < 8) {
            return false;
        }

        for (int i = 0; i < 8; i++) {
            if (data[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 跳过一个 Borsh String：u32LE 长度 + UTF-8 内容。
     */
    private static void skipBorshString(ByteBuffer b) {

        int length = b.getInt();

        if (length < 0 || length > b.remaining()) {
            throw new IllegalArgumentException("非法 Borsh String 长度");
        }

        b.position(b.position() + length);
    }

    /**
     * 读取 32 字节 Solana PublicKey。
     */
    private static String readPubkey(ByteBuffer b) {
        byte[] bytes = new byte[32];
        b.get(bytes);
        return Base58.encode(bytes);
    }

    /**
     * 读取 unsigned u64 little-endian，返回 BigInteger。
     */
    private static BigInteger readU64(ByteBuffer b) {

        byte[] bytes = new byte[8];
        b.get(bytes);

        // 小端转大端
        for (int i = 0; i < 4; i++) {
            byte temp = bytes[i];
            bytes[i] = bytes[7 - i];
            bytes[7 - i] = temp;
        }

        return new BigInteger(1, bytes);
    }

    public record CreateEvent(String mint, BigInteger tokenTotalSupply) {
    }

    public record TradeEvent(String mint, BigInteger solAmount, BigInteger tokenAmount, boolean isBuy,
                             BigInteger virtualSolReserves, BigInteger virtualTokenReserves, BigInteger realSolReserves,
                             BigInteger realTokenReserves) {
    }

    public record FdvResult(String mint, BigInteger virtualSolReserves, BigInteger virtualTokenReserves,
                            BigInteger tokenTotalSupply, BigDecimal fdvUsd) {
    }

    private static final byte[] FULL_TRADE_EVENT = {
            (byte) 0xbd, (byte) 0xdb, 0x7f, (byte) 0xd3,
            0x4e, (byte) 0xe6, 0x61, (byte) 0xee
    };

    private static final byte[] FULL_EVENT_CPI = {
            (byte) 0xe4, 0x45, (byte) 0xa5, 0x2e,
            0x51, (byte) 0xcb, (byte) 0x9a, 0x1d
    };

    /**
     * 完整解析当前 Pump TradeEvent 布局。
     *
     * 支持：
     * 1. Program data：Base64 解码后的原始事件。
     * 2. inner instruction：Base58 解码后的 Event CPI。
     *
     * 非 TradeEvent 返回 null。
     * 数据不完整或布局不符时抛 IllegalArgumentException，不静默填充零。
     */
    public static TradeEventFull decodeTradeEventFull(byte[] data) {
        if (data == null) {
            return null;
        }

        final int offset;
        if (fullMatches(data, 0, FULL_TRADE_EVENT)) {
            offset = 0;
        } else if (fullMatches(data, 0, FULL_EVENT_CPI)
                && fullMatches(data, 8, FULL_TRADE_EVENT)) {
            // 跳过 Event CPI 的8字节前缀。
            offset = 8;
        } else {
            return null;
        }

        ByteBuffer b = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN);

        // 跳过事件 discriminator。
        b.position(offset + 8);

        try {
            String mint = fullPubkey(b);
            BigInteger solAmount = fullU64(b);
            BigInteger tokenAmount = fullU64(b);
            boolean isBuy = fullBool(b);
            String user = fullPubkey(b);
            long timestamp = b.getLong(); // i64，Unix秒，不是毫秒

            // 成交后的储备。
            BigInteger virtualSolReserves = fullU64(b);
            BigInteger virtualTokenReserves = fullU64(b);
            BigInteger realSolReserves = fullU64(b);
            BigInteger realTokenReserves = fullU64(b);

            // 协议及创建者手续费。
            String feeRecipient = fullPubkey(b);
            BigInteger feeBasisPoints = fullU64(b);
            BigInteger fee = fullU64(b);
            String creator = fullPubkey(b);
            BigInteger creatorFeeBasisPoints = fullU64(b);
            BigInteger creatorFee = fullU64(b);

            // 成交量跟踪字段。
            boolean trackVolume = fullBool(b);
            BigInteger totalUnclaimedTokens = fullU64(b);
            BigInteger totalClaimedTokens = fullU64(b);
            BigInteger currentSolVolume = fullU64(b);
            long lastUpdateTimestamp = b.getLong(); // i64

            // Borsh字符串：u32LE长度 + UTF-8，不能写死buy/sell长度。
            String ixName = fullString(b);
            boolean mayhemMode = fullBool(b);

            BigInteger cashbackFeeBasisPoints = fullU64(b);
            BigInteger cashback = fullU64(b);
            BigInteger buybackFeeBasisPoints = fullU64(b);
            BigInteger buybackFee = fullU64(b);

            /*
             * Vec<Shareholder>：
             * u32LE 数量，每项32字节地址 + u16LE shareBps。
             */
            long count = Integer.toUnsignedLong(b.getInt());

            // 后面还有32字节quoteMint和6个u64，共80字节。
            // 分配数组前检查数量，避免异常数据导致超大内存分配。
            if (b.remaining() < 80
                    || count > (b.remaining() - 80L) / 34L) {
                throw new IllegalArgumentException(
                        "shareholders数量或尾部长度不合法：" + count);
            }

            List<TradeShareholder> shareholders;
            if (count == 0) {
                shareholders = List.of();
            } else {
                List<TradeShareholder> items =
                        new ArrayList<>((int) count);

                for (int i = 0; i < count; i++) {
                    items.add(new TradeShareholder(
                            fullPubkey(b),
                            Short.toUnsignedInt(b.getShort())
                    ));
                }

                shareholders = List.copyOf(items);
            }

            // 报价币及其储备。
            String quoteMint = fullPubkey(b);
            BigInteger quoteAmount = fullU64(b);
            BigInteger virtualQuoteReserves = fullU64(b);
            BigInteger realQuoteReserves = fullU64(b);

            // 持有人奖励及尚未领取的创建者费用。
            BigInteger holderRewardsBps = fullU64(b);
            BigInteger holderRewards = fullU64(b);
            BigInteger creatorFeeUnclaimed = fullU64(b);

            // 严格校验当前布局，不把未来新增字段静默忽略。
//            if (b.hasRemaining()) {
//                throw new IllegalArgumentException(
//                        "TradeEvent有未识别尾部：" + b.remaining()
//                                + "字节，请核对事件版本");
//            }

            return new TradeEventFull(
                    mint, solAmount, tokenAmount, isBuy, user, timestamp,
                    virtualSolReserves, virtualTokenReserves,
                    realSolReserves, realTokenReserves,
                    feeRecipient, feeBasisPoints, fee,
                    creator, creatorFeeBasisPoints, creatorFee,
                    trackVolume, totalUnclaimedTokens, totalClaimedTokens,
                    currentSolVolume, lastUpdateTimestamp,
                    ixName, mayhemMode,
                    cashbackFeeBasisPoints, cashback,
                    buybackFeeBasisPoints, buybackFee,
                    shareholders,
                    quoteMint, quoteAmount,
                    virtualQuoteReserves, realQuoteReserves,
                    holderRewardsBps, holderRewards, creatorFeeUnclaimed
            );
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(
                    "完整TradeEvent数据不足，可能是旧版事件：length="
                            + data.length + ", position=" + b.position(),
                    e
            );
        }
    }

    /**
     * 所有数量保持链上原始单位，不自动转换SOL或代币精度。
     * u64使用BigInteger，避免超过long上限后变成负数。
     * 两个时间戳按IDL的i64使用long。
     */
    public record TradeEventFull(
            String mint,
            BigInteger solAmount,
            BigInteger tokenAmount,
            boolean isBuy,
            String user,
            long timestamp,

            BigInteger virtualSolReserves,
            BigInteger virtualTokenReserves,
            BigInteger realSolReserves,
            BigInteger realTokenReserves,

            String feeRecipient,
            BigInteger feeBasisPoints,
            BigInteger fee,
            String creator,
            BigInteger creatorFeeBasisPoints,
            BigInteger creatorFee,

            boolean trackVolume,
            BigInteger totalUnclaimedTokens,
            BigInteger totalClaimedTokens,
            BigInteger currentSolVolume,
            long lastUpdateTimestamp,

            String ixName,
            boolean mayhemMode,
            BigInteger cashbackFeeBasisPoints,
            BigInteger cashback,
            BigInteger buybackFeeBasisPoints,
            BigInteger buybackFee,

            List<TradeShareholder> shareholders,

            String quoteMint,
            BigInteger quoteAmount,
            BigInteger virtualQuoteReserves,
            BigInteger realQuoteReserves,
            BigInteger holderRewardsBps,
            BigInteger holderRewards,
            BigInteger creatorFeeUnclaimed
    ) {
    }

    /** shareBps为u16，10000表示100%；不是u64。 */
    public record TradeShareholder(String address, int shareBps) {
    }

    private static boolean fullMatches(
            byte[] data, int offset, byte[] expected) {

        if (data.length - offset < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }

        return true;
    }

    /** 32字节公钥转Base58地址。 */
    private static String fullPubkey(ByteBuffer b) {
        byte[] key = new byte[32];
        b.get(key);
        return Base58.encode(key);
    }

    /** 小端u64，不为每个整数额外分配8字节数组。 */
    private static BigInteger fullU64(ByteBuffer b) {
        long value = b.getLong();

        return value >= 0
                ? BigInteger.valueOf(value)
                : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63);
    }

    /** Borsh布尔值只允许0或1。 */
    private static boolean fullBool(ByteBuffer b) {
        byte value = b.get();

        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(
                    "非法Borsh布尔值：" + value);
        }

        return value == 1;
    }

    /** Borsh字符串：u32LE字节长度 + UTF-8内容。 */
    private static String fullString(ByteBuffer b) {
        int length = b.getInt();

        if (length < 0 || length > b.remaining()) {
            throw new IllegalArgumentException(
                    "非法Borsh字符串长度：" + length);
        }

        // b由ByteBuffer.wrap创建，直接读取底层数组，省去中间字节数组。
        String value = new String(
                b.array(),
                b.arrayOffset() + b.position(),
                length,
                StandardCharsets.UTF_8
        );

        b.position(b.position() + length);
        return value;
    }


    public static void main(String[] args) {
        //Program data: vdt/007mYe5uwvcVsi1R5FgYUwE9JCWqL//lT1CbcWEIt3TDPC2kf6pfKQAAAAAAc+HwkxYAAAABDW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CJcFslqAAAAAKoLTfwGAAAAjS7ns8zPAwCqXykAAAAAAI2W1Gc70QIArRHmpPwpRKT6glG++BVCbhv7KMa2ZGZ3YHxq2fVmpkZfAAAAAAAAAJ9kAAAAAAAADW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CIeAAAAAAAAAMcfAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAGJ1eQAAAAAAAAAAAAAAAAAAAAAAiBMAAAAAAABPMgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKpfKQAAAAAAqgtN/AYAAACqXykAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==

        String base64 = "vdt/007mYe5uwvcVsi1R5FgYUwE9JCWqL//lT1CbcWEIt3TDPC2kf6pfKQAAAAAAc+HwkxYAAAABDW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CJcFslqAAAAAKoLTfwGAAAAjS7ns8zPAwCqXykAAAAAAI2W1Gc70QIArRHmpPwpRKT6glG++BVCbhv7KMa2ZGZ3YHxq2fVmpkZfAAAAAAAAAJ9kAAAAAAAADW586ekuOPanPRtkC3XPlI+cnX/AFMKHWXmcDYsS+CIeAAAAAAAAAMcfAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAGJ1eQAAAAAAAAAAAAAAAAAAAAAAiBMAAAAAAABPMgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKpfKQAAAAAAqgtN/AYAAACqXykAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";


        var item = decodeTradeEvent(cn.hutool.core.codec.Base64.decode(base64));
        System.out.println(item);

         base64 = "vdt/007mYe5N4D5hdkb9qt5jB4Ei5M78lti6w3SdyueOp9b4E6t/H3QLWCYBAAAAHWXCRu+JAAABw/XymFi7X4Wp6jUrxB+pUtw7ZZEqEAEjPMf6u3s0ik7XLMlqAAAAAHS3eyIIAAAA46oVAfRFAwB0C1gmAQAAAOMSA7ViRwIArRHmpPwpRKT6glG++BVCbhv7KMa2ZGZ3YHxq2fVmpkZfAAAAAAAAAC3YywIAAAAAw/XymFi7X4Wp6jUrxB+pUtw7ZZEqEAEjPMf6u3s0ik4eAAAAAAAAAF8O4gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAGJ1eV9leGFjdF9zb2xfaW4AAAAAAAAAAAAAAAAAAAAAAIgTAAAAAAAAFuxlAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB0C1gmAQAAAHS3eyIIAAAAdAtYJgEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";


         item = decodeTradeEvent(cn.hutool.core.codec.Base64.decode(base64));
        System.out.println(item);

        base64 = "oEJqkYsF8fuTu4NSo8Ets6Sdrcb2gBC8LQVSu2AvQeKgNHC7Jo8GfyUMWHediG4J1wUPvLDAzBQsYBSFeDtprpgv8VtVT2mQNS4t9hiFeUcV3ANr4FbmDYGM4FLZMsLQUtnqHzCg4aWNzTSmS449c5oxyVA3pkwkYyoqtjfxxfQzkJ6AXxRZmmafpcuYPPDDjeoes6CxbqbUCCNVxsQN7KPN8Cu1rcwbwU8PDsqAkFiaTWDLgXGfhD5vNY9zSaKhp5UpDYXRqjnPYauZSKARqxPzzNYK2tuwDBsSZmTsVdn7EbT5AujCFxvAf4ajiNmfLMaurdmdcG9LqkdTG8ggByaAGr6mTUskxF8o2gRVMg7hiYMW7vAwszgEBYy27qnfmvaaKiqjCRXGfssSCmp57p1ruaS7jrAnmC4FhnfrFZJ2am9uhqZWuQBXo6RuPCixikSfzouodf3Db4qAFj3iTatz12ZnS9g99N5vuwFqPqtv5S88CAnakTXipiLTEMsUGHxyE53WSq23UMdcnf61gb";

        item =  decodeTradeEvent(Base58.decode(base64));
        System.out.println(item);

        base64 = "oEJqkYsF8fuTu4NSo8Ets5YrmUUHBkcnmU5wrojHLq9PbeDD4QHRVSDV9iVqQmxVuWNkFvReBiaFzw8ZadYfLfwFDSntfiX1qtwuyovBxXGnGngvP8ErVsDp7Wk6skn5yhibmMis8ybYY3oEZp4y3ynPpmWL6FNsHLNkgUtBJSMq6uQitHMgdUH2Lj68uaFqCkPYNE45ZnwwkGQSU43XyjKARU87WDS9PnQLHC6nPT9PVuwUo4dsDyhri8dB7rEy3uihxpeiyc8se1p1ar75JeQb6oesHc9jUGeEEP6JBRbrVqP1uNMvdDFkbfhk4zvvZCuWrLa3sX9ymzSJikEwhvQ9LNFwZd4uLT4Scz4rpeMqefbrAiwvmAmr9F2YtDjf33Bnzx6iR1h2vGATDPYFSX5SM3bC995Av9bYExoKurxeRen9hJGEX4J8PnH2Q8QwW2dsWNAWfeRirePFGJbuDYAuWVhps28JWBNjkwnc13XDo16Csr2cwdaT5bvwGJkL3N57UqbmTTUwz5WGKonkaK";


        base64 = "xP5X2rtd7D1PF6YVZfjxs4vBhj2A47nLApYuwVQF48BoaM4DesX2kaGqgz9g6JXJEXYVsGaPm3GJNpwZjUCr26SgaR4KEftU7YLEZbNf39bbg9L3BfbJJyDFGq3xuUP3XL857r3MRZhJE7EdG9jc6mBfeXyM25MgyMNKZ7ANHpaucNDdp3rtTvcQR175QuriqGYNpX2ecNaJQBtifV3e72ViLJCexRSunh3yfB7hSeG7SVh23dQSHSqCPEXQFPqVcV7xeKa957G9DfCK56DffXf6qNLDtr2zgfFf7ibbL6xmHPSgZtHzUexiFw67kRi7rD4CgWJciF8rpqPNEy9RngrXX93h336WePQPk4m8MZcL2dMLPxtkaJ8Fqzg9mN9kcZwE7LxxzWxtpTcWr7XcsAi9wYTZUwMSz95QT5nu1c2JPuKNpKfKwxnfsWwVN5GXtzDgETKjDftEVD3fDTsinriDyqPK9dm11xFiSRupBUBTGgE7quKqf67heTGmNsWyBge8YLvuK6SU6yFQemsogqPfJnbT3dkd7k8ooYLQHzsWAZ4mpbfd";


        TradeEventFull eventFull =  decodeTradeEventFull(Base58.decode(base64));
        System.out.println(eventFull);

    }
}
