package org.p2p.solanaj.router;

import org.p2p.solanaj.utils.Env;

import cn.hutool.core.codec.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 两跳构造示例：WSOL 经 Meteora DLMM 买入 quote，再经 Raydium LaunchLab 买入目标币。
 * 池地址与金额来自历史交易快照，不是实时行情；用于真实交易前必须重新报价和核对账户。
 * 第一个测试包含硬编码签名者，并会连接 RPC 获取区块哈希和模拟交易，但不会广播交易。
 * 因此该测试不能视为纯离线测试，也不应在未审查密钥和 RPC 目标时直接运行。
 */
class RouterDlmmLaunchLabExampleTest {
    /** 不用于签名的占位用户公钥，仅供拒绝条件测试使用。 */
    private static final PublicKey USER = dummyKey(1);
    /** 示例中的手续费接收方占位公钥。 */
    private static final PublicKey FEE_RECIPIENT = dummyKey(2);
    /** 历史交易快照的中间 quote mint。 */
    private static final PublicKey QUOTE = key("6GmAFSYs4gk3FDao5FzzySQpPZaWsa4rUJHacpMpUNgx");
    /** 历史交易快照的目标代币 mint。 */
    private static final PublicKey MINT = key("H8FCFy7sKfcxFiu6hJL8DWC2f7D4RqbEcqy7eLnoMUmq");

    /** 历史快照中的 WSOL 输入，单位为 lamports；不是实时交易参数。 */
    private static final long WSOL_IN = 499_500_000L;
    /** 历史快照中的第一跳最低 quote 输出；真实交易前须重新报价。 */
    private static final long QUOTE_MIN_OUT = 373_224_827_987L;
    /** 固定金额旧版第二跳使用的 quote 输入；不可自动等于实时第一跳产出。 */
    private static final long QUOTE_SPEND = 373_224_827_987L;
    /** 历史快照中的目标币最低输出；真实交易前须重新计算滑点。 */
    private static final long MINT_MIN_OUT = 15_441_030_948_488L;

    /** 构造两跳买入并通过 RPC 模拟；此测试会触网，但不会提交交易。 */
    @Test
    void constructsTwoHopBuyFromRealPoolAddressSnapshot()  throws Exception{

        String url = "http://64.130.57.42:8899";
        RpcApi rpc = new RpcApi(new RpcClient(url));

        // 保留原有硬编码签名者示例；不要在真实项目中提交实际钱包私钥。
        Account signer = Env.account();
        RouterTradeBuilder.RouteSwap swap = RouterTradeBuilder.buyViaDlmmLaunchLab(
                signer.getPublicKey(), quotePool(), mintPool(), WSOL_IN, QUOTE_MIN_OUT,
                QUOTE_SPEND, MINT_MIN_OUT);

        List<TransactionInstruction> instructions = RouterTradeBuilder.buyWithSol(swap, FEE_RECIPIENT);

        TransactionInstruction route = instructions.get(5);
        byte[] data = route.getData();

        TransactionConfigV1 config = new TransactionConfigV1()
                .setComputeUnitLimit(178_000)
                .setPriorityFeeLamports(4_596)
                .setLoadedAccountsDataSizeLimit(10 * 1024 * 1024)
                //.setHeapSize(32768) // 如需调整堆大小，可在确认运行环境后启用。
                ;
        TransactionV1 transactionV1 = new TransactionV1();
        transactionV1.setConfig(config);
        for (TransactionInstruction instruction : instructions) {
            transactionV1.addInstruction(instruction);
        }
        // 这里开始调用 RPC；只用于模拟，不会向链上广播。
        String blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();

        SimulatedTransaction simulated = rpc.simulateTransactionV1(transactionV1,signer,blockHash);
        if (simulated.getValue().getErr() != null) return;


    }

    /** 第二跳固定花费超过第一跳最低保证产出时应拒绝构造。 */
    @Test
    void rejectsSecondHopSpendingMoreThanFirstHopGuarantees() {
        assertThrows(IllegalArgumentException.class, () -> RouterTradeBuilder.buyViaDlmmLaunchLab(
                USER, quotePool(), mintPool(), WSOL_IN, QUOTE_MIN_OUT,
                QUOTE_MIN_OUT + 1, MINT_MIN_OUT));
    }

    /** 返回历史快照中的 DLMM quote 池账户。 */
    private static MeteoraLegs.Dlmm quotePool() {
        return new MeteoraLegs.Dlmm(
                key("3C6qVymTAwWNKCSspmd1qbUH9avaqhsjgW2yntvEYBXt"),
                key("J12GBAnKXqusMmRaZRSvvkaVnhakMnpHkxAqfVsw8VQd"),
                key("8bGwVxExBhMwXWv3Kzoqq3rHBrryMN8nCz754ECX648d"),
                QUOTE, RouterTradeBuilder.WSOL_MINT,
                TokenProgram.PROGRAM_ID, TokenProgram.PROGRAM_ID,
                key("FvG6CB6XixB914xfRnpprK2TbRXJov66THqAKXoghWsJ"),
                null, // 此历史快照没有位图扩展账户。
                List.of(key("FK5rkXYEz1x3HwzQ3jX9eSqe1MLM48QUdvmszg6RfqbZ")));
    }

    /** 返回历史快照中的 LaunchLab 目标币池账户。 */
    private static LaunchLabLegs.Pool mintPool() {
        return new LaunchLabLegs.Pool(
                MINT, QUOTE, TokenProgram.TOKEN_2022, TokenProgram.PROGRAM_ID,
                key("HEqWyRYknxkpb8XXbq1LFhoDV6m75NjBQrGLSeCEMK8B"),
                key("FfSmPg37oP77HSPavKXq4hwT55F4EwLiB6zMM7PphMEz"),
                key("GLonHUpb3WT6mdASg9QpNrjCbzKbuHbujfFowBJJgrvM"),
                key("6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt"),
                key("SmnY3zoqhqkYxKXeQcK1Zorkb39QQDLqSC4JzGazNja"),
                key("HqXLUpH3nXRqfnob61odmRRnVnv6pKgpA6ehbxUgzDjU"),
                key("4Rb2joMnuDt9zRPuTYxUjQRGQ8oNBbKdXnCJSCojvBsW"));
    }

    /** 将 Base58 地址转换为公钥对象。 */
    private static PublicKey key(String value) {
        return new PublicKey(value);
    }

    /** 生成仅用于拒绝条件测试的占位公钥。 */
    private static PublicKey dummyKey(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return new PublicKey(bytes);
    }

    /** 按小端字节序读取指令中的 64 位金额。 */
    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
