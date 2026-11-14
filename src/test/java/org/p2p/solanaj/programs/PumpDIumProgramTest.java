package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;



import okhttp3.OkHttpClient;
import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.utils.ray.PumpApiIsComPoolInfo;
import org.p2p.solanaj.utils.ray.PumpApiPoolInfo;
import org.p2p.solanaj.utils.ray.RayCPmmPoolInfo;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class PumpDIumProgramTest {
    private static final Long SOL_NUM = 1000000000L;

    private static final PublicKey NESTED_MINT = new PublicKey("So11111111111111111111111111111111111111112"); // Wrapped SOL mint
    private static final PublicKey PAYER = new PublicKey("11111111111111111111111111111111");

    private static final String[] JIT_ADDRESS = new String[]{
            "ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt",
            "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL",
            "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5",
            "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT",
            "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY",
            "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh",
            "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe",
            "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49"
    };

    @Test
    public void buy() throws RpcException {
        Account fundAcc = Env.account();

        String url = Cluster.HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();
        // 9位小数
        double lamportsTotal = 0.012;
        // 0.002
        int lamports = new BigDecimal(lamportsTotal).add(new BigDecimal("0.00203928")).multiply(new BigDecimal(SOL_NUM)).intValue();
        //lamports = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).intValue();

        Transaction transaction  = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(200000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(1000000));
        PublicKey newPublicKey = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), NESTED_MINT);
        if(newPublicKey == null){
            String send =  new Account().getPublicKeyBase58().substring(0,32);
            newPublicKey = Account.createWithSeed(fundAcc.getPublicKey(),send,TokenProgram.PROGRAM_ID);
            TransactionInstruction instruction = SystemProgram.createAccountWithSeedInstruction(fundAcc.getPublicKey(),newPublicKey,fundAcc.getPublicKey(),
                    send
                    ,lamports,165,TokenProgram.PROGRAM_ID);
            transaction.addInstruction(instruction);
            TransactionInstruction init = TokenProgram.initializeAccount(newPublicKey,NESTED_MINT,fundAcc.getPublicKey());
            transaction.addInstruction(init);
        }


        PumpApiPoolInfo poolInfo = PumpDIumProgram.refPumpInfo(rpcApi,"8Rj3EK6z3DozoRMasYXpVNUrfkS1K2EQXzQN5dmU3djy");
        if(poolInfo==null) return;
        PublicKey tokenAccountOut = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), new PublicKey(poolInfo.getTokenAddress()));
        if(tokenAccountOut == null){
            TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(), fundAcc.getPublicKey()
                    , new PublicKey(poolInfo.getTokenAddress()));
            tokenAccountOut = instruction.getKeys().get(1).getPublicKey();
            transaction.addInstruction(instruction);

        }
        long amountIn = new BigDecimal(lamportsTotal).multiply(new BigDecimal("0.95")).multiply(new BigDecimal(SOL_NUM)).intValue();
        Long minAmountOut = new BigDecimal(lamportsTotal).divide(new BigDecimal("0.00000160"),6,BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("1000000"))
                .multiply(new BigDecimal("0.85")).longValue();
        TransactionInstruction instruction = PumpDIumProgram.makeSwapFixedInInstruction(poolInfo,newPublicKey,tokenAccountOut,amountIn,minAmountOut,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

        transaction.addInstruction(TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey()));

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
        transaction.addInstruction(tranIn);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        if(simulatedTransaction.getValue().getErr()!=null) {
            System.out.println(simulatedTransaction.getValue().getErr().toString());
            return;
        }

        String result = jitRpcApi.sendTransaction(transaction,fundAcc,recentBlockHash);
        System.out.println(result);

    }

    /**
     * 卖出 CPmm
     */
    @Test
    public void sell() throws RpcException {

        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        int lamports = 2039280;
        Transaction transaction  = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(300000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(13333333));
        PublicKey newPublicKey = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), NESTED_MINT);
        if(newPublicKey == null){
            String send =  new Account().getPublicKeyBase58().substring(0,32);
            newPublicKey = Account.createWithSeed(fundAcc.getPublicKey(),send,TokenProgram.PROGRAM_ID);
            TransactionInstruction instruction = SystemProgram.createAccountWithSeedInstruction(fundAcc.getPublicKey(),newPublicKey,fundAcc.getPublicKey(),
                    send
                    ,lamports,165,TokenProgram.PROGRAM_ID);
            transaction.addInstruction(instruction);
            TransactionInstruction init = TokenProgram.initializeAccount(newPublicKey,NESTED_MINT,fundAcc.getPublicKey());
            transaction.addInstruction(init);
        }
        PumpApiPoolInfo poolInfo = PumpDIumProgram.refPumpInfo(rpcApi,"8Rj3EK6z3DozoRMasYXpVNUrfkS1K2EQXzQN5dmU3djy");
        if(poolInfo==null) return;
        Map<String ,Object > map = new HashMap<>();
        map.put("mint",poolInfo.getTokenAddress());
        TokenAccountInfo tokenAccountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(), map,new HashMap<>());
        var any= tokenAccountInfo.getValue().stream().filter(d-> d.getAccount().getData().getParsed().getInfo().getMint().equals(poolInfo.getTokenAddress())).findAny();
        if(!any.isPresent()) return;
        PublicKey tokenAccountOut = new PublicKey(any.get().getPubkey());

        long amountIn = Long.parseLong(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getAmount());
        if(amountIn==0L) return;
        amountIn = new BigDecimal(amountIn).multiply(new BigDecimal("0.99")).longValue();
        long minAmountOut = (new BigDecimal(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmountString())
                .multiply(new BigDecimal("0.000001750")))
                .multiply(new BigDecimal(SOL_NUM)).longValue();
        minAmountOut = BigDecimal.valueOf(minAmountOut).multiply(new BigDecimal("0.95")).longValue();
        //minAmountOut  =277145L;
        TransactionInstruction instruction = PumpDIumProgram.makeSwapFixedOutInstruction(poolInfo,newPublicKey,tokenAccountOut,minAmountOut,amountIn,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        if(simulatedTransaction.getValue().getErr()!=null) {
            System.out.println(simulatedTransaction.getValue().getErr().toString());
            return;
        }
        String result = rpcApi.sendTransaction(transaction,fundAcc);
        System.out.println(result);
        List<String > list = new ArrayList<>();
        list.add(result);

    }


    /**
     * 未打满的 buy 购买
     * @throws RpcException
     */
    @Test
    public void buyISCom()throws RpcException{
        String url = Cluster.HELIUS.getEndpoint();
        url = Env.require("HELIUS_RPC_URL");
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder().header("Origin", "https://pump.fun ").build()))
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url,httpClient));

        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();
        // 9位小数
        double lamportsTotal = 0.012;
        // 0.002
        int lamports = new BigDecimal(lamportsTotal).add(new BigDecimal("0.00203928")).multiply(new BigDecimal(SOL_NUM)).intValue();
        //lamports = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).intValue();

        Transaction transaction  = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(300000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(100000));
        PumpApiIsComPoolInfo poolInfo = PumpDIumProgram.refIsComPumpInfo("63yAmsTng5uxA1BnvJJnJsDeKfjQcFmNHzjwVxvRpump"
                ,"89DB2sFsTttiHUcKHDHXC7RwUdF67U6bkHkLrVfk2Fx3"
                ,"6hW9V1YNLPWQZGQNzaXg3opM1LjxM4wxhR9taCwQnCTa",0);
        PublicKey tokenAccountOut = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), new PublicKey(poolInfo.getMint()));
        if(tokenAccountOut == null){
            TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(), fundAcc.getPublicKey()
                    , new PublicKey(poolInfo.getMint()));
            tokenAccountOut = instruction.getKeys().get(1).getPublicKey();
            transaction.addInstruction(instruction);

        }
        long amountIn = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).intValue();
        long minAmountOut = new BigDecimal(lamportsTotal).divide(new BigDecimal("0.00000002997"),6,BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("1000000"))
                .multiply(new BigDecimal("0.95")).longValue();
        TransactionInstruction instruction = PumpDIumProgram.makeSwapFixedInInstruction(poolInfo,tokenAccountOut,amountIn,minAmountOut,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);


