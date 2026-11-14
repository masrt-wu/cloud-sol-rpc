package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;


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
import org.p2p.solanaj.rpc.types.SignatureStatuses;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.utils.ray.RayApiPoolInfoV5;
import org.p2p.solanaj.utils.ray.RayCPmmPoolInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class RayCPmmTest {


    private static final PublicKey NESTED_MINT = new PublicKey("So11111111111111111111111111111111111111112"); // Wrapped SOL mint

    private static final PublicKey PAYER = new PublicKey("11111111111111111111111111111111");

    private static final PublicKey OWNER_MINT = new PublicKey("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So");

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

    private static final Long SOL_NUM = 1000000000L;

    /**
     * 购买CPmm
     */
    @Test
    public void buyCPmmRay() throws RpcException {

        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        // 9位小数
        double lamportsTotal = 0.01;
        long lamports = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).longValue();
        BigDecimal price = new BigDecimal("0.00000060785");
        long minAmountOut = new BigDecimal(lamportsTotal).divide(price,2, RoundingMode.DOWN).multiply(new BigDecimal("0.95")).longValue();
        minAmountOut = BigDecimal.valueOf(minAmountOut).multiply(new BigDecimal("1000000")).longValue();
        // 小费金额
        long tipPirce = (long) (0.000001*SOL_NUM);
        Random random = new Random();
        String jitAddress = JIT_ADDRESS[random.nextInt(JIT_ADDRESS.length-1)];

        long accLam = 2039280L;
        Transaction transaction  = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(100000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(10000000));
        PublicKey newPublicKey = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), NESTED_MINT);
        if(newPublicKey == null){
            String send =  new Account().getPublicKeyBase58().substring(0,32);
            newPublicKey = Account.createWithSeed(fundAcc.getPublicKey(),send,TokenProgram.PROGRAM_ID);
            TransactionInstruction instruction = SystemProgram.createAccountWithSeedInstruction(fundAcc.getPublicKey(),newPublicKey,fundAcc.getPublicKey(),
                    send
                    ,BigDecimal.valueOf(lamports).add(BigDecimal.valueOf(accLam)).longValue(),165,TokenProgram.PROGRAM_ID);
            transaction.addInstruction(instruction);
            TransactionInstruction init = TokenProgram.initializeAccount(newPublicKey,NESTED_MINT,fundAcc.getPublicKey());
            transaction.addInstruction(init);
        }


        RayCPmmPoolInfo rayCPmmPoolInfo = RayDIumProgram.refCPmmRay(rpcApi,"9y6fWrWH7HyCu8Ef42Z8yjcYueCkgmh3u8ngoCoqrC7B");
        if(rayCPmmPoolInfo==null) return;
        String tokenMint = rayCPmmPoolInfo.getMintB();
        if(rayCPmmPoolInfo.getMintB().equals(NESTED_MINT.toBase58()))
            tokenMint = rayCPmmPoolInfo.getMintA();
        PublicKey tokenAccountOut = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), new PublicKey(tokenMint));
        if(tokenAccountOut == null){
            TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(), fundAcc.getPublicKey(), new PublicKey(tokenMint));
            tokenAccountOut = instruction.getKeys().get(1).getPublicKey();
            transaction.addInstruction(instruction);
        }
        rayCPmmPoolInfo.setPayer(fundAcc.getPublicKeyBase58());
        rayCPmmPoolInfo.setTokenAccountIn(newPublicKey.toBase58());
        rayCPmmPoolInfo.setTokenAccountOut(tokenAccountOut.toBase58());

        TransactionInstruction instruction = RayDIumProgram.makeSwapCPmmBaseInInInstruction(rayCPmmPoolInfo
                , lamports, minAmountOut);
        transaction.addInstruction(instruction);
        instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

