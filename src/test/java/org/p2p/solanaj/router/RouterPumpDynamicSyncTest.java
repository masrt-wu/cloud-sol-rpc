package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/** 新版 Pump 内外盘动态路由与 Rust 字节、账户布局的离线回归测试；不连接 RPC、不提交交易。 */
class RouterPumpDynamicSyncTest {
    /** 仅用于本地构造的用户，不包含私钥。 */
    private static final PublicKey USER = fixtureKey(1);
    /** 仅用于测试的路由费用接收方。 */
    private static final PublicKey FEE = fixtureKey(2);
    /** 仅用于测试的稳定币 mint。 */
    private static final PublicKey USD = fixtureKey(3);
    /** 仅用于测试的中间 quote mint。 */
    private static final PublicKey QUOTE = fixtureKey(4);
    /** 仅用于测试的目标 base mint。 */
    private static final PublicKey BASE = fixtureKey(5);
    /** 经典代币程序。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;
    /** 扩展代币程序。 */
    private static final PublicKey TOKEN_2022 = key("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");

    /** 内盘买卖的全部账户顺序、可写标志、签名位置和数据与 Rust 构造器一致。 */
    @Test
    void innerV2LayoutMatchesRustForBothTokenPrograms() {
        for (PublicKey baseProgram : List.of(TOKEN, TOKEN_2022)) {
            for (PublicKey quoteProgram : List.of(TOKEN, TOKEN_2022)) {
                PumpFunLegs.Pool pool = innerPool(baseProgram, quoteProgram);
                RouterLeg buy = PumpFunLegs.buy(pool, USER, 123, 45);
                RouterLeg sell = PumpFunLegs.sell(pool, USER, 123, 45);
                assertArrayEquals(new byte[]{(byte) 194, (byte) 171, 28, 70, 104, 77, 91, 47},
                        Arrays.copyOf(buy.data(), 8));
                assertArrayEquals(new byte[]{93, (byte) 246, (byte) 130, 60, (byte) 231, (byte) 233, 64, (byte) 178},
                        Arrays.copyOf(sell.data(), 8));
                PublicKey userVolume = pda(PumpFunLegs.PROGRAM,
                        "user_volume_accumulator".getBytes(java.nio.charset.StandardCharsets.US_ASCII), USER.toByteArray());
                PublicKey feeProgram = key("pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ");
                List<PublicKey> common = List.of(
                        key("4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf"), BASE, QUOTE,
                        baseProgram, quoteProgram, AssociatedTokenProgram.PROGRAM_ID,
                        pool.feeRecipient(), ata(pool.feeRecipient(), QUOTE, quoteProgram),
                        pool.buybackFeeRecipient(), ata(pool.buybackFeeRecipient(), QUOTE, quoteProgram),
                        pool.bondingCurve(), pool.associatedBaseBondingCurve(),
                        ata(pool.bondingCurve(), QUOTE, quoteProgram), USER,
                        ata(USER, BASE, baseProgram), ata(USER, QUOTE, quoteProgram),
                        pool.creatorVault(), ata(pool.creatorVault(), QUOTE, quoteProgram),
                        pda(feeProgram, "sharing-config".getBytes(java.nio.charset.StandardCharsets.US_ASCII), BASE.toByteArray()));
                for (RouterLeg leg : List.of(buy, sell)) {
                    boolean isBuy = leg == buy;
                    assertEquals(isBuy ? 27 : 26, leg.accounts().size());
                    assertEquals(24, leg.data().length);
                    assertEquals(123, u64(leg.data(), 8));
                    assertEquals(45, u64(leg.data(), 16));
                    for (int i = 0; i < common.size(); i++)
                        assertMeta(leg.accounts().get(i), common.get(i), i == 13, i >= 6 && i <= 17);
                    int tail = 19;
                    if (isBuy) assertMeta(leg.accounts().get(tail++),
                            key("Hq2wp8uJ9jCPsYgNHex8RtqdvMPfVGoYwjvF1ATiwn2Y"), false, false);
                    assertMeta(leg.accounts().get(tail++), userVolume, false, true);
                    assertMeta(leg.accounts().get(tail++), ata(userVolume, QUOTE, quoteProgram), false, true);
                    assertMeta(leg.accounts().get(tail++), key("8Wf5TiAheLUqBrKXeYg2JtAFFMWtKdG2BSFgqUcPVwTt"), false, false);
                    assertMeta(leg.accounts().get(tail++), feeProgram, false, false);
                    assertMeta(leg.accounts().get(tail++), SystemProgram.PROGRAM_ID, false, false);
                    assertMeta(leg.accounts().get(tail++), key("Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1"), false, false);
                    assertMeta(leg.accounts().get(tail), PumpFunLegs.PROGRAM, false, false);
                    assertEquals(isBuy ? QUOTE : BASE, leg.inputMint());
                    assertEquals(isBuy ? BASE : QUOTE, leg.outputMint());
                }
            }
        }
    }

