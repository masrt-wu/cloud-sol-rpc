package org.p2p.solanaj.programs.parse;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.router.LaunchLabLegs;
import org.p2p.solanaj.router.MeteoraLegs;
import org.p2p.solanaj.router.OrcaLegs;
import org.p2p.solanaj.router.PumpAmmLegs;
import org.p2p.solanaj.router.RaydiumLegs;
import org.p2p.solanaj.router.pda.MeteoraDammV2PoolParser;
import org.p2p.solanaj.router.pda.MeteoraDlmmPda;
import org.p2p.solanaj.router.pda.MeteoraDlmmPoolParser;
import org.p2p.solanaj.router.pda.OrcaWhirlpoolPoolParser;
import org.p2p.solanaj.router.pda.PumpAmmPoolParser;
import org.p2p.solanaj.router.pda.ray.LaunchLabPoolStateParser;
import org.p2p.solanaj.router.pda.ray.LaunchLabRouterPoolParser;
import org.p2p.solanaj.router.pda.ray.RaydiumAmmV4PoolParser;
import org.p2p.solanaj.router.pda.ray.RaydiumClmmPoolParser;
import org.p2p.solanaj.router.pda.ray.RaydiumCpmmPoolParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 仅使用构造的公开账户字节验证多路由快速池转换；不请求 RPC、不签名。 */
class RouterFastPoolParsersTest {
    /** Pump 的 fee 账户不在 Pool 中，新旧布局都应保留调用方传入值。 */
    @Test void pumpAmmPool() throws Exception {
        byte[] bytes = anchor("Pool", 271);
        putKey(bytes, 43, 1); putKey(bytes, 75, 2);
        putKey(bytes, 139, 3); putKey(bytes, 171, 4); putKey(bytes, 211, 5);
        bytes[244] = 1;
        PumpAmmLegs.Pool pool = PumpAmmPoolParser.parse(key(20), bytes,
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022,
                key(6), key(7), key(8), key(9));
        assertEquals(key(20), pool.pool());
        assertEquals(key(1), pool.baseMint());
        assertEquals(key(2), pool.quoteMint());
        assertEquals(key(3), pool.baseVault());
        assertEquals(key(4), pool.quoteVault());
        assertEquals(key(5), pool.coinCreator());
        assertEquals(key(6), pool.protocolFeeRecipient());
        assertEquals(key(7), pool.coinCreatorVaultAta());
        assertEquals(key(8), pool.coinCreatorVaultAuthority());
        assertEquals(key(9), pool.buybackFeeRecipient());
        assertTrue(pool.cashbackCoin());
        DexAccountData pumpState = DexParsers.parsePool(PumpAmmLegs.PROGRAM, bytes);
        assertEquals(pool, PumpAmmPoolParser.fromDexAccountData(key(20), pumpState,
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022,
                key(6), key(7), key(8), key(9)));

        PumpAmmLegs.Pool old = PumpAmmPoolParser.parse(key(20), Arrays.copyOf(bytes, 211),
                TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID,
                key(6), key(7), key(8), key(9));
        assertNull(old.coinCreator());
        assertFalse(old.cashbackCoin());
        DexAccountData oldState = DexParsers.parsePool(PumpAmmLegs.PROGRAM, Arrays.copyOf(bytes, 211));
        assertEquals(old, PumpAmmPoolParser.fromDexAccountData(key(20), oldState,
                TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID,
                key(6), key(7), key(8), key(9)));
        bytes[0] = 0; // 可信热路径不重复校验判别符。
        assertEquals(key(1), PumpAmmPoolParser.parse(key(20), bytes,
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022,
                key(6), key(7), key(8), key(9)).baseMint());
        assertThrows(IndexOutOfBoundsException.class, () -> PumpAmmPoolParser.parse(
                key(20), new byte[202], TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID,
                key(6), key(7), key(8), key(9)));
    }

