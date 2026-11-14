package org.p2p.solanaj.router;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 读取 Router 配置并校验池账户，先模拟精确输入交易，再按调用方要求广播。
 */
public final class RouterTrader {
    /** 允许通过本客户端构造并核验池 owner 的 DEX 程序集合。 */
    private static final Set<PublicKey> SUPPORTED_DEX = Set.of(
            RaydiumLegs.AMM_V4, RaydiumLegs.CPMM, RaydiumLegs.CLMM,
            OrcaLegs.WHIRLPOOL, MeteoraLegs.DAMM_V2, MeteoraLegs.DLMM,
            PumpAmmLegs.PROGRAM, PumpFunLegs.PROGRAM, LaunchLabLegs.PROGRAM);
    /** 用于读取链上配置、校验账户、模拟及按需广播交易的 RPC 客户端。 */
    private final RpcApi rpc;

    /**
     * 注入 RPC 客户端；构造本身不会访问网络。
     * @param rpc 后续读取链上账户及模拟交易使用的客户端
     */
    public RouterTrader(RpcApi rpc) {
        this.rpc = Objects.requireNonNull(rpc, "rpc");
    }

    /**
     * 模拟日志及可选交易签名；有签名不代表交易已经确认。
     * @param signature 广播后 RPC 返回的签名；仅模拟时为 null
     * @param simulationLogs 模拟阶段返回的程序日志
     */
    public record Result(String signature, List<String> simulationLogs) {
        /** 判断本次调用是否已经提交广播。 */
        public boolean broadcast() {
            return signature != null;
        }
    }