//        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
//        transaction.addInstruction(tranIn);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        if(simulatedTransaction.getValue().getErr()!=null) {
            System.out.println(simulatedTransaction.getValue().getErr().toString());
            return;
        }

//        String result = jitRpcApi.sendTransaction(transaction,fundAcc,recentBlockHash);
//        System.out.println(result);
    }

    /**
     * 未打满的 sell 卖出
     * @throws RpcException
     */
    @Test
    public void sellISCom()throws RpcException{
        String url = Cluster.HELIUS.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();
        // 9位小数
        double lamportsTotal = 0.012;
        // 0.002
        int lamports = new BigDecimal(lamportsTotal).add(new BigDecimal("0.00203928")).multiply(new BigDecimal(SOL_NUM)).intValue();
        //lamports = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).intValue();

        Transaction transaction  = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(300000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(100000));
        PumpApiIsComPoolInfo poolInfo = PumpDIumProgram.refIsComPumpInfo("63yAmsTng5uxA1BnvJJnJsDeKfjQcFmNHzjwVxvRpump"
                ,"89DB2sFsTttiHUcKHDHXC7RwUdF67U6bkHkLrVfk2Fx3"
                ,"6hW9V1YNLPWQZGQNzaXg3opM1LjxM4wxhR9taCwQnCTa",1);
        Map<String ,Object > map = new HashMap<>();
        map.put("mint",poolInfo.getMint());
        TokenAccountInfo tokenAccountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(), map,new HashMap<>());
        var any= tokenAccountInfo.getValue().stream().filter(d-> d.getAccount().getData().getParsed().getInfo().getMint().equals(poolInfo.getMint())).findAny();
        if(!any.isPresent()) return;
        PublicKey tokenAccountOut = new PublicKey(any.get().getPubkey());

        long minAmountOut = Long.parseLong(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getAmount());
        if(minAmountOut==0L) return;
        minAmountOut = new BigDecimal(minAmountOut).multiply(new BigDecimal("0.99")).longValue();
        long amountIn = (new BigDecimal(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmountString())
                .multiply(new BigDecimal("0.000000029743")))
                .multiply(new BigDecimal(SOL_NUM)).longValue();
        amountIn = BigDecimal.valueOf(amountIn).multiply(new BigDecimal("0.98")).longValue();

        TransactionInstruction instruction = PumpDIumProgram.makeSwapFixedOutInstruction(poolInfo,tokenAccountOut,amountIn,minAmountOut,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);


//        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
//        transaction.addInstruction(tranIn);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        if(simulatedTransaction.getValue().getErr()!=null) {
            System.out.println(simulatedTransaction.getValue().getErr().toString());
            return;
        }

        String result = jitRpcApi.sendTransaction(transaction,fundAcc,recentBlockHash);
        System.out.println(result);
    }

}