//        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
//        transaction.addInstruction(tranIn);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        System.out.println(simulatedTransaction);
        System.out.println(simulatedTransaction.getValue().getErr() ==null );

        String result = jitRpcApi.sendTransaction(transaction,fundAcc,recentBlockHash);
        System.out.println(result);
        List<String> signatures = new ArrayList<>();
        signatures.add(result);
        getSignTrue(signatures,rpcApi);
    }

    /**
     * 卖出 CPmm
     */
    @Test
    public void sellCPmmRay() throws RpcException {

        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        int lamports = 2039280;
        BigDecimal price = new BigDecimal("0.00000062690");
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
        RayCPmmPoolInfo rayCPmmPoolInfo = RayDIumProgram.refCPmmRay(rpcApi,"9y6fWrWH7HyCu8Ef42Z8yjcYueCkgmh3u8ngoCoqrC7B");
        if(rayCPmmPoolInfo==null) return;
        Map<String ,Object > map = new HashMap<>();
        map.put("mint",rayCPmmPoolInfo.getMintB());
        TokenAccountInfo tokenAccountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(), map,new HashMap<>());
        var any= tokenAccountInfo.getValue().stream().filter(d-> d.getAccount().getData().getParsed().getInfo().getMint().equals(rayCPmmPoolInfo.getMintB())).findAny();
        if(!any.isPresent()) return;
        PublicKey tokenAccountOut = new PublicKey(any.get().getPubkey());

        rayCPmmPoolInfo.setPayer(fundAcc.getPublicKeyBase58());
        rayCPmmPoolInfo.setTokenAccountIn(tokenAccountOut.toBase58());
        rayCPmmPoolInfo.setTokenAccountOut(newPublicKey.toBase58());
        long amountIn = Long.parseLong(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getAmount());
        if(amountIn==0L) return;
        long minAmountOut = new BigDecimal(amountIn).multiply(price).multiply(new BigDecimal(SOL_NUM)).longValue();

        TransactionInstruction instruction = RayDIumProgram.makeSwapCPmmBaseOutInInstruction(rayCPmmPoolInfo, amountIn,minAmountOut);
        transaction.addInstruction(instruction);
        instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        System.out.println(simulatedTransaction.getValue().getErr() ==null );
        String result = rpcApi.sendTransaction(transaction,fundAcc);
        System.out.println(result);
        List<String > list = new ArrayList<>();
        list.add(result);
        getSignTrue(list,rpcApi);
    }


    @Test
    public void totalCPmmRay() throws RpcException {
        buyCPmmRay();
        sellCPmmRay();
    }

    private static void getSignTrue(List<String > signatures,RpcApi rpcApi) throws RpcException {
        List<String > successList = signatures;
        for (int i = 0; i < 10; i++) {
            if(successList.size()<=0) break;
            SignatureStatuses signatureStatuses = rpcApi.getSignatureStatuses(successList,true);
            for (int k = 0; k < signatures.size(); k++) {
                if(signatureStatuses.getValue().get(k)==null){
                    System.out.println(signatures.get(k));
                    System.out.println("交易未上链!");
                    continue;
                }
                if(signatureStatuses.getValue().get(k).getConfirmationStatus().equals("finalized") || signatureStatuses.getValue().get(k).getConfirmationStatus().equals("confirmed"))
                {
                    System.out.println(signatures.get(k));
                    System.out.println("交易已上链!");
                    successList.remove(signatures.get(k));
                    continue;
                }
                System.out.println("交易未上链!");
            }

            try {
                Thread.sleep(1000L);
            }catch (Exception e){

            }
        }
    }

    public static void main(String[] args) throws RpcException {
        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        Map<String ,Object > map = new HashMap<>();
        map.put("mint","3Sz6vvbteTr7YZEgg5SX4pTxuNL8937hrYjfXrqtL8RL");
        TokenAccountInfo tokenAccountInfo = rpcApi.getTokenAccountsByOwner(new PublicKey("EaN1vRcBAWuFTGebN5v4zih2xfaTvAEJ5V2u1kWh98s"), map,new HashMap<>());

        System.out.println(tokenAccountInfo);
    }
}