    /** AMM V4 从池本身读取两侧 mint/vault，不从 market 重新推导。 */
    @Test void raydiumAmmV4Pool() {
        byte[] bytes = new byte[752];
        putKey(bytes, 336, 1); putKey(bytes, 368, 2);
        putKey(bytes, 400, 3); putKey(bytes, 432, 4);
        RaydiumLegs.AmmV4 pool = RaydiumAmmV4PoolParser.parse(key(20), bytes);
        assertEquals(key(20), pool.amm());
        assertEquals(key(3), pool.coinMint());
        assertEquals(key(4), pool.pcMint());
        assertEquals(key(1), pool.coinVault());
        assertEquals(key(2), pool.pcVault());
        assertEquals(TokenProgram.PROGRAM_ID, pool.tokenProgram());
        assertEquals(pool, RaydiumAmmV4PoolParser.fromDexAccountData(key(20),
                DexParsers.parsePool(RaydiumLegs.AMM_V4, bytes)));
    }

    /** CPMM Pool 可以是随机 signer 地址，不用标准 PDA 替换。 */
    @Test void raydiumCpmmPool() throws Exception {
        byte[] bytes = anchor("PoolState", 637);
        putKey(bytes, 8, 1); putKey(bytes, 72, 2); putKey(bytes, 104, 3);
        putKey(bytes, 168, 4); putKey(bytes, 200, 5);
        putKey(bytes, 232, TokenProgram.PROGRAM_ID);
        putKey(bytes, 264, TokenProgram.TOKEN_2022);
        putKey(bytes, 296, 6);
        RaydiumLegs.Cpmm pool = RaydiumCpmmPoolParser.parse(key(20), bytes);
        assertEquals(key(1), pool.config());
        assertEquals(key(20), pool.pool());
        assertEquals(key(4), pool.baseMint());
        assertEquals(key(5), pool.quoteMint());
        assertEquals(key(2), pool.baseVault());
        assertEquals(key(3), pool.quoteVault());
        assertEquals(TokenProgram.PROGRAM_ID, pool.baseTokenProgram());
        assertEquals(TokenProgram.TOKEN_2022, pool.quoteTokenProgram());
        assertEquals(key(6), pool.observation());
        assertEquals(pool, RaydiumCpmmPoolParser.fromDexAccountData(key(20),
                DexParsers.parsePool(RaydiumLegs.CPMM, bytes)));
    }

    /** CLMM 的可选扩展与 tick 数组由调用方提供，负 tick 保留符号。 */
    @Test void raydiumClmmPool() throws Exception {
        byte[] bytes = anchor("PoolState", 1544);
        putKey(bytes, 9, 1); putKey(bytes, 73, 2); putKey(bytes, 105, 3);
        putKey(bytes, 137, 4); putKey(bytes, 169, 5); putKey(bytes, 201, 6);
        put(bytes, 235, 64, 2); put(bytes, 269, -42026, 4);
        List<PublicKey> ticks = List.of(key(30), key(31));
        RaydiumLegs.Clmm pool = RaydiumClmmPoolParser.parse(key(20), bytes,
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022, key(29), ticks);
        assertEquals(key(1), pool.config());
        assertEquals(key(20), pool.pool());
        assertEquals(key(2), pool.token0Mint());
        assertEquals(key(3), pool.token1Mint());
        assertEquals(key(4), pool.token0Vault());
        assertEquals(key(5), pool.token1Vault());
        assertEquals(key(6), pool.observation());
        assertEquals(TokenProgram.TOKEN_2022, pool.token1Program());
        assertEquals(key(29), pool.bitmapExtension());
        assertEquals(ticks, pool.tickArrays());
        assertEquals(-42026, RaydiumClmmPoolParser.tickCurrent(bytes));
        assertEquals(64, RaydiumClmmPoolParser.tickSpacing(bytes));
        assertEquals(pool, RaydiumClmmPoolParser.fromDexAccountData(key(20),
                DexParsers.parsePool(RaydiumLegs.CLMM, bytes),
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022, key(29), ticks));
    }