    /** 两跳买入和卖出均接受内盘 V2；覆盖两侧 Token Program 的全部组合。 */
    @Test
    void innerDynamicTwoHopBuyAndSell() {
        for (PublicKey baseProgram : List.of(TOKEN, TOKEN_2022)) {
            for (PublicKey quoteProgram : List.of(TOKEN, TOKEN_2022)) {
                PumpFunLegs.Pool pool = innerPool(baseProgram, quoteProgram);
                RouterLeg first = exchange(WSOL_MINT, TOKEN, QUOTE, quoteProgram, 100, 40);
                RouterLeg last = PumpFunLegs.buy(pool, USER, 1, 10);
                DynamicRouteSwap buy = two(first, last, TOKEN, quoteProgram, baseProgram);
                assertEquals(3, routeDynamic(buy, FEE).getData()[0]);
                assertArrayEquals(last.data(), legData(routeDynamic(buy, FEE), 1));
                assertEquals(3, RouterTradeBuilder.buy(buy, FEE).size());
                assertEquals(6, RouterTradeBuilder.buyWithSol(buy, FEE).size());
                DynamicRouteSwap sell = two(PumpFunLegs.sell(pool, USER, 100, 40),
                        exchange(QUOTE, quoteProgram, WSOL_MINT, TOKEN, 1, 10),
                        baseProgram, quoteProgram, TOKEN);
                assertEquals(3, routeDynamic(sell, FEE).getData()[0]);
                assertEquals(3, RouterTradeBuilder.sell(sell, FEE).size());
                // 内盘卖出也可作为动态后续跳，不仅限于第一跳。
                DynamicRouteSwap followingSell = two(exchange(USD, TOKEN, BASE, baseProgram, 100, 40),
                        PumpFunLegs.sell(pool, USER, 1, 10), TOKEN, baseProgram, quoteProgram);
                assertEquals(3, routeDynamic(followingSell, FEE).getData()[0]);
            }
        }
    }

    /** 三跳往返：WSOL→稳定币→quote→内盘币；卖出按相反顺序，保留各跳最低输出。 */
    @Test
    void innerDynamicThreeHopBuyAndSell() {
        PumpFunLegs.Pool pool = innerPool(TOKEN_2022, TOKEN_2022);
        ThreeHopRouteSwap buy = three(
                exchange(WSOL_MINT, TOKEN, USD, TOKEN, 100, 40),
                exchange(USD, TOKEN, QUOTE, TOKEN_2022, 1, 20),
                PumpFunLegs.buy(pool, USER, 1, 10), TOKEN, TOKEN, TOKEN_2022, TOKEN_2022);
        assertThree(buy);
        assertEquals(7, RouterTradeBuilder.buyWithSol(buy, FEE).size());
        ThreeHopRouteSwap sell = three(PumpFunLegs.sell(pool, USER, 100, 40),
                exchange(QUOTE, TOKEN_2022, USD, TOKEN, 1, 20),
                exchange(USD, TOKEN, WSOL_MINT, TOKEN, 1, 10), TOKEN_2022, TOKEN_2022, TOKEN, TOKEN);
        assertThree(sell);
        assertEquals(4, RouterTradeBuilder.sell(sell, FEE).size());
    }