    /**
     * 单跳买入：检查链上配置和池账户，模拟后按需发送。
     * @param signer 付款和签名账户
     * @param swap 单跳买入参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buy(Account signer, RouterTradeBuilder.Swap swap, boolean broadcast) throws RpcException {
        return execute(signer, swap, true, broadcast);
    }

    /**
     * 单跳卖出：检查链上配置和池账户，模拟后按需发送。
     * @param signer 付款和签名账户
     * @param swap 单跳卖出参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sell(Account signer, RouterTradeBuilder.Swap swap, boolean broadcast) throws RpcException {
        return execute(signer, swap, false, broadcast);
    }

    /**
     * 多跳买入：输入代币账户须预先备好资金。
     * @param signer 付款和签名账户
     * @param swap 固定金额多跳买入参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buy(Account signer, RouterTradeBuilder.RouteSwap swap, boolean broadcast)
            throws RpcException {
        return executeRoute(signer, swap, true, false, broadcast, null);
    }

    /**
     * 多跳卖出：输入代币账户须预先备好资金。
     * @param signer 付款和签名账户
     * @param swap 固定金额多跳卖出参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sell(Account signer, RouterTradeBuilder.RouteSwap swap, boolean broadcast)
            throws RpcException {
        return executeRoute(signer, swap, false, false, broadcast, null);
    }

    /**
     * 多跳买入前在同一笔交易中将原生 SOL 包装为 WSOL。
     * @param signer 付款和签名账户
     * @param swap 以 WSOL 为输入的固定金额路由
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyWithSol(Account signer, RouterTradeBuilder.RouteSwap swap, boolean broadcast)
            throws RpcException {
        return executeRoute(signer, swap, true, true, broadcast, null);
    }

    /**
     * 用 v0 交易多跳买入；ALT 快照对应的链上查找表须已生效。
     * @param signer 付款和签名账户
     * @param swap 固定金额多跳买入参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyV0(Account signer, RouterTradeBuilder.RouteSwap swap,
                        List<RouterV0Transaction.LookupTable> lookupTables, boolean broadcast)
            throws RpcException {
        return executeRoute(signer, swap, true, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 用 v0 交易多跳卖出；ALT 快照对应的链上查找表须已生效。
     * @param signer 付款和签名账户
     * @param swap 固定金额多跳卖出参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sellV0(Account signer, RouterTradeBuilder.RouteSwap swap,
                         List<RouterV0Transaction.LookupTable> lookupTables, boolean broadcast)
            throws RpcException {
        return executeRoute(signer, swap, false, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 原生 SOL 包装与多跳买入合并为一笔 v0 交易。
     * @param signer 付款和签名账户
     * @param swap 以 WSOL 为输入的固定金额路由
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyWithSolV0(Account signer, RouterTradeBuilder.RouteSwap swap,
                               List<RouterV0Transaction.LookupTable> lookupTables,
                               boolean broadcast) throws RpcException {
        return executeRoute(signer, swap, true, true, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 动态两跳买入：输入代币账户已备好资金，先模拟再按需广播。
     * @param signer 付款和签名账户
     * @param swap 动态两跳买入参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyDynamic(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                             boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, false, false, broadcast, null);
    }

    /**
     * 动态两跳卖出：第二跳可为链上支持的精确输入池；收到 WSOL 时不会自动解包。
     * @param signer 付款和签名账户
     * @param swap 动态两跳卖出参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sellDynamic(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                              boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, true, false, broadcast, null);
    }

    /**
     * 动态两跳：同一笔交易内把原生 SOL 转为 WSOL，模拟通过后才按需广播。
     * @param signer 付款和签名账户
     * @param swap 以 WSOL 为输入的动态两跳参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyDynamicWithSol(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                                    boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, false, true, broadcast, null);
    }

    /**
     * 动态两跳买入的 v0 交易；地址查找表须提前创建并生效。
     * @param signer 付款和签名账户
     * @param swap 动态两跳买入参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyDynamicV0(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                               List<RouterV0Transaction.LookupTable> lookupTables,
                               boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, false, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 动态两跳卖出的 v0 交易；地址查找表须提前创建并生效。
     * @param signer 付款和签名账户
     * @param swap 动态两跳卖出参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sellDynamicV0(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                                List<RouterV0Transaction.LookupTable> lookupTables,
                                boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, true, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 动态两跳的原生 SOL 加 v0 交易。
     * @param signer 付款和签名账户
     * @param swap 以 WSOL 为输入的动态两跳参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyDynamicWithSolV0(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                                      List<RouterV0Transaction.LookupTable> lookupTables,
                                      boolean broadcast) throws RpcException {
        return executeDynamic(signer, swap, false, true, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 三跳买入：先校验链上账户并模拟，仅在明确指定时广播。
     * @param signer 付款和签名账户
     * @param swap 固定金额或动态三跳买入参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buy(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                      boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, false, false, broadcast, null);
    }

    /**
     * 三跳卖出：后两跳可用链上支持的精确输入池。
     * @param signer 付款和签名账户
     * @param swap 固定金额或动态三跳卖出参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sell(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                       boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, true, false, broadcast, null);
    }

    /**
     * 三跳买入前在同一笔交易中包装原生 SOL 为 WSOL。
     * @param signer 付款和签名账户
     * @param swap 以经典 SPL WSOL 为输入的三跳参数
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyWithSol(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                             boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, false, true, broadcast, null);
    }

    /**
     * 使用已生效的地址查找表构造 v0 三跳买入。
     * @param signer 付款和签名账户
     * @param swap 三跳买入参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyV0(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                        List<RouterV0Transaction.LookupTable> lookupTables,
                        boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, false, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 使用已生效的地址查找表构造 v0 三跳卖出。
     * @param signer 付款和签名账户
     * @param swap 三跳卖出参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result sellV0(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                         List<RouterV0Transaction.LookupTable> lookupTables,
                         boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, true, false, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 将原生 SOL 包装与三跳买入合并为一笔 v0 交易。
     * @param signer 付款和签名账户
     * @param swap 以经典 SPL WSOL 为输入的三跳参数
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    public Result buyWithSolV0(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                               List<RouterV0Transaction.LookupTable> lookupTables,
                               boolean broadcast) throws RpcException {
        return executeThreeHop(signer, swap, false, true, broadcast,
                Objects.requireNonNull(lookupTables, "lookupTables"));
    }

    /**
     * 单跳交易的共同执行路径：校验配置、mint 与池 owner 后构造交易。
     * @param signer 付款和签名账户
     * @param swap 单跳参数
     * @param buy 是否为买入路径
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    private Result execute(Account signer, RouterTradeBuilder.Swap swap, boolean buy,
                           boolean broadcast) throws RpcException {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(swap, "swap");
        if (!signer.getPublicKey().equals(swap.payer()))
            throw new IllegalArgumentException("signer is not swap payer");
        if (!SUPPORTED_DEX.contains(swap.leg().programId()))
            throw new IllegalArgumentException("DEX is not one of the supported Router leg builders");
        PublicKey feeRecipient = loadZeroFeeConfig();
        verifyMintProgram(swap.inputMint(), swap.inputTokenProgram());
        verifyMintProgram(swap.outputMint(), swap.outputTokenProgram());
        verifyPoolOwner(swap.leg());
        List<TransactionInstruction> instructions = buy
                ? RouterTradeBuilder.buy(swap, feeRecipient)
                : RouterTradeBuilder.sell(swap, feeRecipient);
        return simulateAndMaybeSend(signer, instructions, broadcast);
    }

    /**
     * 固定金额多跳的共同执行路径，可选择传统交易或 v0 交易。
     * @param signer 付款和签名账户
     * @param swap 固定金额多跳参数
     * @param buy 是否为买入路径
     * @param wrapSol 是否在同笔交易中包装原生 SOL
     * @param broadcast 是否在模拟成功后广播
     * @param lookupTables 可选的已激活地址查找表快照
     * @return 模拟日志和可选交易签名
     */
    private Result executeRoute(Account signer, RouterTradeBuilder.RouteSwap swap, boolean buy,
                                boolean wrapSol, boolean broadcast,
                                List<RouterV0Transaction.LookupTable> lookupTables)
            throws RpcException {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(swap, "swap");
        if (!signer.getPublicKey().equals(swap.payer()))
            throw new IllegalArgumentException("signer is not route payer");
        PublicKey feeRecipient = loadZeroFeeConfig();
        verifyMintProgram(swap.inputMint(), swap.inputTokenProgram());
        for (int i = 0; i < swap.legs().size(); i++) {
            RouterLeg leg = swap.legs().get(i);
            if (!SUPPORTED_DEX.contains(leg.programId()))
                throw new IllegalArgumentException("DEX is not one of the supported Router leg builders");
            verifyMintProgram(leg.outputMint(), swap.tokenPrograms().get(i + 1));
            verifyPoolOwner(leg);
        }
        List<TransactionInstruction> instructions = wrapSol
                ? RouterTradeBuilder.buyWithSol(swap, feeRecipient)
                : buy ? RouterTradeBuilder.buy(swap, feeRecipient)
                      : RouterTradeBuilder.sell(swap, feeRecipient);
        return lookupTables == null
                ? simulateAndMaybeSend(signer, instructions, broadcast)
                : simulateAndMaybeSendV0(signer, instructions, lookupTables, broadcast);
    }

