package org.p2p.solanaj.router;

import org.p2p.solanaj.utils.Env;

import cn.hutool.core.codec.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.router.pda.MeteoraDlmmPda;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最新 Rust 动态路由的买入和卖出构造示例。
 * 池账户使用测试公钥；测试只验证本地构造，不发起 RPC 请求、模拟或提交交易。
 * 买入测试中保留了原有的硬编码签名者示例；运行前应确认其不是任何真实钱包的私钥。
 * 只有已部署支持指令标签 3 的链上 Router，才能执行这里构造的动态两跳指令。
 */
class RouterDynamicBuySellTest {
    /** 模拟交易用户的占位公钥。 */
    private static final PublicKey USER = key(1);
    /** 模拟 Router 手续费接收方的占位公钥。 */
    private static final PublicKey FEE_RECIPIENT = key(2);
    /** 模拟两跳之间 quote 代币的占位 mint。 */
    private static final PublicKey QUOTE = key(3);
    /** 模拟最终目标代币的占位 mint。 */
    private static final PublicKey TARGET = key(4);
    /** 第一跳买入和第二跳卖出的 WSOL mint。 */
    private static final PublicKey WSOL = RouterTradeBuilder.WSOL_MINT;
    /** 经典 SPL Token 程序地址。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;
    /** Token-2022 程序地址，用于测试目标代币账户推导。 */
    private static final PublicKey TOKEN_2022 = TokenProgram.TOKEN_2022;

    /**
     * 买入：WSOL 经 Meteora DLMM 买 quote，再经 LaunchLab 买目标币。
     * 第二跳的输入量 1 只是占位；支持标签 3 的链上 Router 会替换成第一跳实际收到的 quote。
     * 本测试只检查编码，不验证链上的实际成交数量。
     */
    @Test
    void buyUsesActualQuoteReceivedByFirstHop() {
        //Token Mint Y Program Flag
        //Token Mint X Program Flag
        //0 -> TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
        //1 -> TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
        // 保留原有签名者构造代码；本测试不使用它签名，也不发送交易。
        Account signer = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://64.130.57.42:8899";
        RpcApi rpc = new RpcApi(new RpcClient(url));
        BigDecimal buySol = new BigDecimal("0.001");
        long wsolInput = buySol.movePointRight(9).longValue();
        long quoteMinimum = 1L;
        long targetMinimum = 1L;
        PublicKey lpAddress = new PublicKey("E3SotafntrgRg9XjppxqoJWSR4GUaJV7sA8u4a89rYo6");
        PublicKey quoteMint = new PublicKey("Ge87EtsjwRQbHaqQmKRno69RFTwh9bfSsm99XNxTpump");
        PublicKey quoteQuote = new PublicKey("So11111111111111111111111111111111111111112");
        PublicKey reserveX = MeteoraDlmmPda.reserveX(lpAddress,quoteMint);
        PublicKey reserveY = MeteoraDlmmPda.reserveY(lpAddress,quoteQuote);
        // 根据链上 Token Mint X Program Flag
        PublicKey tokenProgramX = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        //Token Mint Y Program Flag
        PublicKey tokenProgramY = TOKEN;
        PublicKey oracle = MeteoraDlmmPda.oracle(lpAddress);

        //公式 active_id / 70  四舍五入 9  bin_array_bitmap 不存在 9
        // 需要访问的 BinArray index 是否超出了 LbPair 内置 bitmap 的范围
        PublicKey bitmapExtension = MeteoraDlmmPda.bitmapExtension(lpAddress);
        bitmapExtension = null;
        // 可能需要读取 几分钟读取一次
        List<PublicKey> binArrays = new ArrayList<>();
        binArrays.add(MeteoraDlmmPda.binArray(lpAddress,9));

        MeteoraLegs.Dlmm  pool = new MeteoraLegs.Dlmm(lpAddress, reserveX, reserveY,
                quoteMint, quoteQuote, tokenProgramX, tokenProgramY, oracle, bitmapExtension, binArrays);
        RouterLeg first = MeteoraLegs.dlmm(pool, USER, WSOL,
                wsolInput, quoteMinimum);
        LaunchLabLegs.Pool matchingTargetPool = new LaunchLabLegs.Pool(
                TARGET, quoteMint, TOKEN_2022, TOKEN,
                key(20), key(21), key(22), key(23), key(24), key(25), key(26));
        RouterLeg second = LaunchLabLegs.buyExactIn(matchingTargetPool, USER,
                1L, targetMinimum);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, second, TOKEN, TOKEN, TOKEN_2022,
                wsolInput, quoteMinimum, targetMinimum, true, true);

