package org.p2p.solanaj.rpc.types.pump;

import cn.hutool.core.codec.Base58;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 全部离线；只使用公开事件和合成字节，不访问RPC、不加载私钥、不发交易。 */
class PumpAmmLogParserTest {
    private static final byte[] CPI = {(byte)228,69,(byte)165,46,81,(byte)203,(byte)154,29};
    private static final byte[] BUY = {103,(byte)244,82,31,44,(byte)245,119,119};
    private static final byte[] SELL = {62,47,55,10,(byte)165,3,(byte)220,42};
    private static final String BUY_SAMPLE = "JRwKXTLw7eZBa6xJjDc7XxFfAQLFJmGstYuku8RcmWSnb8fKWfTnfsMRpAgwX6DmH7HNPBv3Zgbbh1XN59JTzgHeMagUvMdxCgyfvERw8R2i4ZntzNCER866GHtoGtBqxGmF2dg3T9BLCApM9Jv6U7711whVi2Vqv8vYtojZSd9DQpPKd25GHtcUag2RRvycoXY1EXGhwAcdzdKqZB8nYH4Nd6kaW7rb7638KjKc3KJgPMjZQsmjaHC9Vg3nEq2EwWEgpuwMHHYrgNkjv2J21EBJKnkMdsd3DfPxfLv2yBGnfFi9MVDGMp8LWk86QDK6qWxwgDnyF5X2DGCgvipHfw3bHhY9Lqos3uwC6siigbNBqTjRwXzsbdTm9QW3xN2Aq8XFPssK47XoPWujRLbE5KrbdSGfDH5ppESx3jf7v2bojfqVWFjCCthKSLFhUMqKzQ3dgNEgPLFJUKKcTJtFfR1KHJiVFMAciQi4Jgy7A8178WQtCshmruFmrKw13NEpy4wba9dbZKoVxRoCfkEVvshYPpm9vSBDiSv7RhKDG2BLAghzUxJjL6xapYTXNp5SeHXEXM1E7Si4r24UbAyq1o17mZXeZ8pLH7NnHpKr7XaoULTdRVZb25C9JEwWuLiSTkYpc22ninirTwndEhVcQfbUGVYSsXbXZ4YKvko";
    private static final String BUY_EXPECTED = """
            {
              "timestamp":"1792253238", "baseAmountOut":"2509122335399",
              "maxQuoteAmountIn":"2736083260", "userBaseTokenReserves":"0",
              "userQuoteTokenReserves":"2767911760", "poolBaseTokenReserves":"131892265172355",
              "poolQuoteTokenReserves":"125311630098", "quoteAmountIn":"2659852249",
              "lpFeeBasisPoints":"20", "lpFee":"5319705", "protocolFeeBasisPoints":"5",
              "protocolFee":"1329927", "quoteAmountInWithLpFee":"2665171954",
              "userQuoteAmountIn":"2691770478",
              "pool":"2qrojsGeNg99JcoeZ5afyzogMwzenVjH6ejvZFbz7xNw",
              "user":"5aLY85pyxiuX3fd4RgM3Yc1e3MAL6b7UgaZz6MS3JUfG",
              "userBaseTokenAccount":"7Zn7KPiA6oQBLfsroEQZCoPxmZd9QBfusavFwu4mnMUe",
              "userQuoteTokenAccount":"8mXRAyTKKJnQLfLxKssCVhckUK8depM1YKgP4qGB2Hhm",
              "protocolFeeRecipient":"62qc2CNXwrYqQScmEdiZFFAnJR262PxWEuNQtxfafNgV",
              "protocolFeeRecipientTokenAccount":"94qWNrtmfn42h3ZjUZwWvK1MEo9uVmmrBPd2hpNjYDjb",
              "coinCreator":"9VX7QNKUNMRFe4dxWAryAEXJLVBJA7TnoiMtxhESWcBX",
              "coinCreatorFeeBasisPoints":"95", "coinCreatorFee":"25268597", "trackVolume":"true",
              "totalUnclaimedTokens":"0", "totalClaimedTokens":"0", "currentSolVolume":"0",
              "lastUpdateTimestamp":"0", "minBaseAmountOut":"2509122335399", "ixName":"buy",
              "cashbackFeeBasisPoints":"0", "cashback":"0", "buybackFeeBasisPoints":"5000",
              "buybackFee":"664963", "virtualQuoteReserves":"11843915707", "canBoost":"true",
              "baseSupply":"906642114599001", "holderRewardsBps":"0", "holderRewards":"0",
              "creatorFeeUnclaimed":"5395518684"
            }
            """;