    /**
     * 动态两跳共同执行路径：第一跳实际产出的 quote 由链上 Router 计算。
     * @param signer 付款和签名账户
     * @param swap 动态两跳参数
     * @param sell 是否为卖出路径
     * @param wrapSol 是否在同笔交易中包装原生 SOL
     * @param broadcast 是否在模拟成功后广播
     * @param lookupTables 可选的已激活地址查找表快照
     * @return 模拟日志和可选交易签名
     */
    private Result executeDynamic(Account signer, RouterTradeBuilder.DynamicRouteSwap swap,
                                  boolean sell, boolean wrapSol, boolean broadcast,
                                  List<RouterV0Transaction.LookupTable> lookupTables)
            throws RpcException {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(swap, "swap");
        if (!signer.getPublicKey().equals(swap.payer()))
            throw new IllegalArgumentException("签名账户不是动态路由的付款账户");
        PublicKey feeRecipient = loadZeroFeeConfig();
        verifyMintProgram(swap.inputMint(), swap.inputTokenProgram());
        verifyMintProgram(swap.quoteMint(), swap.quoteTokenProgram());
        verifyMintProgram(swap.outputMint(), swap.outputTokenProgram());
        verifyPoolOwner(swap.firstLeg());
        verifyPoolOwner(swap.secondLeg());
        List<TransactionInstruction> instructions = sell
                ? RouterTradeBuilder.sell(swap, feeRecipient)
                : wrapSol ? RouterTradeBuilder.buyWithSol(swap, feeRecipient)
                          : RouterTradeBuilder.buy(swap, feeRecipient);
        return lookupTables == null
                ? simulateAndMaybeSend(signer, instructions, broadcast)
                : simulateAndMaybeSendV0(signer, instructions, lookupTables, broadcast);
    }

