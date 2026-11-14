package org.p2p.solanaj.router;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.utils.TweetNaclFast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证固定金额多跳路由的字节布局、账户准备和 v0 交易封装。 */
class RouterMultiHopTest {
    /** 模拟交易用户的占位公钥。 */
    private static final PublicKey USER = key(1);
    /** 模拟中间 quote 代币的占位 mint。 */
    private static final PublicKey QUOTE = key(2);
    /** 模拟最终目标代币的占位 mint。 */
    private static final PublicKey MINT = key(3);
    /** 模拟 Router 手续费接收方的占位公钥。 */
    private static final PublicKey FEE_RECIPIENT = key(4);

    /** 检查 DLMM 到 LaunchLab 的两跳编码与 Rust 端固定金额格式一致。 */
    @Test
    void dlmmThenLaunchLabMatchesRustRouteEnvelope() {
        RouterLeg dlmm = dlmm(500_000_000, 1_000_000);
        RouterLeg launch = launch(1_000_000, 100);
        RouterTradeBuilder.RouteSwap swap = route(dlmm, launch);
        TransactionInstruction ix = RouterTradeBuilder.route(swap, FEE_RECIPIENT);
        byte[] bytes = ix.getData();
        assertEquals(2, Byte.toUnsignedInt(bytes[0]));
        assertEquals(500_000_000, u64(bytes, 1));
        assertEquals(100, u64(bytes, 9));
        assertEquals(1, Byte.toUnsignedInt(bytes[17])); // 手续费来源为 SPL 代币或 WSOL。
        assertEquals(2, Byte.toUnsignedInt(bytes[18])); // 共封装两跳 CPI。

        int cursor = 19;
        for (RouterLeg leg : List.of(dlmm, launch)) {
            assertArrayEquals(leg.programId().toByteArray(), Arrays.copyOfRange(bytes, cursor, cursor + 32));
            cursor += 32;
            assertEquals(leg.accounts().size(), Byte.toUnsignedInt(bytes[cursor++]));
            int length = Short.toUnsignedInt(ByteBuffer.wrap(bytes, cursor, 2)
                    .order(ByteOrder.LITTLE_ENDIAN).getShort());
            cursor += 2;
            assertEquals(leg.data().length, length);
            assertArrayEquals(leg.data(), Arrays.copyOfRange(bytes, cursor, cursor + length));
            cursor += length;
        }
        assertEquals(bytes.length, cursor);
        assertEquals(6 + dlmm.accounts().size() + launch.accounts().size() + 2, ix.getKeys().size());
        assertEquals(MeteoraLegs.DLMM, ix.getKeys().get(ix.getKeys().size() - 2).getPublicKey());
        assertEquals(LaunchLabLegs.PROGRAM, ix.getKeys().get(ix.getKeys().size() - 1).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, MINT, TokenProgram.TOKEN_2022),
                ix.getKeys().get(4).getPublicKey());
        assertEquals(LaunchLabLegs.PROGRAM, launch.programId());
        assertArrayEquals(new byte[]{(byte) 250, (byte) 234, 13, 123,
                (byte) 213, (byte) 156, 19, (byte) 236}, Arrays.copyOfRange(launch.data(), 0, 8));
        assertEquals(1_000_000, u64(launch.data(), 8));
        assertEquals(0, u64(launch.data(), 24)); // 分成费率为零。
        assertEquals(key(34), launch.accounts().get(4).getPublicKey()); // 核对池状态账户位置。
    }

    /** 验证充值 WSOL、同步余额及创建中间代币账户都排在路由指令之前。 */
    @Test
    void nativeSolSetupAndIntermediateAtaPrecedeRoute() {
        RouterTradeBuilder.RouteSwap swap = route(dlmm(500_000_000, 1_000_000), launch(1_000_000, 100));
        List<TransactionInstruction> ixs = RouterTradeBuilder.buyWithSol(swap, FEE_RECIPIENT);
        assertEquals(6, ixs.size());
        assertEquals(AssociatedTokenProgram.PROGRAM_ID, ixs.get(0).getProgramId());
        assertEquals(SystemProgram.PROGRAM_ID, ixs.get(1).getProgramId());
        assertEquals(500_000_000, u64(ixs.get(1).getData(), 4));
        assertEquals(TokenProgram.PROGRAM_ID, ixs.get(2).getProgramId());
        assertEquals(17, Byte.toUnsignedInt(ixs.get(2).getData()[0])); // 检查同步原生代币余额指令。
        assertFalse(ixs.get(2).getKeys().get(0).isSigner()); // ATA 是 PDA，不需要也不能签名。
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TokenProgram.PROGRAM_ID),
                ixs.get(3).getKeys().get(1).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, MINT, TokenProgram.TOKEN_2022),
                ixs.get(4).getKeys().get(1).getPublicKey());
        assertEquals(RouterTradeBuilder.ROUTER_PROGRAM, ixs.get(5).getProgramId());
    }

    /** 拒绝代币不连通或第二跳花费超过第一跳最低保证值的配置。 */
    @Test
    void rejectsDisconnectedOrUnsafeHops() {
        RouterLeg first = dlmm(500_000_000, 1_000_000);
        assertThrows(IllegalArgumentException.class,
                () -> route(first, launch(1_000_001, 100)));
        RouterLeg reversed = LaunchLabLegs.sellExactIn(launchPool(), USER, 1_000_000, 100);
        assertThrows(IllegalArgumentException.class, () -> route(first, reversed));
    }

    /** 验证 v0 地址查找表能正确承载路由账户。 */
    @Test
    void standardV0LoadsRouteAccountsFromLookupTable() {
        Account signer = new Account();
        PublicKey user = signer.getPublicKey();
        RouterLeg quoteLeg = MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(10), key(11), key(12),
                RouterTradeBuilder.WSOL_MINT, QUOTE, TokenProgram.PROGRAM_ID,
                TokenProgram.PROGRAM_ID, key(13), null, List.of(key(14))),
                user, RouterTradeBuilder.WSOL_MINT, 500_000_000, 1_000_000);
        RouterLeg mintLeg = LaunchLabLegs.buyExactIn(launchPool(), user, 1_000_000, 100);
        RouterTradeBuilder.RouteSwap route = new RouterTradeBuilder.RouteSwap(user,
                List.of(quoteLeg, mintLeg),
                List.of(TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022),
                500_000_000, 100, true, true);
        List<TransactionInstruction> ixs = RouterTradeBuilder.buyWithSol(route, FEE_RECIPIENT);
        LinkedHashSet<PublicKey> all = new LinkedHashSet<>();
        for (TransactionInstruction ix : ixs) {
            for (AccountMeta meta : ix.getKeys())
                if (!meta.isSigner()) all.add(meta.getPublicKey());
        }
        RouterV0Transaction.LookupTable table = new RouterV0Transaction.LookupTable(
                key(60), new ArrayList<>(all));
        byte[] wire = RouterV0Transaction.compileAndSign(signer, key(61).toBase58(),
                ixs, List.of(table));
        assertTrue(wire.length <= 1232);
        assertEquals(1, Byte.toUnsignedInt(wire[0])); // 精简长度编码表示一个签名。
        assertEquals(0x80, Byte.toUnsignedInt(wire[65])); // 标准 v0 消息版本前缀。
        assertEquals(1, Byte.toUnsignedInt(wire[66])); // 仅有一个必需签名者。
        assertTrue(new TweetNaclFast.Signature(user.toByteArray(), new byte[0])
                .detached_verify(Arrays.copyOfRange(wire, 65, wire.length),
                        Arrays.copyOfRange(wire, 1, 65)));
        assertThrows(IllegalArgumentException.class, () ->
                RouterV0Transaction.compileAndSign(signer, key(61).toBase58(), ixs, List.of()));
    }

    /** 工厂方法必须校验两个池子的 mint 衔接关系。 */
    @Test
    void dlmmLaunchLabFactoryChecksAndConnectsPoolMints() {
        MeteoraLegs.Dlmm quotePool = new MeteoraLegs.Dlmm(key(10), key(11), key(12),
                RouterTradeBuilder.WSOL_MINT, QUOTE, TokenProgram.PROGRAM_ID,
                TokenProgram.PROGRAM_ID, key(13), null, List.of(key(14)));
        RouterTradeBuilder.RouteSwap swap = RouterTradeBuilder.buyViaDlmmLaunchLab(USER,
                quotePool, launchPool(), 500_000_000, 1_000_000, 1_000_000, 100);
        assertEquals(2, swap.legs().size());
        assertEquals(QUOTE, swap.legs().get(0).outputMint());
        assertEquals(QUOTE, swap.legs().get(1).inputMint());
        assertEquals(TokenProgram.TOKEN_2022, swap.outputTokenProgram());
        assertThrows(IllegalArgumentException.class, () ->
                RouterTradeBuilder.buyViaDlmmLaunchLab(USER, quotePool, launchPool(),
                        500_000_000, 1_000_000, 1_000_001, 100));
    }

    /** 组合固定金额的两跳测试配置。 */
    private static RouterTradeBuilder.RouteSwap route(RouterLeg first, RouterLeg second) {
        return new RouterTradeBuilder.RouteSwap(USER, List.of(first, second),
                List.of(TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022),
                500_000_000, 100, true, true);
    }

    /** 生成测试用 DLMM 第一跳。 */
    private static RouterLeg dlmm(long amountIn, long minOut) {
        return MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(10), key(11), key(12),
                RouterTradeBuilder.WSOL_MINT, QUOTE, TokenProgram.PROGRAM_ID,
                TokenProgram.PROGRAM_ID, key(13), null, List.of(key(14))),
                USER, RouterTradeBuilder.WSOL_MINT, amountIn, minOut);
    }

    /** 生成测试用 LaunchLab 池账户集合。 */
    private static LaunchLabLegs.Pool launchPool() {
        return new LaunchLabLegs.Pool(MINT, QUOTE, TokenProgram.TOKEN_2022,
                TokenProgram.PROGRAM_ID, key(34), key(35), key(36), key(37),
                key(38), key(39), key(40));
    }

    /** 生成指定输入数量的 LaunchLab 第二跳。 */
    private static RouterLeg launch(long amountIn, long minOut) {
        return LaunchLabLegs.buyExactIn(launchPool(), USER, amountIn, minOut);
    }

    /** 生成稳定的占位公钥。 */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /** 从字节数组指定位置按小端格式读取 64 位整数。 */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
