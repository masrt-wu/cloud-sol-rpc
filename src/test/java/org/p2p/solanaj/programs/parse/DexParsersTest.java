package org.p2p.solanaj.programs.parse;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 纯离线解析测试；不读取钱包、不使用 RPC、不签名或发送交易。 */
class DexParsersTest {
    /** u64 的最大无符号数，确保不能错误解析成负 long。 */
    private static final BigInteger U64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    /** u128 高位置一的样本。 */
    private static final BigInteger LARGE_U128 = BigInteger.ONE.shiftLeft(127).add(BigInteger.valueOf(123));
    /** 测试用公钥，不对应测试钱包或私钥。 */
    private static final PublicKey MINT_A = key(1);
    private static final PublicKey MINT_B = key(2);
    /** 测试用池地址。 */
    private static final PublicKey POOL = key(3);

    /** 验证统一入口分别识别九种池，不通过 mint 或相同判别符猜测协议。 */
    @Test
    void dispatchesAllNinePoolPrograms() {
        List<PublicKey> owners = List.of(PumpSwapParser.PROGRAM_ID, RaydiumAmmV4Parser.PROGRAM_ID,
                RaydiumCpmmParser.PROGRAM_ID, RaydiumClmmParser.PROGRAM_ID, MeteoraDlmmParser.PROGRAM_ID,
                MeteoraDammV1Parser.PROGRAM_ID, MeteoraDammV2Parser.PROGRAM_ID, OrcaWhirlpoolParser.PROGRAM_ID,
                RaydiumLaunchLabParser.PROGRAM_ID);
        List<byte[]> samples = List.of(pump(), ammV4(), cpmm(), clmm(), dlmm(), dammV1(), dammV2(), whirlpool(),
                anchor("PoolState", 429));
        List<String> types = List.of("Pool", "AmmInfo", "PoolState", "PoolState", "LbPair", "pool", "Pool", "Whirlpool", "PoolState");
        for (int i = 0; i < owners.size(); i++) {
            DexAccountData parsed = DexParsers.parsePool(owners.get(i), samples.get(i));
            //System.out.println(JSONObject.toJSONString(parsed));
            assertEquals(types.get(i), parsed.accountType());
            assertFalse(parsed.staticFields().isEmpty());
            assertFalse(parsed.dynamicFields().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> DexParsers.parsePool(DexPda.TOKEN_PROGRAM, pump()));
        assertThrows(IllegalArgumentException.class, () -> PumpSwapParser.parsePool(OrcaWhirlpoolParser.PROGRAM_ID, pump()));
    }

    /** 验证 Pump 追加字段的旧账户兼容和 i128 有符号储备。 */
    @Test void readsPumpSignedVirtualReservesAndLegacyTail() {
        byte[] data = pump();
        put(data, 203, U64_MAX, 8);
        put(data, 245, BigInteger.valueOf(-7), 16);
        DexAccountData parsed = PumpSwapParser.parsePool(data);
        assertEquals(U64_MAX, parsed.integer("lpSupply"));
        assertEquals(BigInteger.valueOf(-7), parsed.integer("virtual_quote_reserves"));
        assertEquals(MINT_A, parsed.publicKey("baseMint"));
        assertEquals(new DexReserves(BigInteger.TEN, BigInteger.valueOf(93)),
                PumpSwapParser.effectiveReserves(parsed, BigInteger.TEN, BigInteger.valueOf(100)));
        DexAccountData legacy = PumpSwapParser.parsePool(Arrays.copyOf(data, 211));
        assertEquals(BigInteger.ZERO, legacy.integer("virtual_quote_reserves"));
        assertTrue(legacy.defaultedFields().contains("virtual_quote_reserves"));
        assertThrows(IllegalArgumentException.class, () -> PumpSwapParser.parsePool(Arrays.copyOf(data, 250)));
    }

    /** 验证 Pump 全局配置和旧版本缺失字段的兼容，不把变更中的费率当成静态数据。 */
    @Test void readsPumpGlobalConfigAndRecipients() {
        byte[] data = anchor("GlobalConfig", 949);
        put(data, 40, BigInteger.valueOf(20), 8);
        putKey(data, 57, MINT_A);
        put(data, 899, BigInteger.valueOf(30), 8);
        DexAccountData parsed = PumpSwapParser.parseGlobalConfig(data);
        assertEquals(BigInteger.valueOf(20), parsed.integer("lp_fee_basis_points"));
        assertEquals(BigInteger.valueOf(30), parsed.integer("buyback_basis_points"));
        assertEquals(MINT_A, parsed.list("protocol_fee_recipients").get(0));
        assertEquals(BigInteger.ZERO, PumpSwapParser.parseGlobalConfig(Arrays.copyOf(data, 313))
                .integer("coin_creator_fee_basis_points"));
    }

    /** 对照官方 Pump README 的真实公开池地址，验证池、LP 和 WSOL vault 推导。 */
    @Test void matchesPumpPublishedPdaVectors() {
        PublicKey pool = PumpSwapParser.derivePool(0,
                new PublicKey("9XDYTfQKwW8sHPqnFdUreMmtmffmkHVPGTNV2e3LKxNW"),
                new PublicKey("7LSsEoJGhLeZzGvDofTdNg7M3JttxQqGWNLo6vWMpump"), wsol());
        assertEquals("GseMAnNDvntR5uFePZ51yZBXzNSn7GdFPkfHwfr6d77J", pool.toBase58());
        assertEquals("6dpnPD6UWDw5hbJEuPQwnCCMba1JYwHANKuL6GQ6otAH", PumpSwapParser.deriveLpMint(pool).toBase58());
        assertEquals("43DVcZR4kQFjh4Xm2i3DcneRxNjZp7HMud8yDrJWrDr8",
                PumpSwapParser.deriveVault(pool, wsol(), DexPda.TOKEN_PROGRAM).toBase58());
    }

    /** 验证 AMM V4 的 u128 累计量及旧订单簿储备公式。 */
    @Test void readsV4OpenOrdersAndPnl() {
        byte[] data = ammV4();
        put(data, 256, LARGE_U128, 16);
        put(data, 192, BigInteger.TEN, 8);
        put(data, 200, BigInteger.valueOf(20), 8);
        DexAccountData pool = RaydiumAmmV4Parser.parsePool(data);
        assertEquals(LARGE_U128, pool.integer("swap_base_in_amount"));
        assertEquals(752, pool.consumedBytes());
        byte[] orders = new byte[3228];
        envelope(orders);
        put(orders, 5, BigInteger.valueOf(5), 8);
        putKey(orders, 13, POOL);
        put(orders, 85, BigInteger.valueOf(100), 8);
        put(orders, 101, BigInteger.valueOf(200), 8);
        DexAccountData openOrders = RaydiumAmmV4Parser.parseOpenOrders(orders);
        assertEquals(new DexReserves(BigInteger.valueOf(1090), BigInteger.valueOf(2180)),
                RaydiumAmmV4Parser.effectiveReserves(pool, BigInteger.valueOf(1000), BigInteger.valueOf(2000), openOrders));
        assertEquals("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1", RaydiumAmmV4Parser.deriveAuthority().toBase58());
        assertThrows(IllegalArgumentException.class, () -> RaydiumAmmV4Parser.parseOpenOrders(new byte[3228]));
    }

    /** 验证 OpenBook/Serum v1 market 布局，不使用错误的 AMM owner 校验市场账户。 */
    @Test void readsLegacyMarketAndValidatesMarketOwner() {
        byte[] data = new byte[388];
        envelope(data);
        put(data, 5, BigInteger.valueOf(3), 8);
        putKey(data, 53, MINT_A);
        putKey(data, 85, MINT_B);
        DexAccountData market = RaydiumAmmV4Parser.parseMarket(MINT_A, MINT_A, data);
        assertEquals(MINT_A, market.publicKey("baseMint"));
        assertEquals(MINT_B, market.publicKey("quoteMint"));
        assertThrows(IllegalArgumentException.class, () -> RaydiumAmmV4Parser.parseMarket(MINT_B, MINT_A, data));
    }

    /** 验证 CPMM 新 creator fee 字段位置和三类费用扣除。 */
    @Test void readsCpmmCreatorFeesAndUnsignedSupply() {
        byte[] data = cpmm();
        put(data, 333, U64_MAX, 8);
        put(data, 341, BigInteger.valueOf(11), 8);
        put(data, 357, BigInteger.valueOf(13), 8);
        put(data, 397, BigInteger.valueOf(17), 8);
        DexAccountData parsed = RaydiumCpmmParser.parsePool(data);
        assertEquals(U64_MAX, parsed.integer("lp_supply"));
        assertEquals(MINT_A, parsed.publicKey("token0Mint"));
        assertEquals(DexPda.TOKEN_2022_PROGRAM, parsed.publicKey("token_1_program"));
        assertEquals(637, parsed.consumedBytes());
        assertEquals(BigInteger.valueOf(959), RaydiumCpmmParser.effectiveReserves(parsed,
                BigInteger.valueOf(1000), BigInteger.valueOf(2000)).first());
        byte[] config = anchor("AmmConfig", 236);
        put(config, 108, BigInteger.valueOf(77), 8);
        assertEquals(BigInteger.valueOf(77), RaydiumCpmmParser.parseAmmConfig(config).integer("creator_fee_rate"));
    }

    /** 验证 CLMM 价格、负 tick、默认位图和新动态费字段的偏移。 */
    @Test void readsClmmDynamicFeeAndTickArray() {
        byte[] data = clmm();
        put(data, 253, LARGE_U128, 16);
        put(data, 269, BigInteger.valueOf(-42026), 4);
        put(data, 1118, BigInteger.valueOf(222), 4);
        DexAccountData parsed = RaydiumClmmParser.parsePool(data);
        assertEquals(LARGE_U128, parsed.integer("sqrtPriceX64"));
        assertEquals(-42026, parsed.intValue("tick_current"));
        assertEquals(BigInteger.valueOf(222), parsed.integer("dynamic_fee_info.volatility_accumulator"));
        assertEquals(1544, parsed.consumedBytes());
        assertFalse(parsed.staticFields().containsKey("sqrt_price_x64"));
        byte[] ticks = anchor("TickArrayState", 10240);
        putKey(ticks, 8, POOL);
        put(ticks, 40, BigInteger.valueOf(-42240), 4);
        put(ticks, 48, BigInteger.valueOf(-5), 16);
        assertEquals(BigInteger.valueOf(-5), RaydiumClmmParser.parseTickArray(ticks).integer("ticks[0].liquidity_net"));
    }

    /** 验证 DLMM 当前 bin、程序标记及 BinArray 的余额和有符号索引。 */
    @Test void readsDlmmBinArrayAndTokenPrograms() {
        byte[] data = dlmm();
        put(data, 76, BigInteger.valueOf(-71), 4);
        DexAccountData parsed = MeteoraDlmmParser.parsePool(data);
        assertEquals(-71, parsed.intValue("activeId"));
        assertEquals(DexPda.TOKEN_2022_PROGRAM, MeteoraDlmmParser.tokenProgramX(parsed));
        assertEquals(DexPda.TOKEN_PROGRAM, MeteoraDlmmParser.tokenProgramY(parsed));
        assertEquals(904, parsed.consumedBytes());
        byte[] bins = anchor("BinArray", 10136);
        put(bins, 8, BigInteger.valueOf(-2), 8);
        putKey(bins, 24, POOL);
        put(bins, 56, U64_MAX, 8);
        put(bins, 88, LARGE_U128, 16);
        DexAccountData array = MeteoraDlmmParser.parseBinArray(bins);
        assertEquals(BigInteger.valueOf(-2), array.integer("index"));
        assertEquals(U64_MAX, array.integer("bins[0].amount_x"));
        assertEquals(LARGE_U128, array.integer("bins[0].liquidity_supply"));
        assertEquals(70, array.list("bins").size());
    }

    /** 对照之前公开交易里的 DLMM 账户，确认 reserve 与 Oracle 种子。 */
    @Test void matchesDlmmKnownAccounts() {
        PublicKey pool = new PublicKey("2ZWxT3niYjyudmDMDVar9ajNE42RkwYdzZBh6TiMuKQY");
        PublicKey quote = new PublicKey("oPAiAikWTaFj9RYoRFD35ccfwhnMcB3ThgBZRHSkjTZ");
        PublicKey usdc = new PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        assertEquals("DUWECDt8osW6K79wEFVPPPDW4ZUrtQZxzFXUCq8mnGk8", MeteoraDlmmParser.deriveReserve(pool, quote).toBase58());
        assertEquals("Ff1ZPrgVnD82SskVsyKEhMnwNCLaEuszZmCtizyzzrCh", MeteoraDlmmParser.deriveReserve(pool, usdc).toBase58());
        assertEquals("EYNPuGybpDgfZrgnMT12A3Bmrs3yLFDGKmJhSd7uUgRK", MeteoraDlmmParser.deriveOracle(pool).toBase58());
    }

    /** 验证 DAMM v1 枚举和 Dynamic Vault 的锁定利润、底层份额。 */
    @Test void readsDammV1AndVaultShares() {
        DexAccountData pool = MeteoraDammV1Parser.parsePool(dammV1());
        assertEquals(MINT_A, pool.publicKey("token_a_mint"));
        assertEquals("ConstantProduct", ((DexAccountData.EnumValue) pool.get("curveType")).variant());
        byte[] vaultData = anchor("Vault", 1227);
        put(vaultData, 11, BigInteger.valueOf(10000), 8);
        put(vaultData, 1203, BigInteger.valueOf(5000), 8);
        put(vaultData, 1211, BigInteger.valueOf(1000), 8);
        put(vaultData, 1219, BigInteger.valueOf(5_000_000_000L), 8);
        DexAccountData vault = MeteoraDammV1Parser.parseDynamicVault(MeteoraDammV1Parser.VAULT_PROGRAM_ID, vaultData);
        assertEquals(BigInteger.valueOf(7500), MeteoraDammV1Parser.unlockedVaultAmount(vault, 1100));
        assertEquals(BigInteger.valueOf(750), MeteoraDammV1Parser.poolUnderlyingAmount(vault,
                BigInteger.valueOf(100), BigInteger.valueOf(1000), 1100));
        assertEquals(BigInteger.valueOf(10000), MeteoraDammV1Parser.unlockedVaultAmount(vault, 1200));
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV1Parser.unlockedVaultAmount(vault, 999));
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV1Parser.parseDynamicVault(MeteoraDammV1Parser.PROGRAM_ID, vaultData));
    }

    /** 验证 DAMM v1 稳定曲线的命名枚举参数和嵌套 depeg 类型。 */
    @Test void readsDammV1StableCurveParameters() {
        byte[] data = anchor("Pool", 944); data[874] = 1;
        put(data, 875, BigInteger.valueOf(100), 8);
        put(data, 883, BigInteger.valueOf(1000), 8);
        data[916] = 2;
        DexAccountData parsed = MeteoraDammV1Parser.parsePool(data);
        DexAccountData.EnumValue curve = (DexAccountData.EnumValue) parsed.get("curve_type");
        assertEquals("Stable", curve.variant());
        Map<?, ?> parameters = (Map<?, ?>) curve.fields().get(0);
        assertEquals(BigInteger.valueOf(100), parameters.get("amp"));
        assertEquals(BigInteger.valueOf(1000), ((Map<?, ?>) parameters.get("token_multiplier")).get("tokenAMultiplier"));
        DexAccountData.EnumValue depegType = (DexAccountData.EnumValue)
                ((Map<?, ?>) parameters.get("depeg")).get("depegType");
        assertEquals("Lido", depegType.variant());
        assertEquals(944, parsed.consumedBytes());
        data[916] = 9;
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV1Parser.parsePool(data));
    }

    /** 验证辅助账户的判别符与固定布局大小，不只验证主 Pool。 */
    @Test void readsAuxiliaryConfigBitmapAndObservationAccounts() {
        assertEquals(117, RaydiumClmmParser.parseAmmConfig(anchor("AmmConfig", 117)).consumedBytes());
        assertEquals(88, RaydiumClmmParser.parseDynamicFeeConfig(anchor("DynamicFeeConfig", 88)).consumedBytes());
        assertEquals(1832, RaydiumClmmParser.parseBitmapExtension(anchor("TickArrayBitmapExtension", 1832)).consumedBytes());
        assertEquals(4483, RaydiumClmmParser.parseObservation(anchor("ObservationState", 4483)).consumedBytes());
        assertEquals(4075, RaydiumCpmmParser.parseObservation(anchor("ObservationState", 4075)).consumedBytes());
        assertEquals(1576, MeteoraDlmmParser.parseBitmapExtension(anchor("BinArrayBitmapExtension", 1576)).consumedBytes());
        assertEquals(36, MeteoraDlmmParser.parsePresetParameter(anchor("PresetParameter", 36)).consumedBytes());
        assertEquals(192, MeteoraDlmmParser.parsePresetParameter2(anchor("PresetParameter2", 192)).consumedBytes());
        assertEquals(32, MeteoraDlmmParser.parseOracleHeader(anchor("Oracle", 32)).consumedBytes());
        assertEquals(340, MeteoraDammV1Parser.parseConfig(anchor("Config", 340)).consumedBytes());
        assertEquals(328, MeteoraDammV2Parser.parseConfig(anchor("Config", 328)).consumedBytes());
        assertEquals(1112, MeteoraDammV2Parser.parsePool(dammV2()).consumedBytes());
        byte[] invalidBoolean = dammV1(); invalidBoolean[233] = 2;
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV1Parser.parsePool(invalidBoolean));
    }

    /** 验证 DAMM v2 金额字段、布局版本，以及所有基础费用调度模式。 */
    @Test void readsDammV2AmountsAndFeeModes() {
        byte[] data = dammV2();
        put(data, 680, U64_MAX, 8);
        put(data, 688, BigInteger.valueOf(100), 8);
        data[696] = 1;
        DexAccountData pool = MeteoraDammV2Parser.parsePool(data);
        assertEquals(MINT_A, pool.publicKey("token_a_mint"));
        assertEquals(U64_MAX, MeteoraDammV2Parser.trackedAmounts(pool).first());
        List<String> types = List.of("PodAlignedFeeTimeScheduler", "PodAlignedFeeTimeScheduler",
                "PodAlignedFeeRateLimiter", "PodAlignedFeeMarketCapScheduler", "PodAlignedFeeMarketCapScheduler");
        for (int mode = 0; mode < 5; mode++) {
            data[16] = (byte) mode;
            DexAccountData scheduler = MeteoraDammV2Parser.parseFeeScheduler(MeteoraDammV2Parser.parsePool(data));
            assertEquals(types.get(mode), scheduler.accountType());
            assertEquals(mode, scheduler.intValue("base_fee_mode"));
            assertEquals(32, scheduler.consumedBytes());
        }
        data[16] = 9;
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV2Parser.parseFeeScheduler(MeteoraDammV2Parser.parsePool(data)));
        data[696] = 0;
        assertThrows(IllegalArgumentException.class, () -> MeteoraDammV2Parser.trackedAmounts(MeteoraDammV2Parser.parsePool(data)));
    }

    /** 验证 Whirlpool 的字段偏移和 adaptive-fee Oracle。 */
    @Test void readsWhirlpoolAndAdaptiveOracle() {
        byte[] data = whirlpool();
        put(data, 65, LARGE_U128, 16);
        put(data, 81, BigInteger.valueOf(-42026), 4);
        put(data, 43, BigInteger.valueOf(128), 2);
        DexAccountData pool = OrcaWhirlpoolParser.parsePool(data);
        assertEquals(LARGE_U128, pool.integer("sqrtPrice"));
        assertEquals(-42026, pool.intValue("tickCurrentIndex"));
        assertEquals(128, OrcaWhirlpoolParser.feeTierIndex(pool));
        assertEquals(64, pool.intValue("tickSpacing"));
        assertEquals(653, pool.consumedBytes());
        byte[] oracle = anchor("Oracle", 254);
        putKey(oracle, 8, POOL);
        put(oracle, 106, BigInteger.valueOf(42), 4);
        assertEquals(BigInteger.valueOf(42), OrcaWhirlpoolParser.parseOracle(oracle)
                .integer("adaptive_fee_variables.volatility_accumulator"));
    }

    /** 验证固定和动态 tick 的不同布局，包含变长已初始化分支。 */
    @Test void readsBothOrcaTickArrayFormats() {
        byte[] fixed = anchor("TickArray", 9988);
        fixed[12] = 1;
        put(fixed, 13, BigInteger.valueOf(-5), 16);
        putKey(fixed, 9956, POOL);
        DexAccountData parsedFixed = OrcaWhirlpoolParser.parseTickArray(fixed);
        assertEquals(BigInteger.valueOf(-5), parsedFixed.integer("ticks[0].liquidity_net"));
        assertEquals(POOL, parsedFixed.publicKey("whirlpool"));
        byte[] dynamic = anchor("DynamicTickArray", 260);
        putKey(dynamic, 12, POOL);
        put(dynamic, 44, BigInteger.ONE, 16);
        dynamic[60] = 1;
        put(dynamic, 61, BigInteger.valueOf(-7), 16);
        DexAccountData parsed = OrcaWhirlpoolParser.parseTickArray(dynamic);
        assertEquals(260, parsed.consumedBytes());
        assertEquals(88, parsed.list("ticks").size());
        DexAccountData.EnumValue tick = (DexAccountData.EnumValue) parsed.list("ticks").get(0);
        assertEquals("Initialized", tick.variant());
        assertEquals(BigInteger.valueOf(-7), ((Map<?, ?>) tick.fields().get(0)).get("liquidity_net"));
        dynamic[60] = 2;
        assertThrows(IllegalArgumentException.class, () -> OrcaWhirlpoolParser.parseTickArray(dynamic));
    }

    /** 对照用户提供的公开 Whirlpool 地址和字段，验证标准池种子。 */
    @Test void matchesWhirlpoolPoolVector() {
        assertEquals("CmyuZJEnEoTnSD5mJLMtzLApocPNszZCWwkiEBbUD3ez", OrcaWhirlpoolParser.derivePool(
                new PublicKey("2LecshUwdy9xi7meFgHtFJQNSKk4KdTrcpvaB56dP2NQ"), wsol(),
                new PublicKey("9gP2kCy3wA1ctvYWQk75guqXuHfrEomqydHLtcTCqiLa"), 64).toBase58());
    }

    /** 验证负索引的取整、协议不同的种子端序和代币排序规则。 */
    @Test void derivesProtocolSpecificSeeds() {
        assertEquals(-1, MeteoraDlmmParser.binArrayIndex(-1));
        assertEquals(-2, MeteoraDlmmParser.binArrayIndex(-71));
        assertEquals(-3840, RaydiumClmmParser.tickArrayStartIndex(-1, 64));
        assertEquals(-5632, OrcaWhirlpoolParser.tickArrayStartIndex(-1, 64));
        assertEquals(PublicKey.findProgramAddress(List.of(text("amm_config"), new byte[]{1, 2}),
                RaydiumCpmmParser.PROGRAM_ID).getAddress(), RaydiumCpmmParser.deriveAmmConfig(258));
        assertEquals(PublicKey.findProgramAddress(List.of(text("tick_array"), POOL.toByteArray(),
                new byte[]{(byte) 255, (byte) 255, (byte) 234, 0}), RaydiumClmmParser.PROGRAM_ID).getAddress(),
                RaydiumClmmParser.deriveTickArray(POOL, -5632));
        assertEquals(PublicKey.findProgramAddress(List.of(text("tick_array"), POOL.toByteArray(), text("-5632")),
                OrcaWhirlpoolParser.PROGRAM_ID).getAddress(), OrcaWhirlpoolParser.deriveTickArray(POOL, -5632));
        assertEquals(PublicKey.findProgramAddress(List.of(text("bin_array"), POOL.toByteArray(),
                new byte[]{(byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255}),
                MeteoraDlmmParser.PROGRAM_ID).getAddress(), MeteoraDlmmParser.deriveBinArray(POOL, -2));
        assertEquals(RaydiumCpmmParser.deriveStandardPool(POOL, MINT_A, MINT_B),
                RaydiumCpmmParser.deriveStandardPool(POOL, MINT_B, MINT_A));
        assertEquals(PublicKey.findProgramAddress(List.of(text("pool"), POOL.toByteArray(), MINT_B.toByteArray(),
                MINT_A.toByteArray()), MeteoraDammV2Parser.PROGRAM_ID).getAddress(),
                MeteoraDammV2Parser.derivePool(POOL, MINT_A, MINT_B));
        assertEquals(MeteoraDammV1Parser.derivePermissionlessPool(0, MINT_A, MINT_B),
                MeteoraDammV1Parser.deriveFeeTierPool(0, MINT_A, MINT_B, BigInteger.valueOf(25)));
        assertNotEquals(DexPda.associatedTokenAccount(POOL, MINT_A, DexPda.TOKEN_PROGRAM),
                DexPda.associatedTokenAccount(POOL, MINT_A, DexPda.TOKEN_2022_PROGRAM));
        assertEquals(PublicKey.findProgramAddress(List.of(text("preset_parameter"), new byte[]{1, 2},
                new byte[]{3, 4}), MeteoraDlmmParser.PROGRAM_ID).getAddress(),
                MeteoraDlmmParser.derivePresetParameter(513, 1027));
        assertThrows(IllegalArgumentException.class, () -> OrcaWhirlpoolParser.tickArrayStartIndex(0, 0));
        assertThrows(IllegalArgumentException.class, () -> RaydiumCpmmParser.deriveStandardPool(POOL, MINT_A, MINT_A));
    }

    /** 验证 SPL/Token-2022 基础金额、供应量和扣留转账费扩展。 */
    @Test void readsTokenBalancesAndMintSupplyWithoutSignLoss() {
        byte[] token = new byte[165];
        putKey(token, 0, MINT_A); putKey(token, 32, POOL); put(token, 64, U64_MAX, 8); token[108] = 1;
        assertEquals(U64_MAX, TokenAccountParser.parseTokenAccount(DexPda.TOKEN_PROGRAM, token).amount());
        byte[] extended = Arrays.copyOf(token, 178);
        extended[165] = 2; extended[166] = 2; extended[168] = 8;
        put(extended, 170, BigInteger.valueOf(99), 8);
        TokenAccountParser.TokenAccount account = TokenAccountParser.parseTokenAccount(DexPda.TOKEN_2022_PROGRAM, extended);
        assertEquals(BigInteger.valueOf(99), account.withheldTransferFee());
        account.mint().toByteArray()[0] = 9;
        account.owner().toByteArray()[0] = 9;
        assertEquals(MINT_A, account.mint());
        assertEquals(POOL, account.owner());
        account.extensions().get(2)[0] = 0;
        assertEquals(BigInteger.valueOf(99), account.withheldTransferFee());
        assertThrows(IllegalArgumentException.class, () -> TokenAccountParser.parseTokenAccount(DexPda.TOKEN_PROGRAM, extended));
        byte[] mint = new byte[82]; put(mint, 36, U64_MAX, 8); mint[44] = 9; mint[45] = 1;
        assertEquals(U64_MAX, TokenAccountParser.parseMint(DexPda.TOKEN_PROGRAM, mint).supply());
        extended[168] = 40;
        assertThrows(IllegalArgumentException.class, () -> TokenAccountParser.parseTokenAccount(DexPda.TOKEN_2022_PROGRAM, extended));
    }

    /** 验证 Token-2022 mint 填充、扩展类型和多签长度，防止误读成普通账户。 */
    @Test void validatesToken2022MintAndAuthorityCopies() {
        byte[] mint = new byte[202]; mint[0] = 1; putKey(mint, 4, MINT_A); mint[45] = 1;
        mint[165] = 1; mint[166] = 3; mint[168] = 32; putKey(mint, 170, MINT_B);
        TokenAccountParser.Mint parsed = TokenAccountParser.parseMint(DexPda.TOKEN_2022_PROGRAM, mint);
        assertTrue(parsed.extensions().containsKey(3));
        parsed.mintAuthority().toByteArray()[0] = 9;
        assertEquals(MINT_A, parsed.mintAuthority());
        mint[82] = 1;
        assertThrows(IllegalArgumentException.class, () -> TokenAccountParser.parseMint(DexPda.TOKEN_2022_PROGRAM, mint));
        mint[82] = 0; mint[165] = 2;
        assertThrows(IllegalArgumentException.class, () -> TokenAccountParser.parseMint(DexPda.TOKEN_2022_PROGRAM, mint));
        byte[] multisigLength = new byte[355]; multisigLength[165] = 2;
        assertThrows(IllegalArgumentException.class, () -> TokenAccountParser.parseTokenAccount(DexPda.TOKEN_2022_PROGRAM, multisigLength));
    }

    /** 验证错误判别符、截断数据和外部修改不能悄悄影响解析结果。 */
    @Test void rejectsMalformedDataAndCopiesMutableValues() {
        byte[] data = pump();
        DexAccountData parsed = PumpSwapParser.parsePool(data);
        data[43] = 9;
        assertEquals(MINT_A, parsed.publicKey("base_mint"));
        parsed.publicKey("base_mint").toByteArray()[0] = 9;
        assertEquals(MINT_A, parsed.publicKey("base_mint"));
        assertThrows(UnsupportedOperationException.class, () -> parsed.staticFields().put("x", 1));
        assertThrows(IllegalArgumentException.class, () -> parsed.integer("base_mint"));
        assertThrows(IllegalArgumentException.class, () -> parsed.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> RaydiumClmmParser.parsePool(new byte[1544]));
        assertThrows(IllegalArgumentException.class, () -> RaydiumCpmmParser.parsePool(Arrays.copyOf(cpmm(), 636)));
        assertThrows(ArithmeticException.class, () -> {
            byte[] supply = pump(); put(supply, 203, U64_MAX, 8);
            PumpSwapParser.parsePool(supply).longValueExact("lp_supply");
        });
    }

    /** 验证缓存丢弃乱序事件、解析失败不覆盖状态、关闭标记阻止旧事件复活。 */
    @Test void cachesUpdatesWithVersionBarrier() {
        AtomicInteger calls = new AtomicInteger();
        DexAccountCache<DexAccountData> cache = new DexAccountCache<>((owner, bytes) -> {
            calls.incrementAndGet(); return DexParsers.parsePool(owner, bytes);
        });
        assertTrue(cache.update(POOL, PumpSwapParser.PROGRAM_ID, 100, 2, pump()));
        assertFalse(cache.update(POOL, PumpSwapParser.PROGRAM_ID, 99, 9, new byte[0]));
        assertFalse(cache.update(POOL, PumpSwapParser.PROGRAM_ID, 100, 1, new byte[0]));
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> cache.update(POOL, PumpSwapParser.PROGRAM_ID, 101, 1, new byte[0]));
        assertEquals(100, cache.get(POOL).orElseThrow().slot());
        assertTrue(cache.markClosed(POOL, 101, 3));
        assertTrue(cache.get(POOL).isEmpty());
        assertFalse(cache.update(POOL, PumpSwapParser.PROGRAM_ID, 101, 2, pump()));
        assertTrue(cache.update(POOL, PumpSwapParser.PROGRAM_ID, 102, 1, pump()));
        cache.clear(); assertTrue(cache.get(POOL).isEmpty());
    }

    /** 独立样本使用官方字段偏移，不借助生产解析器或布局资源生成。 */
    private static byte[] pump() {
        byte[] data = anchor("Pool", 271); putKey(data, 43, MINT_A); putKey(data, 75, MINT_B); return data;
    }
    private static byte[] ammV4() {
        byte[] data = new byte[752]; putKey(data, 400, MINT_A); putKey(data, 432, MINT_B); putKey(data, 528, POOL); return data;
    }
    private static byte[] cpmm() {
        byte[] data = anchor("PoolState", 637); putKey(data, 168, MINT_A); putKey(data, 200, MINT_B);
        putKey(data, 232, DexPda.TOKEN_PROGRAM); putKey(data, 264, DexPda.TOKEN_2022_PROGRAM); return data;
    }
    private static byte[] clmm() {
        byte[] data = anchor("PoolState", 1544); putKey(data, 73, MINT_A); putKey(data, 105, MINT_B);
        put(data, 235, BigInteger.valueOf(64), 2); return data;
    }
    private static byte[] dlmm() {
        byte[] data = anchor("LbPair", 904); putKey(data, 88, MINT_A); putKey(data, 120, MINT_B); data[880] = 1; return data;
    }
    private static byte[] dammV1() {
        byte[] data = anchor("Pool", 894); putKey(data, 40, MINT_A); putKey(data, 72, MINT_B); return data;
    }
    private static byte[] dammV2() {
        byte[] data = anchor("Pool", 2000); putKey(data, 168, MINT_A); putKey(data, 200, MINT_B); return data;
    }
    private static byte[] whirlpool() {
        byte[] data = anchor("Whirlpool", 653); putKey(data, 101, MINT_A); putKey(data, 181, MINT_B);
        put(data, 41, BigInteger.valueOf(64), 2); return data;
    }
    /** 创建纯字节测试账户的 Anchor 头部。 */
    private static byte[] anchor(String type, int length) {
        try {
            byte[] data = new byte[length];
            System.arraycopy(MessageDigest.getInstance("SHA-256").digest(text("account:" + type)), 0, data, 0, 8);
            return data;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    /** 按官方小端编码写入测试整数，允许有符号负数。 */
    private static void put(byte[] data, int offset, BigInteger value, int length) {
        for (int i = 0; i < length; i++) data[offset + i] = value.shiftRight(i * 8).byteValue();
    }
    /** 写入测试公钥。 */
    private static void putKey(byte[] data, int offset, PublicKey key) { System.arraycopy(key.toByteArray(), 0, data, offset, 32); }
    /** 生成明确不含私钥的测试公钥。 */
    private static PublicKey key(int first) { byte[] bytes = new byte[32]; bytes[0] = (byte) first; return new PublicKey(bytes); }
    /** 测试用 UTF-8 文本种子。 */
    private static byte[] text(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    /** WSOL 固定 mint。 */
    private static PublicKey wsol() { return new PublicKey("So11111111111111111111111111111111111111112"); }
    /** 写入旧版 Serum/OpenBook 账户固定头尾。 */
    private static void envelope(byte[] data) {
        System.arraycopy(text("serum"), 0, data, 0, 5); System.arraycopy(text("padding"), 0, data, data.length - 7, 7);
    }
}
