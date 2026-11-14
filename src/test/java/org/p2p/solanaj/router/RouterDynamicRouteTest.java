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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 只测试离线构造和兼容性；不会连接 RPC、签名或提交交易。 */
class RouterDynamicRouteTest {
    /** 模拟交易用户的占位公钥。 */
    private static final PublicKey USER = key(1);
    /** 模拟第一跳收到的中间 quote mint。 */
    private static final PublicKey QUOTE = key(2);
    /** 模拟第二跳买入的最终目标 mint。 */
    private static final PublicKey TARGET = key(3);
    /** 模拟 Router 手续费接收方的占位公钥。 */
    private static final PublicKey FEE_RECIPIENT = key(4);
    /** 测试中使用的经典 SPL Token 程序。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;
    /** 测试第一跳花费的 WSOL 最小单位数量。 */
    private static final long WSOL_IN = 500_000_000L;
    /** 第一跳最低可接受 quote 输出，仅用于离线编码断言。 */
    private static final long QUOTE_MIN = 1_000_000L;
    /** 整条路由最低可接受目标币输出，仅用于离线编码断言。 */
    private static final long TARGET_MIN = 100L;

    /** 验证动态两跳的标签、金额、账户顺序和每跳原始指令布局。 */
    @Test
    void dynamicDlmmToLaunchLabMatchesRustEnvelope() {
        RouterLeg first = MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(
                key(10), key(11), key(12), RouterTradeBuilder.WSOL_MINT, QUOTE,
                TOKEN, TOKEN, key(13), null, List.of(key(14))),
                USER, RouterTradeBuilder.WSOL_MINT, WSOL_IN, QUOTE_MIN);
        RouterLeg second = launch();
        RouterTradeBuilder.DynamicRouteSwap swap = dynamic(first, second);
        TransactionInstruction ix = RouterTradeBuilder.routeDynamic(swap, FEE_RECIPIENT);
        byte[] data = ix.getData();

