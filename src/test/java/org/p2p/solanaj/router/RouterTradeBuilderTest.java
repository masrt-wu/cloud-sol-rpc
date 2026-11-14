package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.TokenProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证单跳 Router 指令布局和各 DEX 交易片段的本地编码。 */
class RouterTradeBuilderTest {
    /** 模拟交易用户的占位公钥。 */
    private static final PublicKey USER = key(1);
    /** 模拟池 A 侧代币的占位 mint。 */
    private static final PublicKey A = key(2);
    /** 模拟池 B 侧代币的占位 mint。 */
    private static final PublicKey B = key(3);
    /** 测试两侧代币所属的经典 SPL Token 程序。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;
    /** 本地买卖测试的 WSOL mint。 */
    private static final PublicKey WSOL = RouterTradeBuilder.WSOL_MINT;
    /** 仅供本地编码测试使用的 USDC 占位 mint，不是真实 USDC。 */
    private static final PublicKey USDC = key(90);
    /** 仅供本地编码测试使用的 quote 占位 mint。 */
    private static final PublicKey QUOTE = key(91);
    /** 仅供本地编码测试使用的目标币占位 mint。 */
    private static final PublicKey TARGET = key(92);
    /** 仅供本地编码测试使用的手续费接收方。 */
    private static final PublicKey FEE_RECIPIENT = key(93);

    /** 重复查询复用确定性 ATA，且 Token-2022 与经典 SPL 的地址不能串用。 */
    @Test
    void cachesAtaWithoutMixingTokenPrograms() {
        PublicKey classic = RouterTradeBuilder.ata(USER, A, TOKEN);
        byte[] expected = classic.toByteArray().clone();
        classic.toByteArray()[0] ^= 1;
        assertArrayEquals(expected, RouterTradeBuilder.ata(USER, A, TOKEN).toByteArray());
        assertEquals(PublicKey.findProgramAddress(List.of(USER.toByteArray(), TOKEN.toByteArray(),
                A.toByteArray()), AssociatedTokenProgram.PROGRAM_ID).getAddress(),
                RouterTradeBuilder.ata(USER, A, TOKEN));
        assertNotEquals(RouterTradeBuilder.ata(USER, A, TOKEN),
                RouterTradeBuilder.ata(USER, A, TokenProgram.TOKEN_2022));
    }

    /**
     * 用重复字节生成可复现的测试公钥。
     * @param value 用于填充公钥的字节值
     * @return 仅供本地测试的公钥
     */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /** 检查单跳路由数据与 Rust 端格式一致。 */
    @Test
    void routeEnvelopeMatchesRustLayout() {
        RouterLeg leg = RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(
                key(4), A, B, key(5), key(6), TOKEN), USER, A, 100, 7);
        RouterTradeBuilder.Swap swap = new RouterTradeBuilder.Swap(
                USER, A, B, TOKEN, TOKEN, 100, 7, leg, true);
        List<TransactionInstruction> buy = RouterTradeBuilder.buy(swap, key(7));
        List<TransactionInstruction> sell = RouterTradeBuilder.sell(swap, key(7));
        assertEquals(2, buy.size());
        assertEquals(1, buy.get(0).getData()[0]); // 幂等创建关联代币账户。
        assertArrayEquals(buy.get(1).getData(), sell.get(1).getData());
        TransactionInstruction route = buy.get(1);
        byte[] data = route.getData();
        assertEquals(2, data[0]);
        assertEquals(100, u64(data, 1));
        assertEquals(7, u64(data, 9));
        assertEquals(1, data[17]); // 手续费资产为代币。
        assertEquals(1, data[18]); // 路由只有一跳。
        assertArrayEquals(RaydiumLegs.AMM_V4.toByteArray(), Arrays.copyOfRange(data, 19, 51));
        assertEquals(leg.accounts().size(), Byte.toUnsignedInt(data[51]));
        assertEquals(17, Short.toUnsignedInt(ByteBuffer.wrap(data, 52, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort()));
        assertArrayEquals(leg.data(), Arrays.copyOfRange(data, 54, data.length));
        assertEquals(6 + leg.accounts().size() + 1, route.getKeys().size());
    }

