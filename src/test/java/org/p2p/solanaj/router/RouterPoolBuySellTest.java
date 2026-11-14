package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 检查每种已实现池型的买卖入口和单跳 Router 编码；只使用测试地址。 */
class RouterPoolBuySellTest {
    /** 测试用户公钥。 */
    private static final PublicKey USER = key(1);
    /** 测试手续费接收方公钥。 */
    private static final PublicKey FEE = key(2);
    /** 测试目标币 mint。 */
    private static final PublicKey BASE = key(3);
    /** 测试支付币 mint。 */
    private static final PublicKey QUOTE = key(4);
    /** 经典 SPL Token 程序。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;

    /** 各池型买入为 quote 到 base，卖出为 base 到 quote，且可封装为标签 2。 */
    @Test
    void everyPoolHasBuyAndSellEntrypoints() {
        RaydiumLegs.AmmV4 v4 = new RaydiumLegs.AmmV4(key(10), BASE, QUOTE,
                key(11), key(12), TOKEN);
        RaydiumLegs.Cpmm cpmm = new RaydiumLegs.Cpmm(key(13), key(14), BASE, QUOTE,
                key(15), key(16), TOKEN, TOKEN, key(17));
        RaydiumLegs.Clmm clmm = new RaydiumLegs.Clmm(key(18), key(19), BASE, QUOTE,
                key(20), key(21), TOKEN, TOKEN, key(22), null, List.of(key(23)));
        MeteoraLegs.DammV2 damm = new MeteoraLegs.DammV2(key(24), BASE, QUOTE,
                key(25), key(26), TOKEN, TOKEN, null, false);
        MeteoraLegs.Dlmm dlmm = new MeteoraLegs.Dlmm(key(27), key(28), key(29),
                BASE, QUOTE, TOKEN, TOKEN, key(30), null, List.of(key(31)));
        OrcaLegs.Pool orca = new OrcaLegs.Pool(key(32), BASE, QUOTE,
                key(33), key(34), TOKEN, TOKEN, List.of(key(35), key(36), key(37)));
        PumpAmmLegs.Pool pump = new PumpAmmLegs.Pool(key(38), BASE, QUOTE,
                key(39), key(40), TOKEN, TOKEN, key(41), key(42), key(43),
                key(44), null, false);
        LaunchLabLegs.Pool launch = new LaunchLabLegs.Pool(BASE, QUOTE, TOKEN, TOKEN,
                key(45), key(46), key(47), key(48), key(49), key(50), key(51));
        PumpFunLegs.Pool inner = new PumpFunLegs.Pool(BASE, QUOTE, TOKEN, TOKEN,
                key(52), key(53), key(54), key(55), key(56));
        List<RouterLeg> buys = List.of(
                RaydiumLegs.buyAmmV4(v4, USER, 100, 1),
                RaydiumLegs.buyCpmm(cpmm, USER, 100, 1),
                RaydiumLegs.buyClmm(clmm, USER, 100, 1),
                MeteoraLegs.buyDammV2(damm, USER, 100, 1),
                MeteoraLegs.buyDlmm(dlmm, USER, 100, 1),
                OrcaLegs.buy(orca, USER, 100, 1),
                PumpAmmLegs.buy(pump, USER, 100, 1),
                PumpFunLegs.buy(inner, USER, 100, 1),
                LaunchLabLegs.buy(launch, USER, 100, 1));
        List<RouterLeg> sells = List.of(
                RaydiumLegs.sellAmmV4(v4, USER, 100, 1),
                RaydiumLegs.sellCpmm(cpmm, USER, 100, 1),
                RaydiumLegs.sellClmm(clmm, USER, 100, 1),
                MeteoraLegs.sellDammV2(damm, USER, 100, 1),
                MeteoraLegs.sellDlmm(dlmm, USER, 100, 1),
                OrcaLegs.sell(orca, USER, 100, 1),
                PumpAmmLegs.sell(pump, USER, 100, 1),
                PumpFunLegs.sell(inner, USER, 100, 1),
                LaunchLabLegs.sell(launch, USER, 100, 1));
        for (RouterLeg buy : buys) {
            assertEquals(QUOTE, buy.inputMint());
            assertEquals(BASE, buy.outputMint());
            RouterTradeBuilder.Swap swap = new RouterTradeBuilder.Swap(USER, QUOTE, BASE,
                    TOKEN, TOKEN, 100, 1, buy, false);
            assertEquals(2, Byte.toUnsignedInt(RouterTradeBuilder.route(swap, FEE).getData()[0]));
        }
        for (RouterLeg sell : sells) {
            assertEquals(BASE, sell.inputMint());
            assertEquals(QUOTE, sell.outputMint());
            RouterTradeBuilder.Swap swap = new RouterTradeBuilder.Swap(USER, BASE, QUOTE,
                    TOKEN, TOKEN, 100, 1, sell, false);
            assertEquals(2, Byte.toUnsignedInt(RouterTradeBuilder.route(swap, FEE).getData()[0]));
        }
    }

    /**
     * 根据重复字节创建稳定的本地测试公钥。
     * @param value 用于填充公钥的字节值
     * @return 仅供测试使用的公钥
     */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }
}