    /** DLMM 从最新默认位图直接找有序 BinArray，负 ID 向下取整。 */
    @Test void meteoraDlmmPoolAndBitmap() throws Exception {
        byte[] bytes = anchor("LbPair", 904);
        putKey(bytes, 88, 1); putKey(bytes, 120, 2);
        putKey(bytes, 152, 3); putKey(bytes, 184, 4); putKey(bytes, 552, 5);
        put(bytes, 76, -71, 4);
        bytes[647] = (byte) ((1 << 6) | (1 << 5)); // -2、-3 两个数组已初始化。
        bytes[880] = 1; bytes[881] = 0;
        List<PublicKey> bins = MeteoraDlmmPoolParser.defaultBinArrays(key(20), bytes, true, 3);
        assertEquals(List.of(MeteoraDlmmPda.binArray(key(20), -2),
                MeteoraDlmmPda.binArray(key(20), -3)), bins);
        MeteoraLegs.Dlmm pool = MeteoraDlmmPoolParser.parse(key(20), bytes, key(29), bins);
        assertEquals(key(20), pool.lbPair());
        assertEquals(key(3), pool.reserveX());
        assertEquals(key(4), pool.reserveY());
        assertEquals(key(1), pool.mintX());
        assertEquals(key(2), pool.mintY());
        assertEquals(key(5), pool.oracle());
        assertEquals(TokenProgram.TOKEN_2022, pool.tokenProgramX());
        assertEquals(TokenProgram.PROGRAM_ID, pool.tokenProgramY());
        assertEquals(key(29), pool.bitmapExtension());
        assertEquals(bins, pool.binArrays());
        assertEquals(-71, MeteoraDlmmPoolParser.activeId(bytes));
        DexAccountData dlmmState = DexParsers.parsePool(MeteoraLegs.DLMM, bytes);
        assertEquals(bins, MeteoraDlmmPoolParser.defaultBinArrays(key(20), dlmmState, true, 3));
        assertEquals(pool, MeteoraDlmmPoolParser.fromDexAccountData(key(20),
                dlmmState, key(29), bins));
    }

    /** DAMM v2 的 Token Program 标记与调用方提供的可选账户都原样进入路由对象。 */
    @Test void meteoraDammV2Pool() throws Exception {
        byte[] bytes = anchor("Pool", 1112);
        putKey(bytes, 168, 1); putKey(bytes, 200, 2);
        putKey(bytes, 232, 3); putKey(bytes, 264, 4);
        bytes[482] = 1; bytes[483] = 0;
        MeteoraLegs.DammV2 pool = MeteoraDammV2PoolParser.parse(key(20), bytes, key(30), true);
        assertEquals(key(20), pool.pool());
        assertEquals(key(1), pool.mintA());
        assertEquals(key(2), pool.mintB());
        assertEquals(key(3), pool.vaultA());
        assertEquals(key(4), pool.vaultB());
        assertEquals(TokenProgram.TOKEN_2022, pool.tokenProgramA());
        assertEquals(TokenProgram.PROGRAM_ID, pool.tokenProgramB());
        assertEquals(key(30), pool.referralTokenAccount());
        assertTrue(pool.includeRateLimiterSysvar());
        assertEquals(pool, MeteoraDammV2PoolParser.fromDexAccountData(key(20),
                DexParsers.parsePool(MeteoraLegs.DAMM_V2, bytes), key(30), true));
    }