    /** 比对各 DEX 精确输入指令长度及买卖方向。 */
    @Test
    void allDexLegsEncodeExactInAndDirection() {
        assertEquals(24, RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(key(10), key(11), A, B,
                key(12), key(13), TOKEN, TOKEN, key(14)), USER, B, 100, 7).data().length);
        assertEquals(41, RaydiumLegs.clmm(new RaydiumLegs.Clmm(key(15), key(16), A, B,
                key(17), key(18), TOKEN, TOKEN, key(19), null, List.of(key(20))),
                USER, A, 100, 7).data().length);
        OrcaLegs.Pool orca = new OrcaLegs.Pool(key(21), A, B, key(22), key(23),
                TOKEN, TOKEN, List.of(key(24), key(25), key(26)));
        assertEquals(43, OrcaLegs.swap(orca, USER, A, 100, 7).data().length);
        assertEquals(1, OrcaLegs.swap(orca, USER, A, 100, 7).data()[41]);
        assertEquals(0, OrcaLegs.swap(orca, USER, B, 100, 7).data()[41]);
        assertEquals(25, MeteoraLegs.dammV2(new MeteoraLegs.DammV2(key(27), A, B,
                key(28), key(29), TOKEN, TOKEN, null, true), USER, A, 100, 7).data().length);
        assertEquals(28, MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(30), key(31), key(32),
                A, B, TOKEN, TOKEN, key(33), null, List.of(key(34))),
                USER, B, 100, 7).data().length);
        PumpAmmLegs.Pool pump = new PumpAmmLegs.Pool(key(35), A, B, key(36), key(37),
                TOKEN, TOKEN, key(38), key(39), key(40), key(41), null, false);
        assertEquals(25, PumpAmmLegs.buy(pump, USER, 100, 7).data().length);
        assertEquals(24, PumpAmmLegs.sell(pump, USER, 100, 7).data().length);
        assertEquals(B, PumpAmmLegs.buy(pump, USER, 100, 7).inputMint());
        assertEquals(A, PumpAmmLegs.sell(pump, USER, 100, 7).inputMint());
    }

    /** 输入与输出 mint 方向不匹配时应拒绝构造。 */
    @Test
    void refusesWrongMintDirection() {
        RouterLeg leg = RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(
                key(4), A, B, key(5), key(6), TOKEN), USER, A, 100, 7);
        RouterTradeBuilder.Swap wrong = new RouterTradeBuilder.Swap(
                USER, B, A, TOKEN, TOKEN, 100, 7, leg, false);
        assertThrows(IllegalArgumentException.class,
                () -> RouterTradeBuilder.route(wrong, key(7)));
    }

    /**
     * 按小端字节序读取指令中的 64 位金额。
     * @param data Router 指令的原始数据
     * @param offset 金额字段的字节偏移
     * @return 解码后的原始金额
     */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }


    /**
     * 买入测试：分别构造 WSOL→quote→目标币的二跳，以及
     * WSOL→USDC→quote→目标币的三跳，覆盖六种桥接池型。
     * 这里只验证指令，不访问 RPC，也不广播交易。
     */
    @Test
    public void buy() {
        verifyTwoHopBuy();
        verifyThreeHopBuy();
        verifyPumpFirstHopBuy();
    }

    /**
     * 卖出测试：分别构造目标币→quote→WSOL 的二跳，以及
     * 目标币→quote→USDC→WSOL 的三跳，覆盖六种桥接池型。
     * 这里只验证指令，不访问 RPC，也不广播交易。
     */
    @Test
    public void sell() {
        verifyTwoHopSell();
        verifyThreeHopSell();
    }

    /** 验证 WSOL→quote→目标币的动态二跳买入。 */
    private static void verifyTwoHopBuy() {
        List<RouterLeg> buyBridges = bridgeLegs(WSOL, QUOTE, 100, 50);
        List<RouterLeg> targetBuys = List.of(
                LaunchLabLegs.buy(targetPool(), USER, 1, 10),
                RaydiumLegs.cpmm(targetCpmmPool(), USER, QUOTE, 1, 10));
        assertEquals(6, buyBridges.size());
        for (RouterLeg bridge : buyBridges) {
            for (RouterLeg targetBuy : targetBuys) {
                RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                        USER, bridge, targetBuy, TOKEN, TOKEN, TOKEN,
                        100, 50, 10, false, false);
                assertTwoHop(swap, true);
            }
        }
    }

    /** 验证目标币→quote→WSOL 的动态二跳卖出。 */
    private static void verifyTwoHopSell() {
        List<RouterLeg> sellBridges = bridgeLegs(QUOTE, WSOL, 1, 10);
        List<RouterLeg> targetSells = List.of(
                LaunchLabLegs.sell(targetPool(), USER, 100, 50),
                RaydiumLegs.cpmm(targetCpmmPool(), USER, TARGET, 100, 50));
        assertEquals(6, sellBridges.size());
        for (RouterLeg targetSell : targetSells) {
            for (RouterLeg bridge : sellBridges) {
                RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                        USER, targetSell, bridge, TOKEN, TOKEN, TOKEN,
                        100, 50, 10, false, false);
                assertTwoHop(swap, false);
            }
        }
    }

    /** 验证 WSOL→USDC→quote→目标币的所有动态三跳买入组合。 */
    private static void verifyThreeHopBuy() {
        List<RouterLeg> wsolToUsdc = bridgeLegs(WSOL, USDC, 100, 50);
        List<RouterLeg> usdcToQuote = bridgeLegs(USDC, QUOTE, 1, 20);
        List<RouterLeg> targetBuys = List.of(
                LaunchLabLegs.buy(targetPool(), USER, 1, 10),
                RaydiumLegs.cpmm(targetCpmmPool(), USER, QUOTE, 1, 10));
        for (RouterLeg entry : wsolToUsdc) {
            for (RouterLeg middle : usdcToQuote) {
                for (RouterLeg targetBuy : targetBuys) {
                    RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                            USER, entry, middle, targetBuy, TOKEN, TOKEN, TOKEN, TOKEN,
                            100, 50, 20, 10, true, false, false);
                    assertThreeHop(swap, true);
                }
            }
        }
    }

    /** 验证目标币→quote→USDC→WSOL 的所有动态三跳卖出组合。 */
    private static void verifyThreeHopSell() {
        List<RouterLeg> quoteToUsdc = bridgeLegs(QUOTE, USDC, 1, 20);
        List<RouterLeg> usdcToWsol = bridgeLegs(USDC, WSOL, 1, 10);
        List<RouterLeg> targetSells = List.of(
                LaunchLabLegs.sell(targetPool(), USER, 100, 50),
                RaydiumLegs.cpmm(targetCpmmPool(), USER, TARGET, 100, 50));
        for (RouterLeg targetSell : targetSells) {
            for (RouterLeg middle : quoteToUsdc) {
                for (RouterLeg exit : usdcToWsol) {
                    RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                            USER, targetSell, middle, exit, TOKEN, TOKEN, TOKEN, TOKEN,
                            100, 50, 20, 10, true, false, false);
                    assertThreeHop(swap, false);
                }
            }
        }
    }

    /** PumpSwap 只在第一跳买入 quote；链上尚不能动态覆盖它作为后续跳的输入量。 */
    private static void verifyPumpFirstHopBuy() {
        PumpAmmLegs.Pool pool = new PumpAmmLegs.Pool(key(70), QUOTE, WSOL,
                key(71), key(72), TOKEN, TOKEN, key(73), key(74), key(75),
                key(76), null, false);
        RouterLeg first = PumpAmmLegs.buy(pool, USER, 100, 50);
        RouterLeg second = LaunchLabLegs.buy(targetPool(), USER, 1, 10);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, second, TOKEN, TOKEN, TOKEN,
                100, 50, 10, false, false);
        assertTwoHop(swap, true);
    }

    /**
     * 为指定代币对构造六种本地桥接池：Raydium AMM V4/CPMM/CLMM、
     * Meteora DAMM v2/DLMM、Orca Whirlpool。所有账户仅是测试占位符。
     * @param inputMint 本跳支付代币的 mint
     * @param outputMint 本跳收到代币的 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return 六种池型按固定顺序构造的一跳列表
     */
    private static List<RouterLeg> bridgeLegs(PublicKey inputMint, PublicKey outputMint,
                                               long amountIn, long minOut) {
        return List.of(
                RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(key(30), inputMint, outputMint,
                        key(31), key(32), TOKEN), USER, inputMint, amountIn, minOut),
                RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(key(33), key(34), inputMint, outputMint,
                        key(35), key(36), TOKEN, TOKEN, key(37)),
                        USER, inputMint, amountIn, minOut),
                RaydiumLegs.clmm(new RaydiumLegs.Clmm(key(38), key(39), inputMint, outputMint,
                        key(40), key(41), TOKEN, TOKEN, key(42), null, List.of(key(43))),
                        USER, inputMint, amountIn, minOut),
                MeteoraLegs.dammV2(new MeteoraLegs.DammV2(key(44), inputMint, outputMint,
                        key(45), key(46), TOKEN, TOKEN, null, false),
                        USER, inputMint, amountIn, minOut),
                MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(47), key(48), key(49),
                        inputMint, outputMint, TOKEN, TOKEN, key(50), null, List.of(key(51))),
                        USER, inputMint, amountIn, minOut),
                OrcaLegs.swap(new OrcaLegs.Pool(key(52), inputMint, outputMint,
                        key(53), key(54), TOKEN, TOKEN, List.of(key(55), key(56), key(57))),
                        USER, inputMint, amountIn, minOut));
    }

    /** 返回 quote/目标币的 LaunchLab 测试池。 */
    private static LaunchLabLegs.Pool targetPool() {
        return new LaunchLabLegs.Pool(TARGET, QUOTE, TOKEN, TOKEN,
                key(60), key(61), key(62), key(63), key(64), key(65), key(66));
    }

    /** 返回 quote/目标币的 CPMM 测试池。 */
    private static RaydiumLegs.Cpmm targetCpmmPool() {
        return new RaydiumLegs.Cpmm(key(67), key(68), TARGET, QUOTE,
                key(69), key(70), TOKEN, TOKEN, key(71));
    }

    /**
     * 断言动态二跳的输入、quote 账户、最低输出和标签均正确。
     * @param swap 待检查的动态二跳参数
     * @param buy 为真时使用买入入口，为假时使用卖出入口
     */
    private static void assertTwoHop(RouterTradeBuilder.DynamicRouteSwap swap, boolean buy) {
        List<TransactionInstruction> instructions = buy
                ? RouterTradeBuilder.buy(swap, FEE_RECIPIENT)
                : RouterTradeBuilder.sell(swap, FEE_RECIPIENT);
        assertEquals(1, instructions.size());
        TransactionInstruction route = instructions.get(0);
        byte[] data = route.getData();
        assertEquals(RouterTradeBuilder.ROUTER_PROGRAM, route.getProgramId());
        assertEquals(3, Byte.toUnsignedInt(data[0]));
        assertEquals(2, Byte.toUnsignedInt(data[18]));
        assertEquals(100, u64(data, 1));
        assertEquals(50, u64(data, 19));
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                route.getKeys().get(6).getPublicKey());
        assertEquals(1, swap.secondLeg().amountInRaw());
    }

    /**
     * 断言动态三跳的两种中间账户和后两跳零输入占位与 Rust 标签 4 一致。
     * @param swap 待检查的动态三跳参数
     * @param buy 为真时使用买入入口，为假时使用卖出入口
     */
    private static void assertThreeHop(RouterTradeBuilder.ThreeHopRouteSwap swap, boolean buy) {
        List<TransactionInstruction> instructions = buy
                ? RouterTradeBuilder.buy(swap, FEE_RECIPIENT)
                : RouterTradeBuilder.sell(swap, FEE_RECIPIENT);
        assertEquals(1, instructions.size());
        TransactionInstruction route = instructions.get(0);
        byte[] data = route.getData();
        assertEquals(RouterTradeBuilder.ROUTER_PROGRAM, route.getProgramId());
        assertEquals(4, Byte.toUnsignedInt(data[0]));
        assertEquals(3, Byte.toUnsignedInt(data[18]));
        assertEquals(100, u64(data, 1));
        assertEquals(50, u64(data, 19));
        assertEquals(20, u64(data, 27));
        assertEquals(RouterTradeBuilder.ata(USER, swap.firstIntermediateMint(), TOKEN),
                route.getKeys().get(6).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, swap.secondIntermediateMint(), TOKEN),
                route.getKeys().get(7).getPublicKey());
        int cursor = 35;
        for (int i = 0; i < 3; i++) {
            RouterLeg leg = swap.legs().get(i);
            cursor += 32;
            assertEquals(leg.accounts().size(), Byte.toUnsignedInt(data[cursor++]));
            int dataLength = (data[cursor] & 0xff) | ((data[cursor + 1] & 0xff) << 8);
            cursor += 2;
            assertEquals(leg.data().length, dataLength);
            if (i > 0) {
                int amountOffset = leg.programId().equals(RaydiumLegs.AMM_V4) ? 1 : 8;
                assertEquals(0, u64(data, cursor + amountOffset));
            }
            cursor += dataLength;
        }
        assertEquals(data.length, cursor);
    }
}