        assertEquals(RouterTradeBuilder.ROUTER_PROGRAM, ix.getProgramId());
        assertEquals(3, Byte.toUnsignedInt(data[0]));
        assertEquals(WSOL_IN, u64(data, 1));
        assertEquals(TARGET_MIN, u64(data, 9));
        assertEquals(1, Byte.toUnsignedInt(data[17]));
        assertEquals(2, Byte.toUnsignedInt(data[18]));
        assertEquals(QUOTE_MIN, u64(data, 19));
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                ix.getKeys().get(6).getPublicKey());
        assertTrue(ix.getKeys().get(6).isWritable());
        assertEquals(RouterTradeBuilder.ata(USER, RouterTradeBuilder.WSOL_MINT, TOKEN),
                ix.getKeys().get(3).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, TARGET, TokenProgram.TOKEN_2022),
                ix.getKeys().get(4).getPublicKey());

        // 两跳仍以原始 DEX 指令封装；链上执行第二跳前才将占位金额 1 替换为实际 quote 增量。
        int offset = 27;
        for (RouterLeg leg : List.of(first, second)) {
            assertArrayEquals(leg.programId().toByteArray(), Arrays.copyOfRange(data, offset, offset + 32));
            offset += 32;
            assertEquals(leg.accounts().size(), Byte.toUnsignedInt(data[offset++]));
            int length = Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2)
                    .order(ByteOrder.LITTLE_ENDIAN).getShort());
            offset += 2;
            assertArrayEquals(leg.data(), Arrays.copyOfRange(data, offset, offset + length));
            offset += length;
        }
        assertEquals(data.length, offset);
        assertEquals(1, second.amountInRaw());
        assertEquals(1, u64(second.data(), 8));
        assertEquals(7 + first.accounts().size() + second.accounts().size() + 2,
                ix.getKeys().size());
        assertEquals(first.programId(), ix.getKeys().get(ix.getKeys().size() - 2).getPublicKey());
        assertEquals(second.programId(), ix.getKeys().get(ix.getKeys().size() - 1).getPublicKey());
    }

    /** 验证原生 SOL 入口会在同一笔交易中准备 WSOL 与相关 ATA。 */
    @Test
    void nativeSolSetupAndAtaCreationAreInTheSameTransaction() {
        RouterTradeBuilder.DynamicRouteSwap swap = dynamic(syntheticFirst(MeteoraLegs.DLMM), launch());
        List<TransactionInstruction> instructions = RouterTradeBuilder.buyWithSol(swap, FEE_RECIPIENT);
        assertEquals(6, instructions.size());
        assertEquals(AssociatedTokenProgram.PROGRAM_ID, instructions.get(0).getProgramId());
        assertEquals(SystemProgram.PROGRAM_ID, instructions.get(1).getProgramId());
        assertEquals(WSOL_IN, u64(instructions.get(1).getData(), 4));
        assertEquals(TokenProgram.PROGRAM_ID, instructions.get(2).getProgramId());
        assertEquals(17, Byte.toUnsignedInt(instructions.get(2).getData()[0]));
        assertFalse(instructions.get(2).getKeys().get(0).isSigner());
        assertEquals(RouterTradeBuilder.ata(USER, QUOTE, TOKEN),
                instructions.get(3).getKeys().get(1).getPublicKey());
        assertEquals(RouterTradeBuilder.ata(USER, TARGET, TokenProgram.TOKEN_2022),
                instructions.get(4).getKeys().get(1).getPublicKey());
        assertEquals(3, Byte.toUnsignedInt(instructions.get(5).getData()[0]));
    }

    /** 已有 WSOL 和 ATA 时，验证可以只保留一条 Router 指令。 */
    @Test
    void prefundedWsolAndExistingAtasNeedOnlyOneRouteInstruction() {
        RouterTradeBuilder.DynamicRouteSwap swap = new RouterTradeBuilder.DynamicRouteSwap(
                USER, syntheticFirst(MeteoraLegs.DLMM), launch(), TOKEN, TOKEN,
                TokenProgram.TOKEN_2022, WSOL_IN, QUOTE_MIN, TARGET_MIN, false, false);
        List<TransactionInstruction> instructions = RouterTradeBuilder.buy(swap, FEE_RECIPIENT);
        assertEquals(1, instructions.size());
        assertEquals(3, Byte.toUnsignedInt(instructions.get(0).getData()[0]));
    }

    /** 遍历受支持的第一跳类型，并验证 LaunchLab 与 CPMM 第二跳的账户布局。 */
    @Test
    void allSupportedFirstDexesCanTargetLaunchLabOrCpmm() {
        for (RouterLeg first : actualFirstLegs()) {
            PublicKey firstProgram = first.programId();
            for (RouterLeg second : List.of(launch(), cpmm())) {
                TransactionInstruction ix = RouterTradeBuilder.routeDynamic(
                        dynamic(first, second), FEE_RECIPIENT);
                assertEquals(3, Byte.toUnsignedInt(ix.getData()[0]));
                assertEquals(firstProgram, ix.getKeys().get(7 + first.accounts().size()
                        + second.accounts().size()).getPublicKey());
                int uniquePrograms = firstProgram.equals(second.programId()) ? 1 : 2;
                assertEquals(7 + first.accounts().size() + second.accounts().size()
                        + uniquePrograms, ix.getKeys().size());
            }
        }
    }

    /** 拒绝错误占位金额和第一跳输入账户，同时确认旧版固定金额标签仍可构造。 */
    @Test
    void rejectsWrongPlaceholderOrUnconnectedAccountsAndKeepsLegacyTag() {
        RouterLeg first = syntheticFirst(MeteoraLegs.DLMM);
        RouterLeg second = launch();
        byte[] wrongAmount = second.data();
        wrongAmount[8] = 2;
        RouterLeg badAmount = new RouterLeg(second.programId(), second.accounts(), wrongAmount,
                second.inputMint(), second.outputMint(), 2, second.minOutRaw());
        assertThrows(IllegalArgumentException.class, () -> dynamic(first, badAmount));

        // 后续跳 ATA 校验已由现有性能版本注释；这里测试仍然启用的第一跳输入校验。
        List<AccountMeta> wrongAccounts = new ArrayList<>(first.accounts());
        wrongAccounts.set(0, RouterTradeBuilder.writable(key(99)));
        RouterLeg badAta = new RouterLeg(first.programId(), wrongAccounts, first.data(),
                first.inputMint(), first.outputMint(), first.amountInRaw(), first.minOutRaw());
        assertThrows(IllegalArgumentException.class, () -> dynamic(badAta, second));

        RouterTradeBuilder.RouteSwap old = new RouterTradeBuilder.RouteSwap(USER,
                List.of(first, LaunchLabLegs.buyExactIn(launchPool(), USER, QUOTE_MIN, TARGET_MIN)),
                List.of(TOKEN, TOKEN, TokenProgram.TOKEN_2022), WSOL_IN, TARGET_MIN, true, true);
        assertEquals(2, Byte.toUnsignedInt(RouterTradeBuilder.route(old, FEE_RECIPIENT).getData()[0]));
    }

    /** 为测试拼接 WSOL、quote、目标代币三段账户关系。 */
    private static RouterTradeBuilder.DynamicRouteSwap dynamic(RouterLeg first, RouterLeg second) {
        return new RouterTradeBuilder.DynamicRouteSwap(USER, first, second,
                TOKEN, TOKEN, TokenProgram.TOKEN_2022,
                WSOL_IN, QUOTE_MIN, TARGET_MIN, true, true);
    }

    /** 构造最小化的第一跳数据，用来测试封装逻辑而非真实 DEX 执行。 */
    private static RouterLeg syntheticFirst(PublicKey program) {
        byte[] data = new byte[program.equals(RaydiumLegs.AMM_V4) ? 17 : 24];
        int amountOffset = program.equals(RaydiumLegs.AMM_V4) ? 1 : 8;
        ByteBuffer.wrap(data, amountOffset, 16).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(WSOL_IN).putLong(QUOTE_MIN);
        return new RouterLeg(program, List.of(
                RouterTradeBuilder.writable(RouterTradeBuilder.ata(USER, RouterTradeBuilder.WSOL_MINT, TOKEN)),
                RouterTradeBuilder.writable(RouterTradeBuilder.ata(USER, QUOTE, TOKEN))),
                data, RouterTradeBuilder.WSOL_MINT, QUOTE, WSOL_IN, QUOTE_MIN);
    }

    /** 用各 DEX 的实际构造器生成第一跳测试样例；账户地址仍是占位值。 */
    private static List<RouterLeg> actualFirstLegs() {
        PublicKey wsol = RouterTradeBuilder.WSOL_MINT;
        PumpAmmLegs.Pool pump = new PumpAmmLegs.Pool(key(80), QUOTE, wsol,
                key(81), key(82), TOKEN, TOKEN, key(83), key(84), key(85),
                key(86), null, false);
        return List.of(
                RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(key(50), wsol, QUOTE,
                        key(51), key(52), TOKEN), USER, wsol, WSOL_IN, QUOTE_MIN),
                RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(key(53), key(54), wsol, QUOTE,
                        key(55), key(56), TOKEN, TOKEN, key(57)),
                        USER, wsol, WSOL_IN, QUOTE_MIN),
                RaydiumLegs.clmm(new RaydiumLegs.Clmm(key(58), key(59), wsol, QUOTE,
                        key(60), key(61), TOKEN, TOKEN, key(62), null, List.of(key(63))),
                        USER, wsol, WSOL_IN, QUOTE_MIN),
                OrcaLegs.swap(new OrcaLegs.Pool(key(64), wsol, QUOTE,
                        key(65), key(66), TOKEN, TOKEN,
                        List.of(key(67), key(68), key(69))),
                        USER, wsol, WSOL_IN, QUOTE_MIN),
                MeteoraLegs.dammV2(new MeteoraLegs.DammV2(key(70), wsol, QUOTE,
                        key(71), key(72), TOKEN, TOKEN, null, false),
                        USER, wsol, WSOL_IN, QUOTE_MIN),
                MeteoraLegs.dlmm(new MeteoraLegs.Dlmm(key(73), key(74), key(75),
                        wsol, QUOTE, TOKEN, TOKEN, key(76), null, List.of(key(77))),
                        USER, wsol, WSOL_IN, QUOTE_MIN),
                PumpAmmLegs.buy(pump, USER, WSOL_IN, QUOTE_MIN));
    }

    /** 生成以 quote 购买目标币的 LaunchLab 第二跳。 */
    private static RouterLeg launch() {
        return LaunchLabLegs.buyExactIn(launchPool(), USER, 1, TARGET_MIN);
    }

    /** 生成供测试使用的 LaunchLab 池账户集合。 */
    private static LaunchLabLegs.Pool launchPool() {
        return new LaunchLabLegs.Pool(TARGET, QUOTE, TokenProgram.TOKEN_2022, TOKEN,
                key(30), key(31), key(32), key(33), key(34), key(35), key(36));
    }

    /** 生成以 quote 购买目标币的 CPMM 第二跳。 */
    private static RouterLeg cpmm() {
        return RaydiumLegs.cpmm(new RaydiumLegs.Cpmm(key(40), key(41), TARGET, QUOTE,
                key(42), key(43), TokenProgram.TOKEN_2022, TOKEN, key(44)),
                USER, QUOTE, 1, TARGET_MIN);
    }

    /** 生成稳定且可重复的测试公钥。 */
    private static PublicKey key(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /** 按小端格式读取指令中的无符号 64 位数。 */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
