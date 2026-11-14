package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.TokenProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Java 三跳编码与当前 Rust Router 的标签、金额占位及账户顺序一致；不访问 RPC。 */
class RouterThreeHopSyncTest {
    /** 测试用户公钥，不代表真实签名账户。 */
    private static final PublicKey USER = key(1);
    /** 测试手续费接收方公钥。 */
    private static final PublicKey FEE = key(2);
    /** 代表 USDC 的测试 mint，不是真实 USDC。 */
    private static final PublicKey USDC = key(3);
    /** 代表 quote 的测试 mint。 */
    private static final PublicKey QUOTE = key(4);
    /** 代表目标币的测试 mint。 */
    private static final PublicKey TARGET = key(5);
    /** 真实 WSOL mint；这里只参与本地账户推导。 */
    private static final PublicKey WSOL = RouterTradeBuilder.WSOL_MINT;
    /** 经典 SPL Token 程序地址。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;

    /** 买入路径：WSOL → USDC → quote → LaunchLab 目标币；后两跳输入在字节中为零。 */
    @Test
    void buyThreeHopWritesActualOutputMode() {
        RouterLeg first = RaydiumLegs.buyCpmm(wsolUsdcPool(), USER, 100, 40);
        RouterLeg middle = OrcaLegs.sell(usdcQuotePool(), USER, 1, 20);
        RouterLeg last = LaunchLabLegs.buy(targetPool(), USER, 1, 10);
        RouterTradeBuilder.ThreeHopRouteSwap swap = threeHop(first, middle, last, true);
        TransactionInstruction instruction = RouterTradeBuilder.routeThreeHop(swap, FEE);
        byte[] data = instruction.getData();
        assertEquals(4, Byte.toUnsignedInt(data[0]));
        assertEquals(100, u64(data, 1));
        assertEquals(10, u64(data, 9));
        assertEquals(1, Byte.toUnsignedInt(data[17]));
        assertEquals(3, Byte.toUnsignedInt(data[18]));
        assertEquals(40, u64(data, 19));
        assertEquals(20, u64(data, 27));
        int offset = 35;
        for (int i = 0; i < 3; i++) {
            RouterLeg leg = swap.legs().get(i);
            offset += 32;
            assertEquals(leg.accounts().size(), Byte.toUnsignedInt(data[offset++]));
            int length = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
            offset += 2;
            assertEquals(leg.data().length, length);
            assertEquals(i == 0 ? leg.amountInRaw() : 0,
                    u64(data, offset + (leg.programId().equals(RaydiumLegs.AMM_V4) ? 1 : 8)));
            offset += length;
        }
        assertEquals(data.length, offset);
        assertEquals(RouterTradeBuilder.ata(USER, USDC, TOKEN),
                instruction.getKeys().get(6).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                instruction.getKeys().get(7).getPublicKey());
        assertTrue(instruction.getKeys().get(6).isWritable());
        assertTrue(instruction.getKeys().get(7).isWritable());
        assertEquals(1, middle.amountInRaw());
        assertEquals(1, last.amountInRaw());
        assertEquals(4, RouterTradeBuilder.buy(swap, FEE).size());
        List<TransactionInstruction> withSol = RouterTradeBuilder.buyWithSol(swap, FEE);
        assertEquals(7, withSol.size());
        assertEquals(4, Byte.toUnsignedInt(withSol.get(6).getData()[0]));
    }