    /** 内盘 V2 买卖可放在第三跳或中间跳，索引始终按买卖方向区分。 */
    @Test
    void innerDynamicMiddleAndFinalSell() {
        PumpFunLegs.Pool pool = innerPool(TOKEN_2022, TOKEN);
        assertThree(three(exchange(WSOL_MINT, TOKEN, QUOTE, TOKEN, 100, 40),
                PumpFunLegs.buy(pool, USER, 1, 20), exchange(BASE, TOKEN_2022, USD, TOKEN, 1, 10),
                TOKEN, TOKEN, TOKEN_2022, TOKEN));
        assertThree(three(exchange(WSOL_MINT, TOKEN, BASE, TOKEN_2022, 100, 40),
                PumpFunLegs.sell(pool, USER, 1, 20), exchange(QUOTE, TOKEN, USD, TOKEN, 1, 10),
                TOKEN, TOKEN_2022, TOKEN, TOKEN));
        assertThree(three(exchange(WSOL_MINT, TOKEN, USD, TOKEN, 100, 40),
                exchange(USD, TOKEN, BASE, TOKEN_2022, 1, 20), PumpFunLegs.sell(pool, USER, 1, 10),
                TOKEN, TOKEN, TOKEN_2022, TOKEN));
    }

    /** PumpSwap 外盘动态买入支持两跳、三跳和中间跳，费用 ATA 按真实 quote 程序推导。 */
    @Test
    void outerDynamicBuyAndQuoteFeeAta() {
        PumpAmmLegs.Pool pool = outerPool();
        RouterLeg buyLeg = PumpAmmLegs.buy(pool, USER, 1, 10);
        assertEquals(25, buyLeg.data().length);
        assertEquals(1, buyLeg.data()[24]);
        assertMeta(buyLeg.accounts().get(10), ata(pool.protocolFeeRecipient(), QUOTE, TOKEN_2022), false, true);
        assertMeta(buyLeg.accounts().get(19), key("C2aFPdENg4A2HQsmrd5rTw5TaYBX5Ku887cWjbFKtZpw"), false, false);
        DynamicRouteSwap buy = two(exchange(WSOL_MINT, TOKEN, QUOTE, TOKEN_2022, 100, 40),
                buyLeg, TOKEN, TOKEN_2022, TOKEN_2022);
        assertEquals(3, routeDynamic(buy, FEE).getData()[0]);
        assertThree(three(exchange(WSOL_MINT, TOKEN, USD, TOKEN, 100, 40),
                exchange(USD, TOKEN, QUOTE, TOKEN_2022, 1, 20), buyLeg,
                TOKEN, TOKEN, TOKEN_2022, TOKEN_2022));
        assertThree(three(exchange(WSOL_MINT, TOKEN, QUOTE, TOKEN_2022, 100, 40),
                PumpAmmLegs.buy(pool, USER, 1, 20), exchange(BASE, TOKEN_2022, USD, TOKEN, 1, 10),
                TOKEN, TOKEN_2022, TOKEN_2022, TOKEN));
        DynamicRouteSwap sell = two(PumpAmmLegs.sell(pool, USER, 100, 40),
                exchange(QUOTE, TOKEN_2022, WSOL_MINT, TOKEN, 1, 10), TOKEN_2022, TOKEN_2022, TOKEN);
        assertEquals(3, routeDynamic(sell, FEE).getData()[0]);
    }