    /** Orca Pool 中保存的是实际 vault，不能编造 vault PDA；tick 数组是外部实时输入。 */
    @Test void orcaWhirlpoolPool() throws Exception {
        byte[] bytes = anchor("Whirlpool", 653);
        putKey(bytes, 101, 1); putKey(bytes, 133, 2);
        putKey(bytes, 181, 3); putKey(bytes, 213, 4);
        put(bytes, 41, 64, 2); put(bytes, 81, -42026, 4);
        List<PublicKey> ticks = List.of(key(30), key(31), key(32));
        OrcaLegs.Pool pool = OrcaWhirlpoolPoolParser.parse(key(20), bytes,
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022, ticks);
        assertEquals(key(20), pool.whirlpool());
        assertEquals(key(1), pool.mintA());
        assertEquals(key(3), pool.mintB());
        assertEquals(key(2), pool.vaultA());
        assertEquals(key(4), pool.vaultB());
        assertEquals(TokenProgram.TOKEN_2022, pool.tokenProgramB());
        assertEquals(ticks, pool.tickArrays());
        assertEquals(-42026, OrcaWhirlpoolPoolParser.tickCurrent(bytes));
        assertEquals(64, OrcaWhirlpoolPoolParser.tickSpacing(bytes));
        assertEquals(pool, OrcaWhirlpoolPoolParser.fromDexAccountData(key(20),
                DexParsers.parsePool(OrcaLegs.WHIRLPOOL, bytes),
                TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022, ticks));
    }

    /** LaunchLab 沿用现有完整解析器做对照，热路径只读取建单需要的地址。 */
    @Test void raydiumLaunchLabPool() throws Exception {
        byte[] bytes = anchor("PoolState", 429);
        putKey(bytes, 141, 1); putKey(bytes, 173, 2);
        putKey(bytes, 205, 3); putKey(bytes, 237, 4);
        putKey(bytes, 269, 5); putKey(bytes, 301, 6);
        bytes[365] = 3;
        LaunchLabLegs.Pool pool = LaunchLabRouterPoolParser.parse(key(20), bytes, key(30), key(31));
        assertEquals(key(1), pool.globalConfig());
        assertEquals(key(2), pool.platformConfig());
        assertEquals(key(3), pool.baseMint());
        assertEquals(key(4), pool.quoteMint());
        assertEquals(key(5), pool.baseVault());
        assertEquals(key(6), pool.quoteVault());
        assertEquals(TokenProgram.TOKEN_2022, pool.baseTokenProgram());
        assertEquals(TokenProgram.TOKEN_2022, pool.quoteTokenProgram());
        assertEquals(key(20), pool.poolState());
        assertEquals(key(30), pool.platformFeeVault());
        assertEquals(key(31), pool.creatorFeeVault());
        assertEquals(pool.baseMint(), LaunchLabPoolStateParser.parse(bytes).baseMint());
        DexAccountData state = DexParsers.parsePool(LaunchLabLegs.PROGRAM, bytes);
        assertEquals(429, state.consumedBytes());
        assertEquals(pool, LaunchLabRouterPoolParser.fromDexAccountData(
                key(20), state, key(30), key(31)));
    }

    /** 使用独立构造的 Anchor 判别符，避免生产解析器制造自己的测试输入。 */
    private static byte[] anchor(String type, int length) throws Exception {
        byte[] bytes = new byte[length];
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("account:" + type).getBytes(StandardCharsets.US_ASCII));
        System.arraycopy(hash, 0, bytes, 0, 8);
        return bytes;
    }

    /** 测试公钥均为公开的 32 字节值，不包含钱包或私钥。 */
    private static PublicKey key(int marker) {
        byte[] bytes = new byte[32]; bytes[0] = (byte) marker;
        return new PublicKey(bytes);
    }

    /** 向独立测试账户写入公钥。 */
    private static void putKey(byte[] data, int offset, int marker) {
        putKey(data, offset, key(marker));
    }

    /** 向独立测试账户写入公钥。 */
    private static void putKey(byte[] data, int offset, PublicKey key) {
        System.arraycopy(key.toByteArray(), 0, data, offset, 32);
    }

    /** 按小端二补码编码测试 tick、bin 和间距。 */
    private static void put(byte[] data, int offset, int value, int width) {
        for (int i = 0; i < width; i++) data[offset + i] = (byte) (value >>> (8 * i));
    }
}