    /**
     * 三跳共同执行路径：检查真实池和 mint owner，模拟后按需广播。
     * @param signer 付款和签名账户
     * @param swap 三跳路径及动态模式参数
     * @param sell 是否为卖出路径
     * @param wrapSol 是否在本笔交易中先包装原生 SOL
     * @param broadcast 是否在模拟成功后广播
     * @param lookupTables 可选的已激活地址查找表快照；空值使用传统交易
     * @return 模拟日志和可选交易签名
     */
    private Result executeThreeHop(Account signer, RouterTradeBuilder.ThreeHopRouteSwap swap,
                                   boolean sell, boolean wrapSol, boolean broadcast,
                                   List<RouterV0Transaction.LookupTable> lookupTables)
            throws RpcException {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(swap, "swap");
        if (!signer.getPublicKey().equals(swap.payer()))
            throw new IllegalArgumentException("签名账户不是三跳路由的付款账户");
        PublicKey feeRecipient = loadZeroFeeConfig();
        verifyMintProgram(swap.inputMint(), swap.inputTokenProgram());
        verifyMintProgram(swap.firstIntermediateMint(), swap.firstTokenProgram());
        verifyMintProgram(swap.secondIntermediateMint(), swap.secondTokenProgram());
        verifyMintProgram(swap.outputMint(), swap.outputTokenProgram());
        for (RouterLeg leg : swap.legs()) {
            if (!SUPPORTED_DEX.contains(leg.programId()))
                throw new IllegalArgumentException("三跳中包含不支持的 DEX 程序");
            verifyPoolOwner(leg);
        }
        List<TransactionInstruction> instructions = wrapSol
                ? RouterTradeBuilder.buyWithSol(swap, feeRecipient)
                : sell ? RouterTradeBuilder.sell(swap, feeRecipient)
                       : RouterTradeBuilder.buy(swap, feeRecipient);
        return lookupTables == null
                ? simulateAndMaybeSend(signer, instructions, broadcast)
                : simulateAndMaybeSendV0(signer, instructions, lookupTables, broadcast);
    }

    /**
     * 对 v0 交易签名并模拟；仅在 broadcast=true 时发送。
     * @param signer 付款和签名账户
     * @param instructions 按顺序排列的交易指令
     * @param lookupTables 已激活的地址查找表快照
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    private Result simulateAndMaybeSendV0(Account signer, List<TransactionInstruction> instructions,
                                          List<RouterV0Transaction.LookupTable> lookupTables,
                                          boolean broadcast) throws RpcException {
        String blockhash = rpc.getLatestBlockhash().getValue().getBlockhash();
        byte[] wire = RouterV0Transaction.compileAndSign(signer, blockhash, instructions, lookupTables);
        String base64 = Base64.getEncoder().encodeToString(wire);
        SimulatedTransaction simulation = rpc.simulateTransaction(base64);
        List<String> logs = checkedLogs(simulation);
        if (!broadcast) return new Result(null, logs);
        return new Result(rpc.sendTransaction(base64, new RpcSendTransactionConfig()), logs);
    }

    /**
     * 对传统交易签名并模拟，并在广播前拒绝超过 1232 字节的消息。
     * @param signer 付款和签名账户
     * @param instructions 按顺序排列的交易指令
     * @param broadcast 是否在模拟成功后广播
     * @return 模拟日志和可选交易签名
     */
    private Result simulateAndMaybeSend(Account signer, List<TransactionInstruction> instructions,
                                        boolean broadcast) throws RpcException {
        Transaction tx = new Transaction();
        for (TransactionInstruction instruction : instructions) tx.addInstruction(instruction);
        String blockhash = rpc.getLatestBlockhash().getValue().getBlockhash();
        tx.setRecentBlockHash(blockhash);
        tx.sign(signer);
        if (tx.serialize().length > 1232)
            throw new IllegalStateException("Route exceeds Solana's 1232-byte legacy transaction limit; "
                    + "use buyV0/sellV0/buyWithSolV0 with activated lookup tables");
        SimulatedTransaction simulation = rpc.simulateTransaction(tx, signer, blockhash);
        List<String> logs = checkedLogs(simulation);
        if (!broadcast) return new Result(null, logs);
        // RPC 返回的是交易签名，不代表达到确认或最终确定状态。
        return new Result(rpc.sendTransaction(tx, signer, blockhash), logs);
    }