    /** 与 Rust 一致，仅允许精确输入形状；拒绝内盘 V1、精确输出、额外字节和外盘非法布尔值。 */
    @Test
    void malformedPumpDynamicInstructionsAreRejected() {
        RouterLeg first = exchange(WSOL_MINT, TOKEN, QUOTE, TOKEN, 100, 40);
        RouterLeg good = PumpFunLegs.buy(innerPool(TOKEN, TOKEN), USER, 1, 10);
        byte[] wrongDiscriminator = good.data();
        wrongDiscriminator[0] ^= 1;
        for (byte[] invalid : List.of(wrongDiscriminator, Arrays.copyOf(good.data(), 23), Arrays.copyOf(good.data(), 25)))
            assertThrows(IllegalArgumentException.class, () -> two(first, withData(good, invalid), TOKEN, TOKEN, TOKEN));
        RouterLeg shortAccounts = new RouterLeg(good.programId(), good.accounts().subList(0, 26), good.data(),
                good.inputMint(), good.outputMint(), 1, 10);
        assertThrows(IllegalArgumentException.class, () -> two(first, shortAccounts, TOKEN, TOKEN, TOKEN));
        RouterLeg outer = PumpAmmLegs.buy(outerPool(), USER, 1, 10);
        RouterLeg outerFirst = exchange(WSOL_MINT, TOKEN, QUOTE, TOKEN_2022, 100, 40);
        byte[] invalidFlag = outer.data();
        invalidFlag[24] = 2;
        assertThrows(IllegalArgumentException.class, () -> two(outerFirst, withData(outer, invalidFlag), TOKEN, TOKEN_2022, TOKEN_2022));
        assertThrows(IllegalArgumentException.class, () -> two(outerFirst,
                withData(outer, Arrays.copyOf(outer.data(), 26)), TOKEN, TOKEN_2022, TOKEN_2022));
        byte[] zeroFlag = outer.data();
        zeroFlag[24] = 0;
        assertDoesNotThrow(() -> two(outerFirst, withData(outer, zeroFlag), TOKEN, TOKEN_2022, TOKEN_2022));
    }