    @Test
    void userProvided497ByteBuyMatchesEveryField() throws Exception {
        byte[] data = Base58.decode(BUY_SAMPLE);
        assertEquals(497, data.length);
        var event = PumpAmmLogParser.decodeBuyEventFull(data);
        JSONObject expected = JSON.parseObject(BUY_EXPECTED);
        assertEquals(expected.size(), event.getClass().getRecordComponents().length);
        // 逐字段与用户提供的独立JSON对照，不能只校验两个成交金额。
        for (var field : event.getClass().getRecordComponents()) {
            assertEquals(expected.getString(field.getName()),
                    String.valueOf(field.getAccessor().invoke(event)), field.getName());
        }
        assertInstanceOf(PumpAmmLogParser.BuyEventFull.class, PumpAmmLogParser.decodeEvent(data));
        assertNull(PumpAmmLogParser.decodeSellEventFull(data));
        assertNull(PumpLogFdvParser.decodeTradeEventFull(data)); // 不修改/混用内盘解析器。
    }

    @Test
    void directEventCpiAndBase64LogHaveIdenticalResults() {
        byte[] cpi = Base58.decode(BUY_SAMPLE);
        byte[] direct = Arrays.copyOfRange(cpi, 8, cpi.length);
        var expected = PumpAmmLogParser.decodeBuyEventFull(cpi);
        assertEquals(expected, PumpAmmLogParser.decodeBuyEventFull(direct));
        assertEquals(expected, PumpAmmLogParser.decodeLog("Program data: " + Base64.getEncoder().encodeToString(direct)));
        assertEquals(expected, PumpAmmLogParser.decodeLog("Program data: " + Base64.getEncoder().encodeToString(cpi)));
    }

    @Test
    void completeBuyRecordSerializesWithTheExpectedFieldNames() {
        var event = PumpAmmLogParser.decodeBuyEventFull(Base58.decode(BUY_SAMPLE));
        JSONObject actual = JSON.parseObject(JSON.toJSONString(event));
        JSONObject expected = JSON.parseObject(BUY_EXPECTED);
        assertEquals(expected.keySet(), actual.keySet());
        for (String field : expected.keySet()) {
            assertEquals(expected.getString(field), actual.getString(field), field);
        }
    }

