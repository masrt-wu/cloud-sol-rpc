package org.p2p.solanaj.router.buyOrSell;

import org.p2p.solanaj.utils.Env;


import cn.hutool.core.codec.Base58;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.router.*;
import org.p2p.solanaj.router.pda.ray.LaunchLabPoolStateParser;
import org.p2p.solanaj.router.pda.MeteoraDlmmLbPairParser;
import org.p2p.solanaj.router.pda.MeteoraDlmmPda;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.utils.ray.LiquidityStateV4;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

@Slf4j
public class MeteBuyOrSell {

    /** 经典 SPL Token 程序地址。 */
    private static final PublicKey TOKEN = TokenProgram.PROGRAM_ID;
    /** Token-2022 程序地址，用于测试目标代币账户推导。 */
    private static final PublicKey TOKEN_2022 = TokenProgram.TOKEN_2022;

    private static final String[] JIT_ADDRESS = new String[]{"ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt", "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL", "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5", "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT", "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY", "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh", "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe", "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49"};

    private static final long SOL_NUM = 1000000000L;

    @Test
    public  void sendBuy() throws Exception {

        Account fundAcc = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://64.130.57.42:8899";
        BigDecimal buySol = new BigDecimal("0.001");
        RpcApi rpc = new RpcApi(new RpcClient(url));
        long wsolInput = buySol.movePointRight(9).longValue();
        long quoteMinimum = 1L;
        long targetMinimum = 1L;
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length - 1)];
        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();

        // 第二条 lpAddress
        PublicKey twoLpAddress = new PublicKey("2ZWxT3niYjyudmDMDVar9ajNE42RkwYdzZBh6TiMuKQY");

        PublicKey mintLpAddress = new PublicKey("8MC2TdS5cU5PXVn9j7wcaz6p1g1t5jCpCCNzbEYrTqLX");


        //假如说 发现是usdc池子 先wsol 转usdc usdc 转换qoute
        PublicKey oneQuoteUsDc = new PublicKey("58oQChx4yWmvKdwLLZzBi4ChoCc2fqCUWBkwMihLYQo2");
        PublicKey oneBaseMint = new PublicKey("So11111111111111111111111111111111111111112");
        PublicKey oneQuoteMint = new PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        AccountInfo accountInfo = rpc.getAccountInfo(oneQuoteUsDc);
        if (accountInfo.getValue() == null) return;
        LiquidityStateV4 stateV4 = LiquidityStateV4.decode(accountInfo.getDecodedData());
        //LiquidityStateV4
        //RaydiumV4Pda.deriveBaseVault()

        //LaunchLabPoolStateParser
        accountInfo = rpc.getAccountInfo(mintLpAddress);
        if (accountInfo.getValue() == null) return;
        LaunchLabPoolStateParser.PoolState launchPool = LaunchLabPoolStateParser.parse(accountInfo.getDecodedData());
        PublicKey mintCreate = launchPool.creator();

        PublicKey mintPlatformFeeVault = PublicKey.findProgramAddress(
                List.of(
                        launchPool.platformConfig().toByteArray(),
                        launchPool.quoteMint().toByteArray()
                ),
                LaunchLabLegs.PROGRAM
        ).getAddress();
        var mintCreatorFeeVault = PublicKey.findProgramAddress(
                List.of(
                        mintCreate.toByteArray(),
                        launchPool.quoteMint().toByteArray()
                ),
                LaunchLabLegs.PROGRAM
        ).getAddress();


        accountInfo = rpc.getAccountInfo(twoLpAddress);
        if (accountInfo.getValue() == null) return;
        var dec = accountInfo.getDecodedData();
        MeteoraDlmmLbPairParser.LbPair lbPair = MeteoraDlmmLbPairParser.parse(dec);

        // 根据链上 Token Mint X Program Flag
        PublicKey tokenProgramX = lbPair.tokenProgramX();
        //Token Mint Y Program Flag
        PublicKey tokenProgramY = lbPair.tokenProgramY();
        // LbPair 已保存真实 Oracle 地址，直接使用，避免重复推导 PDA。
        PublicKey oracle = lbPair.oracle();

        PublicKey quoteMint = lbPair.tokenXMint();
        PublicKey quoteQuote = lbPair.tokenYMint();
        // 两侧 Reserve 已在本次链上快照里，直接读取比重新推导更快。
        PublicKey reserveX = lbPair.reserveX();
        PublicKey reserveY = lbPair.reserveY();
        //公式 active_id / 70  四舍五入 9  bin_array_bitmap 不存在 9
        // 需要访问的 BinArray index 是否超出了 LbPair 内置 bitmap 的范围
        var index = new BigDecimal(lbPair.activeId()).divide(new BigDecimal("70"), 0, RoundingMode.HALF_UP).intValue();
        // 默认位图足够时不推导最终不会传入交易的扩展 PDA。
        PublicKey bitmapExtension = lbPair.binArrayBitmap().length > index
                ? null : MeteoraDlmmPda.bitmapExtension(twoLpAddress);
        List<PublicKey> binArrays = MeteoraDlmmLbPairParser.buildBinArraysForSwap(twoLpAddress, lbPair, quoteMint.equals(quoteQuote), 4);
        MeteoraLegs.Dlmm pool = new MeteoraLegs.Dlmm(twoLpAddress, reserveX, reserveY,
                quoteMint, quoteQuote, tokenProgramX, tokenProgramY, oracle, bitmapExtension, binArrays);

        PublicKey solCreate = RouterTradeBuilder.ata(fundAcc.getPublicKey(), RouterTradeBuilder.WSOL_MINT, TokenProgram.PROGRAM_ID);

        // 第一条 wsol 转usdc
        var entry = RaydiumLegs.ammV4(new RaydiumLegs.AmmV4(oneQuoteUsDc, stateV4.baseMint, stateV4.quoteMint,
                stateV4.baseVault, stateV4.quoteVault, TOKEN), fundAcc.getPublicKey(),oneBaseMint, wsolInput, quoteMinimum);

        // 第二调 usdc转 quote
        RouterLeg met = MeteoraLegs.buyDlmm(pool, fundAcc.getPublicKey(), quoteMinimum, quoteMinimum);



        LaunchLabLegs.Pool lauPool = new LaunchLabLegs.Pool(launchPool.baseMint(),launchPool.quoteMint(),
                launchPool.baseTokenProgram(),launchPool.quoteTokenProgram(),mintLpAddress,launchPool.baseVault(),
                launchPool.quoteVault(),launchPool.platformConfig(),mintPlatformFeeVault,mintCreatorFeeVault,launchPool.globalConfig());

        // 第三条 qoute转 mint
        var targetBuy = LaunchLabLegs.buy(lauPool,fundAcc.getPublicKey(),quoteMinimum,quoteMinimum);

        RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                fundAcc.getPublicKey(), entry, met, targetBuy, TOKEN, tokenProgramY, tokenProgramX, lauPool.baseTokenProgram(),
                wsolInput, quoteMinimum, quoteMinimum, quoteMinimum,
                true, true, true);
        List<TransactionInstruction> list = RouterTradeBuilder.buyWithSol(swap,new PublicKey("CzjYCxfENUJm9BD6M3Qn61gaw8re5RM1MqXnBqfY13tN"));

        //List<TransactionInstruction> list = RouterTradeBuilder.buy(swap,new PublicKey("CzjYCxfENUJm9BD6M3Qn61gaw8re5RM1MqXnBqfY13tN"));

        TransactionV1 transactionV1 = new TransactionV1();
        TransactionConfigV1 config = new TransactionConfigV1()
                .setComputeUnitLimit(178_000)
                .setPriorityFeeLamports(4_596)
                .setLoadedAccountsDataSizeLimit(10 * 1024 * 1024)
                //.setHeapSize(32768)
                ;
        transactionV1.setConfig(config);

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(), new PublicKey(jitAddress), tipPirce);
        transactionV1.addInstruction(tranIn);

        for (TransactionInstruction transactionInstruction : list) {
            transactionV1.addInstruction(transactionInstruction);
        }
        TransactionInstruction instruction = TokenProgram.closeAccount(solCreate,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transactionV1.addInstruction(instruction);

        String blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction sim = rpc.simulateTransactionV1(transactionV1,fundAcc,blockHash);
        if (sim.getValue().getErr() != null) return;
        System.out.println(1111);
        blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
        String tx = jitRpcApi.sendTransactionV1(transactionV1,fundAcc,blockHash);
        System.out.println(tx);

    }

    @Test
    public void sendSell()  throws Exception{


        // 先 mint sell quote
        // qoute 转 usdc
        // usdc 转 wsol

        Account fundAcc = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://162.55.199.125:8899/";
        BigDecimal buySol = new BigDecimal("0.001");
        RpcApi rpc = new RpcApi(new RpcClient(url));
        long wsolInput = buySol.movePointRight(9).longValue();
        // 卖出数量
        long baseNum = 30194221644L;
        long quoteMinimum = 1L;
        long targetMinimum = 1L;
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length - 1)];
        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();


        PublicKey mintLpAddress = new PublicKey("8MC2TdS5cU5PXVn9j7wcaz6p1g1t5jCpCCNzbEYrTqLX");

        //假如说 发现是usdc池子 先wsol 转usdc usdc 转换qoute
        PublicKey oneQuoteUsDc = new PublicKey("58oQChx4yWmvKdwLLZzBi4ChoCc2fqCUWBkwMihLYQo2");
        PublicKey oneBaseMint = new PublicKey("So11111111111111111111111111111111111111112");
        PublicKey oneQuoteMint = new PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        // quote 池子 oPAiAikWTaFj9RYoRFD35ccfwhnMcB3ThgBZRHSkjTZ 币对
        PublicKey twoLpAddress = new PublicKey("2ZWxT3niYjyudmDMDVar9ajNE42RkwYdzZBh6TiMuKQY");


        PublicKey solCreate = RouterTradeBuilder.ata(fundAcc.getPublicKey(), RouterTradeBuilder.WSOL_MINT, TokenProgram.PROGRAM_ID);



        //假如说 发现是usdc池子 先wsol 转usdc usdc 转换qoute
        AccountInfo accountInfo = rpc.getAccountInfo(oneQuoteUsDc);
        if (accountInfo.getValue() == null) return;
        var lpAccountInfo = rpc.getAccountInfo(twoLpAddress);
        if (lpAccountInfo.getValue() == null) return;


        // mint的 Lp
        var mintAccountInfo = rpc.getAccountInfo(mintLpAddress);
        if (mintAccountInfo.getValue() == null) return;

        var start = System.nanoTime();
        LiquidityStateV4 stateV4 = LiquidityStateV4.decode(accountInfo.getDecodedData());

        LaunchLabPoolStateParser.PoolState launchPool = LaunchLabPoolStateParser.parse(mintAccountInfo.getDecodedData());
        PublicKey mintCreate = launchPool.creator();
        PublicKey mintPlatformFeeVault = PublicKey.findProgramAddress(List.of(launchPool.platformConfig().toByteArray(), launchPool.quoteMint().toByteArray()), LaunchLabLegs.PROGRAM).getAddress();
        var mintCreatorFeeVault = PublicKey.findProgramAddress(List.of(mintCreate.toByteArray(), launchPool.quoteMint().toByteArray()), LaunchLabLegs.PROGRAM).getAddress();
        LaunchLabLegs.Pool lauPool = new LaunchLabLegs.Pool(launchPool.baseMint(),
                launchPool.quoteMint(),
                launchPool.baseTokenProgram(),
                launchPool.quoteTokenProgram(),
                mintLpAddress,
                launchPool.baseVault(),
                launchPool.quoteVault(),
                launchPool.platformConfig(),
                mintPlatformFeeVault,
                mintCreatorFeeVault,launchPool.globalConfig());
        var targetSell = LaunchLabLegs.sell(lauPool,fundAcc.getPublicKey(),baseNum,quoteMinimum);
        long launchDone = System.nanoTime();


        var dec = lpAccountInfo.getDecodedData();
        MeteoraDlmmLbPairParser.LbPair lbPair = MeteoraDlmmLbPairParser.parse(dec);

        // 根据链上 Token Mint X Program Flag
        PublicKey tokenProgramX = lbPair.tokenProgramX();
        //Token Mint Y Program Flag
        PublicKey tokenProgramY = lbPair.tokenProgramY();
        // LbPair 已保存真实 Oracle 地址，直接使用，避免重复推导 PDA。
        PublicKey oracle = lbPair.oracle();

        PublicKey quoteMint = lbPair.tokenXMint();
        PublicKey quoteQuote = lbPair.tokenYMint();
        // 两侧 Reserve 已在本次链上快照里，直接读取比重新推导更快。
        PublicKey reserveX = lbPair.reserveX();
        PublicKey reserveY = lbPair.reserveY();
        //公式 active_id / 70  四舍五入 9  bin_array_bitmap 不存在 9
        // 需要访问的 BinArray index 是否超出了 LbPair 内置 bitmap 的范围
        var index = new BigDecimal(lbPair.activeId()).divide(new BigDecimal("70"), 0, RoundingMode.HALF_UP).intValue();
        // 默认位图足够时不推导最终不会传入交易的扩展 PDA。
        PublicKey bitmapExtension = lbPair.binArrayBitmap().length > index
                ? null : MeteoraDlmmPda.bitmapExtension(twoLpAddress);
        List<PublicKey> binArrays = MeteoraDlmmLbPairParser.buildBinArraysForSwap(twoLpAddress, lbPair, true, 4);
        MeteoraLegs.Dlmm pool = new MeteoraLegs.Dlmm(twoLpAddress, reserveX, reserveY,
                quoteMint, quoteQuote, tokenProgramX, tokenProgramY, oracle, bitmapExtension, binArrays);
        // 卖出的话  inputMint 参数转 quoteMint 也就是 tokenXMint， 之前参数是 tokenYMint
        // qoute转 usdc
        RouterLeg met = MeteoraLegs.sellDlmm(pool, fundAcc.getPublicKey(), quoteMinimum, quoteMinimum);
        long dlmmDone = System.nanoTime();

        //System.out.println(met);



        // usdc转 wsol
        // oneQuoteMint 比如是 sol 转 usdc 那就 填usdc地址
        var rayUsdcPool = new RaydiumLegs.AmmV4(oneQuoteUsDc, stateV4.baseMint, stateV4.quoteMint,
                stateV4.baseVault, stateV4.quoteVault, TOKEN);

        var entry = RaydiumLegs.ammV4(rayUsdcPool, fundAcc.getPublicKey(), oneQuoteMint, quoteMinimum, quoteMinimum);
        long rayDone = System.nanoTime();


        RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                fundAcc.getPublicKey(), targetSell, met, entry, lauPool.baseTokenProgram(), tokenProgramX, tokenProgramY, TOKEN,
                baseNum, quoteMinimum, quoteMinimum, quoteMinimum,
                true, true, true);

        List<TransactionInstruction> list = RouterTradeBuilder.sellCreWSol(swap,new PublicKey("CzjYCxfENUJm9BD6M3Qn61gaw8re5RM1MqXnBqfY13tN"));
        long routeDone = System.nanoTime();


        TransactionV1 transactionV1 = new TransactionV1();
        TransactionConfigV1 config = new TransactionConfigV1()
                .setComputeUnitLimit(178_000)
                .setPriorityFeeLamports(4_596)
                .setLoadedAccountsDataSizeLimit(10 * 1024 * 1024)
                //.setHeapSize(32768)
                ;
        transactionV1.setConfig(config);

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(), new PublicKey(jitAddress), tipPirce);
        transactionV1.addInstruction(tranIn);

        for (TransactionInstruction transactionInstruction : list) {
            transactionV1.addInstruction(transactionInstruction);
        }
        TransactionInstruction instruction = TokenProgram.closeAccount(solCreate,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transactionV1.addInstruction(instruction);

//        String blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
//        SimulatedTransaction sim = rpc.simulateTransactionV1(transactionV1,fundAcc,blockHash);
//        if (sim.getValue().getErr() != null) return;
//        System.out.println(1111);
//
//        blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
//        String tx = jitRpcApi.sendTransactionV1(transactionV1,fundAcc,blockHash);
//        System.out.println(tx);

        var end  = System.nanoTime();

        long duration = end - start;
        log.info("localHost time in nanoseconds: {} ns", duration);
        System.out.println(duration);
        // 分段计时仅用于定位本地构造瓶颈，三次 RPC 读取不在统计范围内。
        System.out.printf("LaunchLab=%d ns, DLMM=%d ns, Raydium=%d ns, Router=%d ns, TX=%d ns%n",
                launchDone - start, dlmmDone - launchDone, rayDone - dlmmDone,
                routeDone - rayDone, end - routeDone);
    }

    @Test
    public void forSell() throws Exception{

        Account fundAcc = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://162.55.199.125:8899/";
        BigDecimal buySol = new BigDecimal("0.001");
        RpcApi rpc = new RpcApi(new RpcClient(url));
        long wsolInput = buySol.movePointRight(9).longValue();
        // 卖出数量
        long baseNum = 30194221644L;
        long quoteMinimum = 1L;
        long targetMinimum = 1L;
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length - 1)];
        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();


        PublicKey mintLpAddress = new PublicKey("8MC2TdS5cU5PXVn9j7wcaz6p1g1t5jCpCCNzbEYrTqLX");

        //假如说 发现是usdc池子 先wsol 转usdc usdc 转换qoute
        PublicKey oneQuoteUsDc = new PublicKey("58oQChx4yWmvKdwLLZzBi4ChoCc2fqCUWBkwMihLYQo2");
        PublicKey oneBaseMint = new PublicKey("So11111111111111111111111111111111111111112");
        PublicKey oneQuoteMint = new PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        // quote 池子 oPAiAikWTaFj9RYoRFD35ccfwhnMcB3ThgBZRHSkjTZ 币对
        PublicKey twoLpAddress = new PublicKey("2ZWxT3niYjyudmDMDVar9ajNE42RkwYdzZBh6TiMuKQY");


        PublicKey solCreate = RouterTradeBuilder.ata(fundAcc.getPublicKey(), RouterTradeBuilder.WSOL_MINT, TokenProgram.PROGRAM_ID);



        //假如说 发现是usdc池子 先wsol 转usdc usdc 转换qoute
        AccountInfo accountInfo = rpc.getAccountInfo(oneQuoteUsDc);
        if (accountInfo.getValue() == null) return;
        var lpAccountInfo = rpc.getAccountInfo(twoLpAddress);
        if (lpAccountInfo.getValue() == null) return;


        // mint的 Lp
        var mintAccountInfo = rpc.getAccountInfo(mintLpAddress);
        if (mintAccountInfo.getValue() == null) return;

        for (int i = 0; i < 5; i++) {
// 先 mint sell quote
            // qoute 转 usdc
            // usdc 转 wsol



            LiquidityStateV4 stateV4 = LiquidityStateV4.decode(accountInfo.getDecodedData());

            LaunchLabPoolStateParser.PoolState launchPool = LaunchLabPoolStateParser.parse(mintAccountInfo.getDecodedData());
            PublicKey mintCreate = launchPool.creator();
            PublicKey mintPlatformFeeVault = PublicKey.findProgramAddress(List.of(launchPool.platformConfig().toByteArray(), launchPool.quoteMint().toByteArray()), LaunchLabLegs.PROGRAM).getAddress();
            var mintCreatorFeeVault = PublicKey.findProgramAddress(List.of(mintCreate.toByteArray(), launchPool.quoteMint().toByteArray()), LaunchLabLegs.PROGRAM).getAddress();
            LaunchLabLegs.Pool lauPool = new LaunchLabLegs.Pool(launchPool.baseMint(),
                    launchPool.quoteMint(),
                    launchPool.baseTokenProgram(),
                    launchPool.quoteTokenProgram(),
                    mintLpAddress,
                    launchPool.baseVault(),
                    launchPool.quoteVault(),
                    launchPool.platformConfig(),
                    mintPlatformFeeVault,
                    mintCreatorFeeVault,launchPool.globalConfig());
            var targetSell = LaunchLabLegs.sell(lauPool,fundAcc.getPublicKey(),baseNum,quoteMinimum);
            long launchDone = System.nanoTime();


            var dec = lpAccountInfo.getDecodedData();
            MeteoraDlmmLbPairParser.LbPair lbPair = MeteoraDlmmLbPairParser.parse(dec);

            var start = System.nanoTime();

            // 根据链上 Token Mint X Program Flag
            PublicKey tokenProgramX = lbPair.tokenProgramX();
            //Token Mint Y Program Flag
            PublicKey tokenProgramY = lbPair.tokenProgramY();
            // LbPair 已保存真实 Oracle 地址，直接使用，避免重复推导 PDA。
            PublicKey oracle = lbPair.oracle();

            PublicKey quoteMint = lbPair.tokenXMint();
            PublicKey quoteQuote = lbPair.tokenYMint();
            // 两侧 Reserve 已在本次链上快照里，直接读取比重新推导更快。
            PublicKey reserveX = lbPair.reserveX();
            PublicKey reserveY = lbPair.reserveY();
            //公式 active_id / 70  四舍五入 9  bin_array_bitmap 不存在 9
            // 需要访问的 BinArray index 是否超出了 LbPair 内置 bitmap 的范围
            var index = new BigDecimal(lbPair.activeId()).divide(new BigDecimal("70"), 0, RoundingMode.HALF_UP).intValue();
            // 默认位图足够时不推导最终不会传入交易的扩展 PDA。
            PublicKey bitmapExtension = lbPair.binArrayBitmap().length > index
                    ? null : MeteoraDlmmPda.bitmapExtension(twoLpAddress);
            List<PublicKey> binArrays = MeteoraDlmmLbPairParser.buildBinArraysForSwap(twoLpAddress, lbPair, true, 4);
            MeteoraLegs.Dlmm pool = new MeteoraLegs.Dlmm(twoLpAddress, reserveX, reserveY,
                    quoteMint, quoteQuote, tokenProgramX, tokenProgramY, oracle, bitmapExtension, binArrays);
            // 卖出的话  inputMint 参数转 quoteMint 也就是 tokenXMint， 之前参数是 tokenYMint
            // qoute转 usdc
            RouterLeg met = MeteoraLegs.sellDlmm(pool, fundAcc.getPublicKey(), quoteMinimum, quoteMinimum);
            long dlmmDone = System.nanoTime();

            //System.out.println(met);



            // usdc转 wsol
            // oneQuoteMint 比如是 sol 转 usdc 那就 填usdc地址
            var rayUsdcPool = new RaydiumLegs.AmmV4(oneQuoteUsDc, stateV4.baseMint, stateV4.quoteMint,
                    stateV4.baseVault, stateV4.quoteVault, TOKEN);

            var entry = RaydiumLegs.ammV4(rayUsdcPool, fundAcc.getPublicKey(), oneQuoteMint, quoteMinimum, quoteMinimum);
            long rayDone = System.nanoTime();


            RouterTradeBuilder.ThreeHopRouteSwap swap = new RouterTradeBuilder.ThreeHopRouteSwap(
                    fundAcc.getPublicKey(), targetSell, met, entry, lauPool.baseTokenProgram(), tokenProgramX, tokenProgramY, TOKEN,
                    baseNum, quoteMinimum, quoteMinimum, quoteMinimum,
                    true, true, true);

            List<TransactionInstruction> list = RouterTradeBuilder.sellCreWSol(swap,new PublicKey("CzjYCxfENUJm9BD6M3Qn61gaw8re5RM1MqXnBqfY13tN"));
            long routeDone = System.nanoTime();


            TransactionV1 transactionV1 = new TransactionV1();
            TransactionConfigV1 config = new TransactionConfigV1()
                    .setComputeUnitLimit(178_000)
                    .setPriorityFeeLamports(4_596)
                    .setLoadedAccountsDataSizeLimit(10 * 1024 * 1024)
                    //.setHeapSize(32768)
                    ;
            transactionV1.setConfig(config);

            TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(), new PublicKey(jitAddress), tipPirce);
            transactionV1.addInstruction(tranIn);

            for (TransactionInstruction transactionInstruction : list) {
                transactionV1.addInstruction(transactionInstruction);
            }
            TransactionInstruction instruction = TokenProgram.closeAccount(solCreate,fundAcc.getPublicKey(),fundAcc.getPublicKey());
            transactionV1.addInstruction(instruction);

//        String blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
//        SimulatedTransaction sim = rpc.simulateTransactionV1(transactionV1,fundAcc,blockHash);
//        if (sim.getValue().getErr() != null) return;
//        System.out.println(1111);
//
//        blockHash = rpc.getLatestBlockhash().getValue().getBlockhash();
//        String tx = jitRpcApi.sendTransactionV1(transactionV1,fundAcc,blockHash);
//        System.out.println(tx);

            var end  = System.nanoTime();

            long duration = end - start;
            log.info("localHost time in nanoseconds: {} ns", duration);
            System.out.println(duration);
            // 分段计时仅用于定位本地构造瓶颈，三次 RPC 读取不在统计范围内。
            System.out.printf("LaunchLab=%d ns, DLMM=%d ns, Raydium=%d ns, Router=%d ns, TX=%d ns%n",
                    launchDone - start, dlmmDone - launchDone, rayDone - dlmmDone,
                    routeDone - rayDone, end - routeDone);
        }
    }

}
