package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;

import cn.hutool.core.codec.Base64;

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
import org.p2p.solanaj.rpc.types.*;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;
import org.p2p.solanaj.token.TokenManager;
import org.p2p.solanaj.utils.ray.LiquidityStateV4;
import org.p2p.solanaj.utils.ray.MarketStateV3;
import org.p2p.solanaj.utils.ray.RayApiPoolInfoV5;
import org.p2p.solanaj.utils.ray.SplMintLayout;
import org.p2p.solanaj.utils.wsol.SolUtils;

import java.math.BigDecimal;
import java.util.*;

public class SolToWSolTest {

    private static final PublicKey NESTED_MINT = new PublicKey("So11111111111111111111111111111111111111112"); // Wrapped SOL mint

    private static final PublicKey PAYER = new PublicKey("11111111111111111111111111111111");

    private static final PublicKey OWNER_MINT = new PublicKey("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So");

    private static final Long SOL_NUM = 1000000000L;



    /**
     *  SOL 更换 WSol
     */
    @Test
    public void SolToWSol() throws RpcException {
        String url = Cluster.DEV_HELIUS.getEndpoint();
        ////System.out.println(newAcc.getPublicKey());
        //url = "https://testnet.block-engine.jito.wtf/api/v1/transactions";
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        // 9位小数
        double lamportsTotal = 0.05;
        int lamports = (int) (lamportsTotal * SOL_NUM);
        Account fundAcc = Env.account();
        List<TransactionInstruction> list = SolUtils.SolToWSolUtils(fundAcc.getPublicKey(),rpcApi,lamports);

        Transaction transaction =  new Transaction();
        // 优先费
//        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(5));
//        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(200000));

        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(300000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(13333333));
        for (TransactionInstruction instruction : list) {
            transaction.addInstruction(instruction);
        }


        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        String result = rpcApi.sendTransaction(transaction,fundAcc,recentBlockHash);
        //System.out.println(result);
    }

    /**
     * WSol 更换 SOL
     */
    @Test
    public void WSolToSol() throws RpcException {
        String url = Cluster.QUINET.getEndpoint();
        ////System.out.println(newAcc.getPublicKey());
        //url = "https://testnet.block-engine.jito.wtf/api/v1/transactions";
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        // 9位小数
        double lamportsTotal = 0.1;
        int lamports = (int) (lamportsTotal * SOL_NUM);
        Account fundAcc = Env.account();
        List<TransactionInstruction> list = SolUtils.WSolToSolUtils(fundAcc,rpcApi);
        Transaction transaction = new Transaction();
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(300000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(13333333));

//        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(19465));
//        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(300_000));
        for (TransactionInstruction instruction : list) {
            transaction.addInstruction(instruction);
        }
        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
//        transaction.setRecentBlockHash(recentBlockHash);
//        transaction.sign(fundAcc);
//        byte[] serializedTransaction = transaction.serialize();
//
//        String base64Trx = java.util.Base64.getEncoder().encodeToString(serializedTransaction);
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        String result = rpcApi.sendTransaction(transaction,fundAcc);
        //System.out.println(result);
    }

    /**
     * 根据池子获取信息
     */
    @Test
    public void buyRay() throws RpcException {

        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        // 9位小数
        double lamportsTotal = 0.012;

        // 0.002
        int lamports = new BigDecimal(lamportsTotal).add(new BigDecimal("0.00203928")).multiply(new BigDecimal(SOL_NUM)).intValue();

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

//            TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(), fundAcc.getPublicKey(), NESTED_MINT);
//            newPublicKey = instruction.getKeys().get(1).getPublicKey();
//            transaction.addInstruction(instruction);
        }
        //transaction.addInstruction(SystemProgram.transfer(fundAcc.getPublicKey(), newPublicKey, lamports));