    @Test
    void sellFieldsFollowTheirOwnLayoutAndPreserveUnsignedAmounts() {
        var event = PumpAmmLogParser.decodeSellEventFull(sellData(false, BigInteger.valueOf(-123)));
        assertEquals(-17, event.timestamp());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), event.baseAmountIn());
        assertEquals(BigInteger.valueOf(11), event.minQuoteAmountOut());
        assertEquals(BigInteger.valueOf(12), event.userBaseTokenReserves());
        assertEquals(BigInteger.valueOf(13), event.userQuoteTokenReserves());
        assertEquals(BigInteger.valueOf(14), event.poolBaseTokenReserves());
        assertEquals(BigInteger.valueOf(15), event.poolQuoteTokenReserves());
        assertEquals(BigInteger.valueOf(16), event.quoteAmountOut());
        assertEquals(BigInteger.valueOf(17), event.lpFeeBasisPoints());
        assertEquals(BigInteger.valueOf(18), event.lpFee());
        assertEquals(BigInteger.valueOf(19), event.protocolFeeBasisPoints());
        assertEquals(BigInteger.valueOf(20), event.protocolFee());
        assertEquals(BigInteger.valueOf(21), event.quoteAmountOutWithoutLpFee());
        assertEquals(BigInteger.valueOf(22), event.userQuoteAmountOut());
        assertEquals(key(1), event.pool());
        assertEquals(key(2), event.user());
        assertEquals(key(3), event.userBaseTokenAccount());
        assertEquals(key(4), event.userQuoteTokenAccount());
        assertEquals(key(5), event.protocolFeeRecipient());
        assertEquals(key(6), event.protocolFeeRecipientTokenAccount());
        assertEquals(key(7), event.coinCreator());
        assertEquals(BigInteger.valueOf(300), event.coinCreatorFeeBasisPoints());
        assertEquals(BigInteger.valueOf(301), event.coinCreatorFee());
        assertEquals(BigInteger.valueOf(400), event.cashbackFeeBasisPoints());
        assertEquals(BigInteger.valueOf(401), event.cashback());
        assertEquals(BigInteger.valueOf(402), event.buybackFeeBasisPoints());
        assertEquals(BigInteger.valueOf(403), event.buybackFee());
        assertEquals(BigInteger.valueOf(-123), event.virtualQuoteReserves());
        assertTrue(event.canBoost());
        assertEquals(BigInteger.valueOf(11), event.baseSupply());
        assertEquals(BigInteger.valueOf(12), event.holderRewardsBps());
        assertEquals(BigInteger.valueOf(13), event.holderRewards());
        assertEquals(BigInteger.valueOf(14), event.creatorFeeUnclaimed());
        byte[] cpi = sellData(true, BigInteger.valueOf(-123));
        assertEquals(449, cpi.length);
        assertEquals(event, PumpAmmLogParser.decodeEvent(cpi));
        assertEquals(event, PumpAmmLogParser.decodeLog("Program data: " + Base64.getEncoder().encodeToString(cpi)));
        assertNull(PumpAmmLogParser.decodeBuyEventFull(cpi));
    }

    @Test
    void signed128BoundariesDoNotShiftFollowingFields() {
        BigInteger[] values = {
                BigInteger.ONE.shiftLeft(127).negate(), BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(64).negate(), BigInteger.ONE.shiftLeft(63).negate(),
                BigInteger.valueOf(-1), BigInteger.ZERO, BigInteger.ONE.shiftLeft(63),
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), BigInteger.ONE.shiftLeft(64)
        };
        for (BigInteger value : values) assertSigned128(value);
        Random random = new Random(20261018);
        for (int i = 0; i < 2000; i++) {
            BigInteger value = new BigInteger(127, random);
            assertSigned128(random.nextBoolean() ? value : value.negate());
        }
    }

    @Test
    void allU64HighBitsAndSignedTimestampsArePreserved() {
        byte[] data = Base58.decode(BUY_SAMPLE);
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(16, -1L); // timestamp i64。
        b.putLong(24, -1L); // baseAmountOut u64。
        b.putLong(56, Long.MIN_VALUE); // poolBaseTokenReserves u64。
        b.putLong(393, Long.MIN_VALUE); // lastUpdateTimestamp i64。
        var event = PumpAmmLogParser.decodeBuyEventFull(data);
        assertEquals(-1L, event.timestamp());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), event.baseAmountOut());
        assertEquals(BigInteger.ONE.shiftLeft(63), event.poolBaseTokenReserves());
        assertEquals(Long.MIN_VALUE, event.lastUpdateTimestamp());
    }

    @Test
    void variableUtf8InstructionNamesDoNotShiftFeeOrReserveFields() {
        byte[] original = Base58.decode(BUY_SAMPLE);
        for (String name : new String[]{"", "buy", "buy_exact_quote_in", "买入测试"}) {
            byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
            byte[] data = new byte[original.length - 3 + encoded.length];
            System.arraycopy(original, 0, data, 0, 409);
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(409, encoded.length);
            System.arraycopy(encoded, 0, data, 413, encoded.length);
            System.arraycopy(original, 416, data, 413 + encoded.length, original.length - 416);
            var event = PumpAmmLogParser.decodeBuyEventFull(data);
            assertEquals(name, event.ixName());
            assertEquals(new BigInteger("11843915707"), event.virtualQuoteReserves());
            assertEquals(new BigInteger("5395518684"), event.creatorFeeUnclaimed());
            assertTrue(event.canBoost());
        }
    }

    @Test
    void unrelatedEventsAndInstructionsReturnNull() {
        assertNull(PumpAmmLogParser.decodeEvent(null));
        assertNull(PumpAmmLogParser.decodeEvent(new byte[0]));
        assertNull(PumpAmmLogParser.decodeEvent(new byte[500]));
        byte[] data = Base58.decode(BUY_SAMPLE);
        data[8] ^= 1;
        assertNull(PumpAmmLogParser.decodeEvent(data));
        data = Base58.decode(BUY_SAMPLE);
        data[0] ^= 1;
        assertNull(PumpAmmLogParser.decodeEvent(data));
        assertNull(PumpAmmLogParser.decodeLog(null));
        assertNull(PumpAmmLogParser.decodeLog("Program log: Instruction: Buy"));
        assertThrows(IllegalArgumentException.class, () -> PumpAmmLogParser.decodeLog("Program data: !!!"));
    }

    @Test
    void everyRecognizedTruncationAndUnknownTailAreRejected() {
        byte[] buy = Base58.decode(BUY_SAMPLE);
        for (byte[] valid : new byte[][]{buy, Arrays.copyOfRange(buy, 8, buy.length),
                sellData(false, BigInteger.ZERO), sellData(true, BigInteger.ZERO)}) {
            boolean cpi = valid[0] == CPI[0];
            int header = cpi ? 16 : 8;
            for (int cut = 0; cut < valid.length; cut++) {
                byte[] truncated = Arrays.copyOf(valid, cut);
                if (cut < header) assertNull(PumpAmmLogParser.decodeEvent(truncated));
                else assertThrows(IllegalArgumentException.class, () -> PumpAmmLogParser.decodeEvent(truncated), "cut=" + cut);
            }
            assertThrows(IllegalArgumentException.class,
                    () -> PumpAmmLogParser.decodeEvent(Arrays.copyOf(valid, valid.length + 1)));
        }
    }

    @Test
    void invalidBooleanAndStringLengthsCannotBeSilentlyAccepted() {
        for (int offset : new int[]{368, 464}) { // CPI Buy的trackVolume/canBoost。
            for (int invalid : new int[]{2, 127, 255}) {
                byte[] data = Base58.decode(BUY_SAMPLE);
                data[offset] = (byte) invalid;
                assertThrows(IllegalArgumentException.class, () -> PumpAmmLogParser.decodeBuyEventFull(data));
            }
        }
        byte[] sell = sellData(false, BigInteger.ZERO);
        sell[408] = 2;
        assertThrows(IllegalArgumentException.class, () -> PumpAmmLogParser.decodeSellEventFull(sell));
        for (int invalid : new int[]{-1, Integer.MAX_VALUE, 1000}) {
            byte[] data = Base58.decode(BUY_SAMPLE);
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(409, invalid);
            assertThrows(IllegalArgumentException.class, () -> PumpAmmLogParser.decodeBuyEventFull(data));
        }
    }

    @Test
    void discriminatorsMatchAnchorEventNames() throws Exception {
        var sha = MessageDigest.getInstance("SHA-256");
        assertArrayEquals(BUY, Arrays.copyOf(sha.digest("event:BuyEvent".getBytes(StandardCharsets.UTF_8)), 8));
        assertArrayEquals(SELL, Arrays.copyOf(sha.digest("event:SellEvent".getBytes(StandardCharsets.UTF_8)), 8));
    }

    /** 用独立的BigInteger位移编码校验解码器的两段long优化，覆盖正负i128及其边界。 */
    private static void assertSigned128(BigInteger value) {
        byte[] buy = Base58.decode(BUY_SAMPLE);
        writeI128(buy, 448, value);
        var buyEvent = PumpAmmLogParser.decodeBuyEventFull(buy);
        assertEquals(value, buyEvent.virtualQuoteReserves());
        assertTrue(buyEvent.canBoost());
        assertEquals(new BigInteger("906642114599001"), buyEvent.baseSupply());
        var sellEvent = PumpAmmLogParser.decodeSellEventFull(sellData(false, value));
        assertEquals(value, sellEvent.virtualQuoteReserves());
        assertTrue(sellEvent.canBoost());
        assertEquals(BigInteger.valueOf(14), sellEvent.creatorFeeUnclaimed());
    }

    private static void writeI128(byte[] data, int offset, BigInteger value) {
        for (int i = 0; i < 16; i++) data[offset + i] = value.shiftRight(8 * i).byteValue();
    }

    /** SellEvent没有动态字符串；按官方独立字段序列合成441字节直接事件。 */
    private static byte[] sellData(boolean cpi, BigInteger virtualQuote) {
        ByteBuffer b = ByteBuffer.allocate(441).order(ByteOrder.LITTLE_ENDIAN);
        b.put(SELL).putLong(-17L);
        for (int i = 0; i < 13; i++) b.putLong(i == 0 ? -1L : 10L + i);
        for (int i = 1; i <= 7; i++) {
            byte[] key = new byte[32];
            Arrays.fill(key, (byte) i);
            b.put(key);
        }
        b.putLong(300).putLong(301);
        for (int i = 0; i < 4; i++) b.putLong(400L + i);
        writeI128(b.array(), b.position(), virtualQuote);
        b.position(b.position() + 16);
        b.put((byte)1);
        b.putLong(11).putLong(12).putLong(13).putLong(14);
        assertEquals(b.capacity(), b.position());
        if (!cpi) return b.array();
        byte[] data = new byte[449];
        System.arraycopy(CPI, 0, data, 0, 8);
        System.arraycopy(b.array(), 0, data, 8, 441);
        return data;
    }

    private static String key(int value) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte)value);
        return Base58.encode(key);
    }
}