    /** 原生 SOL 内盘不能被误编码为 WSOL ATA 路由，手工伪造账户也不能绕过动态识别。 */
    @Test
    void nativeSolInnerQuoteIsRejected() {
        PumpFunLegs.Pool p = innerPool(TOKEN, TOKEN);
        PumpFunLegs.Pool nativePool = new PumpFunLegs.Pool(BASE, WSOL_MINT, TOKEN, TOKEN,
                p.bondingCurve(), p.associatedBaseBondingCurve(), p.creatorVault(), p.feeRecipient(), p.buybackFeeRecipient());
        assertThrows(IllegalArgumentException.class, () -> PumpFunLegs.buy(nativePool, USER, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> PumpFunLegs.sell(nativePool, USER, 1, 10));
        RouterLeg good = PumpFunLegs.buy(p, USER, 1, 10);
        List<AccountMeta> accounts = new java.util.ArrayList<>(good.accounts());
        accounts.set(2, readonly(WSOL_MINT));
        RouterLeg forged = new RouterLeg(good.programId(), accounts, good.data(), QUOTE, BASE, 1, 10);
        assertThrows(IllegalArgumentException.class, () -> two(
                exchange(USD, TOKEN, QUOTE, TOKEN, 100, 40), forged, TOKEN, TOKEN, TOKEN));
    }

    /** 相同池快照支持旧单跳和固定金额三跳，固定金额标签与输入字节保持不变。 */
    @Test
    void legacyFixedAndSingleHopRemainAvailable() {
        RouterLeg leg = PumpFunLegs.buy(innerPool(TOKEN_2022, TOKEN), USER, 100, 10);
        Swap single = new Swap(USER, QUOTE, BASE, TOKEN, TOKEN_2022, 100, 10, leg, true);
        assertEquals(2, RouterTradeBuilder.buy(single, FEE).get(1).getData()[0]);
        ThreeHopRouteSwap fixed = new ThreeHopRouteSwap(USER,
                exchange(WSOL_MINT, TOKEN, USD, TOKEN, 100, 40),
                exchange(USD, TOKEN, QUOTE, TOKEN, 40, 20),
                PumpFunLegs.buy(innerPool(TOKEN_2022, TOKEN), USER, 20, 10),
                TOKEN, TOKEN, TOKEN, TOKEN_2022, 100, 40, 20, 10, false, true, true);
        assertEquals(2, routeThreeHop(fixed, FEE).getData()[0]);
    }

    /**
     * 使用模拟 RPC 核对内盘 owner 检查读取索引 10 的曲线，而不是索引 0 的全局配置。
     * @throws Exception 反射调用或模拟 RPC 配置失败时抛出
     */
    @Test
    void traderChecksInnerCurveOwnerWithoutAnyNetwork() throws Exception {
        PumpFunLegs.Pool pool = innerPool(TOKEN, TOKEN);
        org.p2p.solanaj.rpc.RpcApi rpc = org.mockito.Mockito.mock(org.p2p.solanaj.rpc.RpcApi.class);
        org.p2p.solanaj.rpc.types.AccountInfo info = org.mockito.Mockito.mock(org.p2p.solanaj.rpc.types.AccountInfo.class);
        org.p2p.solanaj.rpc.types.AccountInfo.Value value = org.mockito.Mockito.mock(org.p2p.solanaj.rpc.types.AccountInfo.Value.class);
        org.mockito.Mockito.when(rpc.getAccountInfo(pool.bondingCurve())).thenReturn(info);
        org.mockito.Mockito.when(info.getValue()).thenReturn(value);
        org.mockito.Mockito.when(value.getOwner()).thenReturn(PumpFunLegs.PROGRAM.toBase58());
        java.lang.reflect.Method method = RouterTrader.class.getDeclaredMethod("verifyPoolOwner", RouterLeg.class);
        method.setAccessible(true);
        method.invoke(new RouterTrader(rpc), PumpFunLegs.buy(pool, USER, 1, 10));
        org.mockito.Mockito.verify(rpc).getAccountInfo(pool.bondingCurve());
        org.mockito.Mockito.verifyNoMoreInteractions(rpc);
    }

    /**
     * 生成使用不同代币程序的内盘测试快照。
     * @param baseProgram base 所属代币程序
     * @param quoteProgram quote 所属代币程序
     * @return 不对应真实链上池的快照
     */
    private static PumpFunLegs.Pool innerPool(PublicKey baseProgram, PublicKey quoteProgram) {
        return new PumpFunLegs.Pool(BASE, QUOTE, baseProgram, quoteProgram,
                fixtureKey(10), fixtureKey(11), fixtureKey(12), fixtureKey(13), fixtureKey(14));
    }

    /** 返回费用接收方不依赖旧 WSOL 固定映射的外盘测试池。 */
    private static PumpAmmLegs.Pool outerPool() {
        return new PumpAmmLegs.Pool(fixtureKey(20), BASE, QUOTE, fixtureKey(21), fixtureKey(22),
                TOKEN_2022, TOKEN_2022, fixtureKey(23), fixtureKey(24), fixtureKey(25), fixtureKey(26), null, false);
    }

    /**
     * 使用 CPMM 构造任意方向的连接跳，金额均为最小单位。
     * @param input 输入 mint
     * @param inputProgram 输入代币程序
     * @param output 输出 mint
     * @param outputProgram 输出代币程序
     * @param amount 精确输入量
     * @param minimum 最低输出量
     * @return 测试连接跳
     */
    private static RouterLeg exchange(PublicKey input, PublicKey inputProgram, PublicKey output,
                                      PublicKey outputProgram, long amount, long minimum) {
        return RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(fixtureKey(30), fixtureKey(31), input, output,
                fixtureKey(32), fixtureKey(33), inputProgram, outputProgram, fixtureKey(34)), USER, input, amount, minimum);
    }