        RayApiPoolInfoV5 rayApiPoolInfoV5 = RayDIumProgram.refRay(rpcApi,"GZdjboSjQYcfgtRb2eAASVgp2PCoyBaz3mmar1qa4Qof",null,null);
        if(rayApiPoolInfoV5==null) return;
        PublicKey tokenAccountOut = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), new PublicKey(rayApiPoolInfoV5.getQuoteMint()));
        if(tokenAccountOut == null){
            TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(), fundAcc.getPublicKey(), new PublicKey(rayApiPoolInfoV5.getQuoteMint()));
            tokenAccountOut = instruction.getKeys().get(1).getPublicKey();
            transaction.addInstruction(instruction);
        }
        Long amountIn = new BigDecimal(lamportsTotal).multiply(new BigDecimal(SOL_NUM)).longValue();
        // 0.000000027
        Long minAmountOut = new BigDecimal(lamportsTotal).divide(new BigDecimal("0.00000002603"),6,BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("1000000"))

                .multiply(new BigDecimal("0.75")).longValue();

        TransactionInstruction instruction = RayDIumProgram.makeSwapFixedInInstruction(rayApiPoolInfoV5,newPublicKey,
                tokenAccountOut, amountIn,minAmountOut,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        if(simulatedTransaction.getValue().getErr()!=null) {
            System.out.println(simulatedTransaction.getValue().getErr().toString());
            return;
        }
//        String result = rpcApi.sendTransaction(transaction,fundAcc);
//        System.out.println(result);
//        List<String> signatures = new ArrayList<>();
//        signatures.add(result);
//        getSignTrue(signatures,rpcApi);
    }



    @Test
    public void getPROGRAM() throws RpcException {
        String url = Cluster.DEV_HELIUS.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        Transaction transaction  = new Transaction();
        long lamports = 2039280;
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
        TransactionInstruction instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        //if(simulatedTransaction.getValue().)
        String result = rpcApi.sendTransaction(transaction,fundAcc);
        System.out.println(result);
    }
    /**
     * ray 池子 去除
     */
    @Test
    public void sellRay() throws RpcException {

        String url = Cluster.QUINET.getEndpoint();
        Account fundAcc = Env.account();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        // 9位小数
        double lamportsTotal = 0.012;
        int lamports = (int) (lamportsTotal * SOL_NUM);
        lamports = 2039280;
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

        RayApiPoolInfoV5 rayApiPoolInfoV5 = RayDIumProgram.refRay(rpcApi,"GZdjboSjQYcfgtRb2eAASVgp2PCoyBaz3mmar1qa4Qof",null,null);
        if(rayApiPoolInfoV5==null) return;
        Map<String ,Object > map = new HashMap<>();
        map.put("mint",rayApiPoolInfoV5.getQuoteMint());
        TokenAccountInfo tokenAccountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(), map,new HashMap<>());
        var any= tokenAccountInfo.getValue().stream().filter(d-> d.getAccount().getData().getParsed().getInfo().getMint().equals(rayApiPoolInfoV5.getQuoteMint())).findAny();
        if(!any.isPresent()) return;
        PublicKey tokenAccountOut = new PublicKey(any.get().getPubkey());
        long amountIn = Long.parseLong(any.get().getAccount().getData().getParsed().getInfo().getTokenAmount().getAmount());
        TransactionInstruction instruction = RayDIumProgram.makeSwapFixedOutInstruction(rayApiPoolInfoV5,tokenAccountOut,
                newPublicKey, (long) amountIn,0L,fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);

        String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,recentBlockHash);
        //if(simulatedTransaction.getValue().)
        String result = rpcApi.sendTransaction(transaction,fundAcc);
        System.out.println(result);
        List<String > list = new ArrayList<>();
        list.add(result);
        getSignTrue(list,rpcApi);
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
        String result = "29itSR7nemjoPYBwEdTnMeQhcFSciQjUNyzoYUYzSWYG1Nua1pg9Qs8LnLkgQFKTzpWHGuXMNkCUkRmDNFWzkbbb";
        List<String> signatures = new ArrayList<>();
        signatures.add(result);
        SignatureStatuses signatureStatuses = rpcApi.getSignatureStatuses(signatures,true);
        if(signatureStatuses.getValue()==null || signatureStatuses.getValue().size()<=0) return;
        for (int i = 0; i < signatures.size(); i++) {
            if(signatureStatuses.getValue().get(i)==null){
                System.out.println("交易未上链!");
                return;
            }
            if(signatureStatuses.getValue().stream().noneMatch(d-> d.getConfirmationStatus().equals("finalized"))){
                System.out.println("交易未上链!");
                return;
            }
            System.out.println("交易已上链!");
        }
    }


}
