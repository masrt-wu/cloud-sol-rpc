package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.programs.parse.DexParsers;
import org.p2p.solanaj.router.pda.MeteoraDlmmPoolParser;
import org.p2p.solanaj.router.pda.MeteoraDlmmLbPairParser;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 用真实 LbPair 字节布局构造 DexAccountData，离线验证双向 BinArray 推导。 */
class MeteoraDlmmDexAccountDataTest {
    /** LbPair 池账户地址。 */
    private static final PublicKey POOL = key("zxTpi4BtaWX3mgdAPoezkMD1hxx8CdeCfrqXMWvSCLX");
    /** 池 X 侧代币。 */
    private static final PublicKey MINT_X = key("6GmAFSYs4gk3FDao5FzzySQpPZaWsa4rUJHacpMpUNgx");
    /** 池 Y 侧 WSOL。 */
    private static final PublicKey MINT_Y = key("So11111111111111111111111111111111111111112");
    /** 用户提供的 LbPair 内置位图第 7 个 u64。 */
    private static final long BITMAP_WORD = 68719476734L;

    /** 同一池和索引复用 BinArray PDA，不同索引仍得到不同地址。 */
    @Test
    void cachesBinArrayByPoolAndIndex() {
        PublicKey first = MeteoraDlmmLbPairParser.deriveBinArray(POOL, -45);
        byte[] expected = first.toByteArray().clone();
        first.toByteArray()[0] ^= 1;
        assertArrayEquals(expected, MeteoraDlmmLbPairParser.deriveBinArray(POOL, -45).toByteArray());
        assertNotEquals(MeteoraDlmmLbPairParser.deriveBinArray(POOL, -45),
                MeteoraDlmmLbPairParser.deriveBinArray(POOL, -44));
    }

    /** WSOL 输入时，自动选择 Y→X 并从活跃数组向较大索引查找。 */
    @Test
    void derivesYToXBinArraysFromDexAccountData() throws Exception {
        DexAccountData state = state(BITMAP_WORD);
        assertEquals(-3122, state.intValue("active_id"));
        assertEquals(BigInteger.valueOf(BITMAP_WORD), state.integer("bin_array_bitmap[7]"));
        List<PublicKey> arrays = MeteoraDlmmPoolParser.binArraysForSwap(POOL, state, MINT_Y, 4);
        assertEquals(List.of(
                key("F2NyEUGbzaHJuts6UsghpjWVo6XDRUC7YrW4XssGhDmU"),
                key("3iV4XPjhZMcdsmUTdKZApkNYGYi88vaxTfUjQphTTHAs"),
                key("AgaFMkcRmTHtyBD6817T3EWvvtvc3gib8Fz22EQHjXhR"),
                key("5dNV3HKrox2aQnk3GWn6gCsTGx9FNtpAJme9Zwuo9wjT")), arrays);
    }

    /** X 侧代币输入时，自动选择 X→Y 并向较小索引查找。 */
    @Test
    void derivesXToYBinArraysFromDexAccountData() throws Exception {
        List<PublicKey> arrays = MeteoraDlmmPoolParser.binArraysForSwap(
                POOL, state(BITMAP_WORD), MINT_X, 4);
        assertEquals(List.of(
                key("F2NyEUGbzaHJuts6UsghpjWVo6XDRUC7YrW4XssGhDmU"),
                key("7hgnWazWcpDP7jpE6S1bnnwKKs3M9nYDB5D6Drvjka3q"),
                key("24sUbHgmkDqPvDTAc1PMaQ7BSWLYyLY9BkjpsJGEddSP"),
                key("BaNVW1TwXr4hVzycDhNJjs8EL4kodyrfpQkRVviM3gdC")), arrays);
    }

    /** 输入 mint 不属于池、位图为空或数量非法时，应在构造交易前失败。 */
    @Test
    void rejectsWrongMintOrEmptyBitmap() throws Exception {
        DexAccountData valid = state(BITMAP_WORD);
        DexAccountData empty = state(0);
        assertThrows(IllegalArgumentException.class, () ->
                MeteoraDlmmPoolParser.binArraysForSwap(POOL, valid,
                        key("11111111111111111111111111111111"), 4));
        assertThrows(IllegalArgumentException.class, () ->
                MeteoraDlmmPoolParser.binArraysForSwap(POOL, empty, MINT_Y, 4));
        assertThrows(IllegalArgumentException.class, () ->
                MeteoraDlmmPoolParser.binArraysForSwap(POOL, valid, MINT_Y, 0));
    }

    /**
     * 按账户真实偏移构造并解析测试池快照。
     * @param bitmapWord 内置位图第 7 个 u64
     * @return 已解析的 LbPair 对象
     */
    private static DexAccountData state(long bitmapWord) throws Exception {
        byte[] bytes = new byte[904];
        byte[] discriminator = MessageDigest.getInstance("SHA-256")
                .digest("account:LbPair".getBytes(StandardCharsets.US_ASCII));
        System.arraycopy(discriminator, 0, bytes, 0, 8);
        System.arraycopy(MINT_X.toByteArray(), 0, bytes, 88, 32);
        System.arraycopy(MINT_Y.toByteArray(), 0, bytes, 120, 32);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(76, -3122);
        buffer.putLong(584 + 7 * 8, bitmapWord);
        return DexParsers.parsePool(MeteoraLegs.DLMM, bytes);
    }

    /**
     * 把 Base58 文本转换成测试公钥。
     * @param address 公钥的 Base58 文本
     * @return 公钥对象
     */
    private static PublicKey key(String address) { return new PublicKey(address); }
}