    /** 卖出路径：LaunchLab 目标币 → quote → USDC → WSOL；最终仍为 WSOL 代币。 */
    @Test
    void sellThreeHopSpendsOnlyNewIntermediateBalances() {
        RouterLeg first = LaunchLabLegs.sell(targetPool(), USER, 100, 40);
        RouterLeg middle = OrcaLegs.buy(usdcQuotePool(), USER, 1, 20);
        RouterLeg last = RaydiumLegs.sellCpmm(wsolUsdcPool(), USER, 1, 10);
        RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                USER, first, middle, last, TOKEN, TOKEN, TOKEN, TOKEN,
                100, 40, 20, 10, true, true, true);
        TransactionInstruction instruction = RouterTradeBuilder.routeThreeHop(swap, FEE);
        assertEquals(4, Byte.toUnsignedInt(instruction.getData()[0]));
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                instruction.getKeys().get(6).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, USDC, TOKEN),
                instruction.getKeys().get(7).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, WSOL, TOKEN),
                instruction.getKeys().get(4).getPublicKey());
        assertEquals(4, RouterTradeBuilder.sell(swap, FEE).size());
    }

    /** 关闭动态模式时，三跳应保留旧标签 2 及调用方预先填写的固定输入量。 */
    @Test
    void fixedModeKeepsLegacyRouteData() {
        RouterLeg first = RaydiumLegs.buyCpmm(wsolUsdcPool(), USER, 100, 40);
        RouterLeg middle = OrcaLegs.sell(usdcQuotePool(), USER, 40, 20);
        RouterLeg last = LaunchLabLegs.buy(targetPool(), USER, 20, 10);
        RouterTradeBuilder.ThreeHopRouteSwap swap = threeHop(first, middle, last, false);
        byte[] data = RouterTradeBuilder.routeThreeHop(swap, FEE).getData();
        assertEquals(2, Byte.toUnsignedInt(data[0]));
        assertEquals(3, Byte.toUnsignedInt(data[18]));
    }

    /** 后两跳金额不是占位量，或第二跳是 LaunchLab，必须在本地拒绝。 */
    @Test
    void rejectsInvalidDynamicMiddleLeg() {
        RouterLeg first = RaydiumLegs.buyCpmm(wsolUsdcPool(), USER, 100, 40);
        RouterLeg middle = OrcaLegs.sell(usdcQuotePool(), USER, 40, 20);
        RouterLeg last = LaunchLabLegs.buy(targetPool(), USER, 1, 10);
        assertThrows(IllegalArgumentException.class, () -> threeHop(first, middle, last, true));
    }

    /** Rust 白名单中的每种精确输入池都可作为动态两跳的最后一跳。 */
    @Test
    void dynamicSellAcceptsEverySupportedFinalPool() {
        RouterLeg first = LaunchLabLegs.sell(targetPool(), USER, 100, 40);
        List<RouterLeg> finalLegs = List.of(
                RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(key(60), QUOTE, WSOL,
                        key(61), key(62), TOKEN), USER, QUOTE, 1, 10),
                RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(key(63), key(64), QUOTE, WSOL,
                        key(65), key(66), TOKEN, TOKEN, key(67)), USER, QUOTE, 1, 10),
                RaydiumLegs.clmm(new RaydiumLegs.Clmm(key(68), key(69), QUOTE, WSOL,
                        key(70), key(71), TOKEN, TOKEN, key(72), null, List.of(key(73))),
                        USER, QUOTE, 1, 10),
                MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(74), key(75), key(76),
                        QUOTE, WSOL, TOKEN, TOKEN, key(77), null, List.of(key(78))),
                        USER, QUOTE, 1, 10),
                MeteoraLegs.dammV2(new MeteoraLegs.DammV2(key(79), QUOTE, WSOL,
                        key(80), key(81), TOKEN, TOKEN, null, false), USER, QUOTE, 1, 10),
                OrcaLegs.swap(new OrcaLegs.Pool(key(82), QUOTE, WSOL,
                        key(83), key(84), TOKEN, TOKEN, List.of(key(85), key(86), key(87))),
                        USER, QUOTE, 1, 10));
        for (RouterLeg last : finalLegs) {
            RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                    USER, first, last, TOKEN, TOKEN, TOKEN, 100, 40, 10, false, false);
            assertEquals(3, Byte.toUnsignedInt(RouterTradeBuilder.routeDynamic(swap, FEE).getData()[0]));
            assertEquals(1, RouterTradeBuilder.sell(swap, FEE).size());
        }
    }

    /** 两跳卖出时，PumpSwap sell 可将第一跳实际收到的 quote 全部换成 WSOL。 */
    @Test
    void pumpSellCanBeDynamicTwoHopFinalLeg() {
        RouterLeg first = LaunchLabLegs.sell(targetPool(), USER, 100, 40);
        RouterLeg last = PumpAmmLegs.sell(pumpPool(QUOTE, WSOL), USER, 1, 10);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, last, TOKEN, TOKEN, TOKEN, 100, 40, 10, false, false);
        TransactionInstruction instruction = RouterTradeBuilder.routeDynamic(swap, FEE);
        assertEquals(3, Byte.toUnsignedInt(instruction.getData()[0]));
        assertEquals(1, u64(last.data(), 8));
        assertArrayEquals(last.data(), encodedLegData(instruction, 1));
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                last.accounts().get(5).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, WSOL, TOKEN),
                last.accounts().get(6).getPublicKey());
        assertEquals(1, RouterTradeBuilder.sell(swap, FEE).size());
    }

    /** 三跳买入时，PumpSwap sell 可作为第二跳，并由链上填写其实际输入量。 */
    @Test
    void pumpSellCanBeDynamicThreeHopMiddleLeg() {
        RouterLeg first = RaydiumLegs.buyCpmm(wsolUsdcPool(), USER, 100, 40);
        RouterLeg middle = PumpAmmLegs.sell(pumpPool(USDC, QUOTE), USER, 1, 20);
        RouterLeg last = LaunchLabLegs.buy(targetPool(), USER, 1, 10);
        RouterTradeBuilder.ThreeHopRouteSwap swap = threeHop(first, middle, last, true);
        TransactionInstruction instruction = RouterTradeBuilder.routeThreeHop(swap, FEE);
        assertEquals(4, Byte.toUnsignedInt(instruction.getData()[0]));
        assertEquals(0, u64(encodedLegData(instruction, 1), 8));
        assertEquals(20, u64(encodedLegData(instruction, 1), 16));
        assertEquals(0, u64(encodedLegData(instruction, 2), 8));
        assertEquals(1, u64(middle.data(), 8));
        assertEquals(RouterTradeBuilder.ata(USER, USDC, TOKEN),
                middle.accounts().get(5).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                middle.accounts().get(6).getPublicKey());
    }

    /** 三跳卖出时，PumpSwap sell 也可作为最后一跳，将第二跳实际收到的币换成 WSOL。 */
    @Test
    void pumpSellCanBeDynamicThreeHopFinalLeg() {
        RouterLeg first = LaunchLabLegs.sell(targetPool(), USER, 100, 40);
        RouterLeg middle = OrcaLegs.buy(usdcQuotePool(), USER, 1, 20);
        RouterLeg last = PumpAmmLegs.sell(pumpPool(USDC, WSOL), USER, 1, 10);
        RouterTradeBuilder.ThreeHopRouteSwap swap = threeHop(first, middle, last, true);
        TransactionInstruction instruction = RouterTradeBuilder.routeThreeHop(swap, FEE);
        assertEquals(4, Byte.toUnsignedInt(instruction.getData()[0]));
        assertEquals(0, u64(encodedLegData(instruction, 1), 8));
        assertEquals(0, u64(encodedLegData(instruction, 2), 8));
        assertEquals(10, u64(encodedLegData(instruction, 2), 16));
        assertEquals(1, u64(last.data(), 8));
        // 两个中间 ATA、一个输出 ATA，加上一条 Router 指令，共四条。
        assertEquals(4, RouterTradeBuilder.sell(swap, FEE).size());
    }

    /** 新版 Rust 已允许 PumpSwap 精确输入买入作为动态后续跳，旧卖出路径仍保留。 */
    @Test
    void pumpBuyIsAcceptedAsDynamicFollowingLeg() {
        RouterLeg first = LaunchLabLegs.sell(targetPool(), USER, 100, 40);
        RouterLeg buy = PumpAmmLegs.buy(pumpPool(WSOL, QUOTE), USER, 1, 10);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, buy, TOKEN, TOKEN, TOKEN, 100, 40, 10, false, false);
        assertArrayEquals(buy.data(), encodedLegData(RouterTradeBuilder.routeDynamic(swap, FEE), 1));
    }

    /**
     * 为本地编码测试准备三跳路径。
     * @param first 第一跳 DEX 指令
     * @param middle 第二跳 DEX 指令
     * @param last 第三跳 DEX 指令
     * @param dynamic 是否使用实际新增量模式
     * @return 测试用三跳参数
     */
    private static RouterTradeBuilder.ThreeHopRouteSwap threeHop(RouterLeg first, RouterLeg middle,
                                                                  RouterLeg last, boolean dynamic) {
        return new RouterTradeBuilder.ThreeHopRouteSwap(USER, first, middle, last,
                TOKEN, TOKEN, TOKEN, TOKEN, 100, 40, 20, 10, dynamic, true, true);
    }

    /** 准备 WSOL/USDC 的测试 CPMM 池，base 为 USDC、quote 为 WSOL。 */
    private static RaydiumLegs.Cpmm wsolUsdcPool() {
        return new RaydiumLegs.Cpmm(key(10), key(11), USDC, WSOL,
                key(12), key(13), TOKEN, TOKEN, key(14));
    }

    /** 准备 USDC/quote 的测试 Whirlpool 池，A 为 USDC、B 为 quote。 */
    private static OrcaLegs.Pool usdcQuotePool() {
        return new OrcaLegs.Pool(key(20), USDC, QUOTE, key(21), key(22), TOKEN, TOKEN,
                List.of(key(23), key(24), key(25)));
    }

    /** 准备 quote/目标币的测试 LaunchLab 池。 */
    private static LaunchLabLegs.Pool targetPool() {
        return new LaunchLabLegs.Pool(TARGET, QUOTE, TOKEN, TOKEN,
                key(30), key(31), key(32), key(33), key(34), key(35), key(36));
    }

    /**
     * 准备仅供本地编码测试的 PumpSwap 池。
     * @param base base 侧 mint
     * @param quote quote 侧 mint
     * @return 费用接收方有效、其余地址为占位值的测试池
     */
    private static PumpAmmLegs.Pool pumpPool(PublicKey base, PublicKey quote) {
        return new PumpAmmLegs.Pool(key(90), base, quote, key(91), key(92),
                TOKEN, TOKEN, new PublicKey("7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ"),
                key(93), key(94), key(95), null, false);
    }

    /**
     * 从 Router 指令中取出指定跳的原始 DEX 指令数据。
     * @param instruction 已构造的动态两跳或动态三跳 Router 指令
     * @param legIndex 从零开始的跳序号
     * @return 指定跳的 DEX 指令数据副本
     */
    private static byte[] encodedLegData(TransactionInstruction instruction, int legIndex) {
        byte[] data = instruction.getData();
        int offset = Byte.toUnsignedInt(data[0]) == 4 ? 35 : 27;
        for (int i = 0; i <= legIndex; i++) {
            offset += 33; // 跳程序地址占 32 字节，账户数量占 1 字节。
            int length = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
            offset += 2;
            if (i == legIndex) return Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
        }
        throw new IllegalArgumentException("跳序号超出范围");
    }

    /**
     * 根据重复字节生成稳定的测试公钥。
     * @param value 用于填充公钥的字节值
     * @return 仅供测试使用的公钥
     */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /**
     * 从指令数据指定位置读取小端 64 位金额。
     * @param data 待读取的指令数据
     * @param offset 金额字段的字节偏移
     * @return 解码后的原始金额
     */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