        // 原生 SOL 路径先创建并充值 WSOL，再创建 quote/目标币账户，最后执行动态路由。
        List<TransactionInstruction> instructions = RouterTradeBuilder.buyWithSol(
                swap, FEE_RECIPIENT);
        TransactionInstruction route = instructions.get(instructions.size() - 1);
        assertEquals(6, instructions.size());
        assertEquals(RouterTradeBuilder.ROUTER_PROGRAM, route.getProgramId());
        assertEquals(3, Byte.toUnsignedInt(route.getData()[0]));
        assertEquals(wsolInput, u64(route.getData(), 1));
        assertEquals(targetMinimum, u64(route.getData(), 9));
        assertEquals(quoteMinimum, u64(route.getData(), 19));
        assertEquals(1L, u64(second.data(), 8));
        assertEquals(RouterTradeBuilder.ata(USER, quoteMint, TOKEN),
                route.getKeys().get(6).getPublicKey());
        assertTrue(route.getKeys().get(6).isWritable());
    }

    /**
     * 卖出：LaunchLab 卖目标币得到 quote，再经 Raydium CPMM 换回 WSOL。
     * 动态路由只花本笔第一跳新收到的 quote，不会连同账户原有的 quote 一起花掉。
     * 这里的最终输出是 WSOL 代币，不会自动解包成原生 SOL。
     */
    @Test
    void sellUsesActualQuoteReceivedByFirstHop() {
        long targetInput = 5_000_000L;
        long quoteMinimum = 1_000_000L;
        long wsolMinimum = 100L;
        RouterLeg first = LaunchLabLegs.sellExactIn(targetPool(), USER,
                targetInput, quoteMinimum);
        RouterLeg second = RaydiumLegs.cpmm(exitPool(), USER, QUOTE,
                1L, wsolMinimum);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, second, TOKEN_2022, TOKEN, TOKEN,
                targetInput, quoteMinimum, wsolMinimum, true, true);

        List<TransactionInstruction> instructions = RouterTradeBuilder.sell(
                swap, FEE_RECIPIENT);
        TransactionInstruction route = instructions.get(instructions.size() - 1);
        assertEquals(3, instructions.size());
        assertEquals(3, Byte.toUnsignedInt(route.getData()[0]));
        assertEquals(targetInput, u64(route.getData(), 1));
        assertEquals(wsolMinimum, u64(route.getData(), 9));
        assertEquals(quoteMinimum, u64(route.getData(), 19));
        assertEquals(1L, u64(second.data(), 8));
        assertEquals(RouterTradeBuilder.ata(USER, TARGET, TOKEN_2022),
                route.getKeys().get(3).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, WSOL, TOKEN),
                route.getKeys().get(4).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                route.getKeys().get(6).getPublicKey());
    }

    /** 新版动态两跳允许 DLMM 精确输入作为卖出第二跳。 */
    @Test
    void sellSupportsDlmmAsSecondHop() {
        RouterLeg first = LaunchLabLegs.sellExactIn(targetPool(), USER, 5_000_000L, 1_000_000L);
        RouterLeg second = MeteoraLegs.dlmm(quotePool(), USER, QUOTE, 1L, 100L);
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, first, second, TOKEN_2022, TOKEN, TOKEN,
                5_000_000L, 1_000_000L, 100L, true, true);
        assertEquals(3, Byte.toUnsignedInt(RouterTradeBuilder.routeDynamic(
                swap, FEE_RECIPIENT).getData()[0]));
    }

    /** 测试公钥仅用于验证编码；真实交易必须传入已核实的当前池账户。 */
    private static MeteoraLegs.Dlmm quotePool() {
        return new MeteoraLegs.Dlmm(key(10), key(11), key(12),
                WSOL, QUOTE, TOKEN, TOKEN, key(13), null, List.of(key(14)));
    }

    /** 生成供本地编码测试使用的 LaunchLab 池账户。 */
    private static LaunchLabLegs.Pool targetPool() {
        return new LaunchLabLegs.Pool(TARGET, QUOTE, TOKEN_2022, TOKEN,
                key(20), key(21), key(22), key(23), key(24), key(25), key(26));
    }

    /** 生成供卖出路径使用的 CPMM 池账户。 */
    private static RaydiumLegs.Cpmm exitPool() {
        return new RaydiumLegs.Cpmm(key(30), key(31), QUOTE, WSOL,
                key(32), key(33), TOKEN, TOKEN, key(34));
    }

    /** 根据固定字节生成测试公钥。 */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /** 从 Router 指令中按小端格式读取 64 位金额。 */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