    /**
     * 统一检查模拟结果；失败时把链上日志连同错误一起抛出。
     * @param simulation RPC 返回的模拟结果
     * @return 模拟阶段返回的程序日志
     */
    private static List<String> checkedLogs(SimulatedTransaction simulation) {
        if (simulation == null || simulation.getValue() == null)
            throw new IllegalStateException("RPC returned no simulation result");
        List<String> logs = simulation.getValue().getLogs() == null
                ? List.of() : List.copyOf(simulation.getValue().getLogs());
        if (simulation.getValue().getErr() != null)
            throw new IllegalStateException("Router simulation failed: "
                    + simulation.getValue().getErr() + "\n" + String.join("\n", logs));
        return logs;
    }

    /** 从链上读取本 Router 的配置 PDA，并要求费率为零且程序未暂停。 */
    private PublicKey loadZeroFeeConfig() throws RpcException {
        PublicKey configAddress = RouterTradeBuilder.pda(RouterTradeBuilder.ROUTER_PROGRAM,
                "config".getBytes(StandardCharsets.US_ASCII));
        AccountInfo account = rpc.getAccountInfo(configAddress);
        if (account == null || account.getValue() == null
                || !RouterTradeBuilder.ROUTER_PROGRAM.toBase58().equals(account.getValue().getOwner())) {
            throw new IllegalStateException("self-deployed Router config missing or wrong owner");
        }
        byte[] data = account.getDecodedData();
        if (data.length < 80 || !"ROUTCFG1".equals(new String(data, 0, 8, StandardCharsets.US_ASCII)))
            throw new IllegalStateException("Router config has the wrong layout");
        int feeBps = (data[72] & 0xff) | ((data[73] & 0xff) << 8);
        if (feeBps != 0) throw new IllegalStateException("this Java port requires Router fee_bps=0");
        if (data[75] != 0) throw new IllegalStateException("Router is paused");
        return PublicKey.readPubkey(data, 40);
    }

    /**
     * 确认 mint 账户 owner 与调用方提供的 Token Program 一致。
     * @param mint 待检查的代币 mint
     * @param tokenProgram 预期的 Token Program
     */
    private void verifyMintProgram(PublicKey mint, PublicKey tokenProgram) throws RpcException {
        AccountInfo account = rpc.getAccountInfo(mint);
        if (account == null || account.getValue() == null
                || !tokenProgram.toBase58().equals(account.getValue().getOwner())) {
            throw new IllegalStateException("mint owner does not match token program: " + mint);
        }
    }

    /**
     * 按 DEX 类型找出池账户，并核对其 owner 为预期 DEX 程序。
     * @param leg 待检查的 DEX 一跳
     */
    private void verifyPoolOwner(RouterLeg leg) throws RpcException {
        PublicKey program = leg.programId();
        int index;
        if (program.equals(RaydiumLegs.AMM_V4) || program.equals(MeteoraLegs.DAMM_V2)) index = 1;
        else if (program.equals(RaydiumLegs.CPMM)) index = 3;
        else if (program.equals(OrcaLegs.WHIRLPOOL)) index = 4;
        else if (program.equals(RaydiumLegs.CLMM)) index = 2;
        else if (program.equals(LaunchLabLegs.PROGRAM)) index = 4;
        else if (program.equals(PumpFunLegs.PROGRAM)) index = 10;
        else index = 0; // DLMM 与 PumpSwap 的池账户都位于首位。
        if (leg.accounts().size() <= index) throw new IllegalArgumentException("DEX pool account missing");
        PublicKey pool = leg.accounts().get(index).getPublicKey();
        AccountInfo account = rpc.getAccountInfo(pool);
        if (account == null || account.getValue() == null
                || !program.toBase58().equals(account.getValue().getOwner())) {
            throw new IllegalStateException("pool owner is not the expected DEX program: " + pool);
        }
    }
}