    /**
     * 生成两跳测试参数，第二跳用金额 1 作为链上替换的占位量。
     * @param first 第一跳
     * @param last 第二跳
     * @param inProgram 初始代币程序
     * @param quoteProgram 中间代币程序
     * @param outProgram 最终代币程序
     * @return 动态两跳参数
     */
    private static DynamicRouteSwap two(RouterLeg first, RouterLeg last,
                                       PublicKey inProgram, PublicKey quoteProgram, PublicKey outProgram) {
        return new DynamicRouteSwap(USER, first, last, inProgram, quoteProgram, outProgram, 100, 40, 10, true, true);
    }

    /**
     * 生成三跳测试参数，后两跳金额 1 会编码成链上约定的零。
     * @param first 第一跳
     * @param middle 第二跳
     * @param last 第三跳
     * @param inProgram 初始代币程序
     * @param firstProgram 第一中间代币程序
     * @param secondProgram 第二中间代币程序
     * @param outProgram 最终代币程序
     * @return 动态三跳参数
     */
    private static ThreeHopRouteSwap three(RouterLeg first, RouterLeg middle, RouterLeg last,
                                           PublicKey inProgram, PublicKey firstProgram,
                                           PublicKey secondProgram, PublicKey outProgram) {
        return new ThreeHopRouteSwap(USER, first, middle, last, inProgram, firstProgram,
                secondProgram, outProgram, 100, 40, 20, 10, true, true, true);
    }

    /**
     * 核对三跳标签、金额占位、最低输出及源对象没有被编码过程修改。
     * @param swap 待编码的三跳参数
     */
    private static void assertThree(ThreeHopRouteSwap swap) {
        TransactionInstruction ix = routeThreeHop(swap, FEE);
        assertEquals(4, ix.getData()[0]);
        assertEquals(100, u64(legData(ix, 0), 8));
        for (int i = 1; i < 3; i++) {
            byte[] encoded = legData(ix, i);
            assertEquals(0, u64(encoded, 8));
            assertEquals(i == 1 ? 20 : 10, u64(encoded, 16));
            assertEquals(1, u64(swap.legs().get(i).data(), 8));
        }
    }

    /**
     * 在不改变元数据的前提下替换指令数据，用于非法指令回归测试。
     * @param leg 原始跳
     * @param data 替换后的指令字节
     * @return 替换后的跳
     */
    private static RouterLeg withData(RouterLeg leg, byte[] data) {
        return new RouterLeg(leg.programId(), leg.accounts(), data, leg.inputMint(), leg.outputMint(), 1, 10);
    }

    /**
     * 核对某个 CPI 账户的完整权限。
     * @param meta 实际账户描述
     * @param key 期望地址
     * @param signer 是否签名
     * @param writable 是否可写
     */
    private static void assertMeta(AccountMeta meta, PublicKey key, boolean signer, boolean writable) {
        assertEquals(key, meta.getPublicKey());
        assertEquals(signer, meta.isSigner());
        assertEquals(writable, meta.isWritable());
    }

    /**
     * 从动态路由字节中取出某一跳的指令数据。
     * @param ix 动态两跳或三跳路由
     * @param index 从零开始的跳序号
     * @return 该跳的数据副本
     */
    private static byte[] legData(TransactionInstruction ix, int index) {
        byte[] data = ix.getData();
        int offset = data[0] == 4 ? 35 : 27;
        for (int i = 0; i <= index; i++) {
            offset += 33;
            int length = (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
            offset += 2;
            if (i == index) return Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
        }
        throw new IllegalArgumentException("测试跳序号错误");
    }

    /**
     * 读取小端金额。
     * @param data 指令字节
     * @param offset 金额偏移
     * @return 原始金额
     */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /**
     * 返回不含私钥的稳定测试公钥。
     * @param value 填充字节
     * @return 测试公钥
     */
    private static PublicKey fixtureKey(int value) {
        byte[] data = new byte[32];
        Arrays.fill(data, (byte) value);
        return new PublicKey(data);
    }
}
