package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;

import com.alibaba.fastjson2.JSONObject;
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
import org.p2p.solanaj.rpc.types.SplNonceAccountInfo;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.utils.ray.PumpApiIsComToken2022PoolInfo;
import org.p2p.solanaj.utils.ray.PumpToken2022PoolInfoV3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class TestPumpToken2022 {

    private static final long SOL_NUM = 1000000000L;

    private static final Long SPL_NUM = 1000000L;

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


    /**
     * buy
     * pump 内盘数据
     */
    @Test
    public void buyPumpAss() throws RpcException {

        String url = Cluster.HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();

        Account fundAcc = Env.account();
        //BLq8Bz8NuNCGEcrWYn1NMhuYpP9z4mkshywSYybKYbau associated_bonding_curve
        //5zA8Wan66RiM7LYEcyNf5hHVpXEcD5v7k5LysQukS4RH bonding_curve
        String mint = "7F2bUQSUMDYzJmmAs24DNmVK6YaGayHvXP2byk1Ppump";
        String create = "whamNNP9tHoxLg92yHvJPdYhghEoCg1qYTsh5a2oLbx";
        String nonceAddress = "U14ncFuvRfwgjSpmKtzHmcmC8PSNk54hfPUxuTuuR3i";
        Transaction transaction  = new Transaction();

        TransactionInstruction instruction = SystemProgram.nonceAdvance(new PublicKey(nonceAddress),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(200000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(555513));

        BigDecimal quteSol = new BigDecimal("0.00000005800");
        // 买入数量
        BigDecimal buySol = new BigDecimal("0.01");
        BigDecimal amount = buySol.divide(quteSol,5, RoundingMode.HALF_UP).multiply(new BigDecimal(SPL_NUM));
        BigDecimal max_sol_cost = buySol.multiply(new BigDecimal(SOL_NUM));
        max_sol_cost = max_sol_cost.multiply(new BigDecimal("1.02"));

        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(fundAcc.getPublicKey().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);
        PumpApiIsComToken2022PoolInfo info = PumpDIumProgram.refPumpToken2022(mint,associateUser.getAddress().toBase58(),fundAcc.getPublicKeyBase58(),create,null);

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
        transaction.addInstruction(tranIn);


//        instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(),new PublicKey(info.getAssociatedBondingCurve()),new PublicKey(info.getBondingCurve()),new PublicKey(mint));
//        transaction.addInstruction(instruction);

        instruction = AssociatedTokenProgram.createIdempotentNew(fundAcc.getPublicKey(),associateUser.getAddress(),fundAcc.getPublicKey(),new PublicKey(mint));
        transaction.addInstruction(instruction);


        instruction = PumpDIumProgram.makePumpTotalToken2022(info,0,amount.longValue(),max_sol_cost.longValue());
        transaction.addInstruction(instruction);


        String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
        if(simulatedTransaction.getValue().getErr()!= null)return;
        System.out.println(111);
//        SplNonceAccountInfo nonceAccountInfo = rpcApi.getAccountNonceInfo(new PublicKey(nonceAddress));
//        if(nonceAccountInfo==null || nonceAccountInfo.getValue()==null)return;
//
//        blockHash =  nonceAccountInfo.getValue().getData().getParsed().getInfo().getBlockhash();
//        String tx = jitRpcApi.sendTransaction(transaction,fundAcc,blockHash);
//
//        System.out.println(tx);
    }

    @Test
    public void sellPumpAss() throws RpcException {

        String url = Cluster.HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient(Env.require("JITO_AUTH_RPC_URL")));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];
        String pumpAddress = PumpDIumProgram.PUMP_ADDRESS[new Random().nextInt(PumpDIumProgram.PUMP_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();

        Account fundAcc = Env.account();
        //BLq8Bz8NuNCGEcrWYn1NMhuYpP9z4mkshywSYybKYbau associated_bonding_curve
        //5zA8Wan66RiM7LYEcyNf5hHVpXEcD5v7k5LysQukS4RH bonding_curve
        String mint = "7F2bUQSUMDYzJmmAs24DNmVK6YaGayHvXP2byk1Ppump";
        String create = "whamNNP9tHoxLg92yHvJPdYhghEoCg1qYTsh5a2oLbx";
        String nonceAddress = "U14ncFuvRfwgjSpmKtzHmcmC8PSNk54hfPUxuTuuR3i";
        Transaction transaction  = new Transaction();


        TransactionInstruction instruction = SystemProgram.nonceAdvance(new PublicKey(nonceAddress),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(200000));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(555513));

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
        transaction.addInstruction(tranIn);

        BigDecimal quteSol = new BigDecimal("0.00000002800");
        // 买入数量
//        PublicKey.ProgramDerivedAddress sellAcc = PublicKey.findProgramAddress(
//                Arrays.asList(new PublicKey(pumpAddress).toByteArray(),
//                        TokenProgram.TOKEN_2022.toByteArray(),
//                        new PublicKey(mint).toByteArray()
//                ),AssociatedTokenProgram.PROGRAM_ID);

        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(fundAcc.getPublicKey().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);
        Map<String, Object> requiredParams = new HashMap<>();
        requiredParams.put("mint",mint);
        TokenAccountInfo accountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(),requiredParams,new HashMap<>());
        if(accountInfo.getValue()==null || accountInfo.getValue().size()<=0)return;
        String uiAmountString = accountInfo.getValue().get(0).getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmountString();
        BigDecimal amount = new BigDecimal(uiAmountString).multiply(new BigDecimal(SPL_NUM));

        BigDecimal max_sol_cost = new BigDecimal(uiAmountString).multiply(quteSol).multiply(new BigDecimal("0.98"));
        max_sol_cost = max_sol_cost.multiply(new BigDecimal(SOL_NUM));
//         instruction = AssociatedTokenProgram.createIdempotentNew(fundAcc.getPublicKey(),associateUser.getAddress(),fundAcc.getPublicKey(),new PublicKey(mint));
//        transaction.addInstruction(instruction);
//
//        instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(),sellAcc.getAddress(),new PublicKey(pumpAddress),new PublicKey(mint));
//        transaction.addInstruction(instruction);

        PumpApiIsComToken2022PoolInfo info = PumpDIumProgram.refPumpToken2022(mint,associateUser.getAddress().toBase58(),fundAcc.getPublicKeyBase58(),create,null);

        instruction = PumpDIumProgram.makePumpTotalToken2022(info,1,amount.longValue(),max_sol_cost.longValue());
        transaction.addInstruction(instruction);


        String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
        if(simulatedTransaction.getValue().getErr()!= null)return;

        SplNonceAccountInfo nonceAccountInfo = rpcApi.getAccountNonceInfo(new PublicKey(nonceAddress));
        if(nonceAccountInfo==null || nonceAccountInfo.getValue()==null)return;

        blockHash =  nonceAccountInfo.getValue().getData().getParsed().getInfo().getBlockhash();
        String tx = jitRpcApi.sendTransaction(transaction,fundAcc,blockHash);

        System.out.println("sell:"+tx);


        //Gv2Ji4Nqe5EeLG35gG6dRu77M2kZUp2htd4DA8r9dfJg


    }

    /**
     * buy
     * pump 内盘数据
     */
    @Test
    public void buyPumpAssV3() throws RpcException {

        String url = Cluster.K2_NET.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();

        Account fundAcc = new Account(Base58
                .decode(Env.require("SOLANA_PUMP_V3_PRIVATE_KEY_BASE58")));
        //BLq8Bz8NuNCGEcrWYn1NMhuYpP9z4mkshywSYybKYbau associated_bonding_curve
        //5zA8Wan66RiM7LYEcyNf5hHVpXEcD5v7k5LysQukS4RH bonding_curve
        String mint = "CPL2msD8HNyRPxWjfMTFf9KSatyWPuTEnYa1ZvvVpump";
        String nonceAddress = "Ho6MUPpwFhG4FrXVKc5JqtDzUAtuBy6p4WA6oH5cDDtq";
        Transaction transaction  = new Transaction();

        TransactionInstruction instruction = SystemProgram.nonceAdvance(new PublicKey(nonceAddress),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(25820));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(78888));

        BigDecimal quteSol = new BigDecimal("0.00000002844");
        // 买入数量
        BigDecimal buySol = new BigDecimal("0.01");
        BigDecimal amount = buySol.divide(quteSol,5, RoundingMode.HALF_UP).multiply(new BigDecimal(SPL_NUM));
        BigDecimal max_sol_cost = buySol.multiply(new BigDecimal(SOL_NUM));
        max_sol_cost = max_sol_cost.multiply(new BigDecimal("1.02"));

        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(fundAcc.getPublicKey().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);
        PumpToken2022PoolInfoV3 info = PumpDIumProgram.refPumpToken2022V3(mint,"So11111111111111111111111111111111111111112",
                associateUser.getAddress().toBase58(),fundAcc.getPublicKeyBase58(),"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

//        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
//        transaction.addInstruction(tranIn);


//        instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(),new PublicKey(info.getAssociatedBondingCurve()),new PublicKey(info.getBondingCurve()),new PublicKey(mint));
//        transaction.addInstruction(instruction);

        instruction = AssociatedTokenProgram.createIdempotentNew(fundAcc.getPublicKey(),associateUser.getAddress(),fundAcc.getPublicKey(),new PublicKey(mint));
        transaction.addInstruction(instruction);


        instruction = PumpDIumProgram.makePumpTotalToken2022V3(info,0,amount.longValue(),max_sol_cost.longValue());
        transaction.addInstruction(instruction);


        String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
        if(simulatedTransaction.getValue().getErr()!= null)return;
        System.out.println(111);
        SplNonceAccountInfo nonceAccountInfo = rpcApi.getAccountNonceInfo(new PublicKey(nonceAddress));
        if(nonceAccountInfo==null || nonceAccountInfo.getValue()==null)return;

        blockHash =  nonceAccountInfo.getValue().getData().getParsed().getInfo().getBlockhash();
        String tx = rpcApi.sendTransaction(transaction,fundAcc,blockHash);

        System.out.println(tx);
    }

    @Test
    public void sellPumpAssV3() throws RpcException {

        String url = Cluster.K2_NET.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient(Env.require("JITO_AUTH_RPC_URL")));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length-1)];
        String pumpAddress = PumpDIumProgram.PUMP_ADDRESS[new Random().nextInt(PumpDIumProgram.PUMP_ADDRESS.length-1)];

        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();

        Account fundAcc = new Account(Base58
                .decode(Env.require("SOLANA_PUMP_V3_PRIVATE_KEY_BASE58")));
        //BLq8Bz8NuNCGEcrWYn1NMhuYpP9z4mkshywSYybKYbau associated_bonding_curve
        //5zA8Wan66RiM7LYEcyNf5hHVpXEcD5v7k5LysQukS4RH bonding_curve
        String mint = "CPL2msD8HNyRPxWjfMTFf9KSatyWPuTEnYa1ZvvVpump";
        String nonceAddress = "Ho6MUPpwFhG4FrXVKc5JqtDzUAtuBy6p4WA6oH5cDDtq";
        Transaction transaction  = new Transaction();


        TransactionInstruction instruction = SystemProgram.nonceAdvance(new PublicKey(nonceAddress),fundAcc.getPublicKey());
        transaction.addInstruction(instruction);
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(25820));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(78888));

//        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(jitAddress),tipPirce);
//        transaction.addInstruction(tranIn);

        BigDecimal quteSol = new BigDecimal("0.00000002844");
        // 买入数量
//        PublicKey.ProgramDerivedAddress sellAcc = PublicKey.findProgramAddress(
//                Arrays.asList(new PublicKey(pumpAddress).toByteArray(),
//                        TokenProgram.TOKEN_2022.toByteArray(),
//                        new PublicKey(mint).toByteArray()
//                ),AssociatedTokenProgram.PROGRAM_ID);

        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(fundAcc.getPublicKey().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);
        Map<String, Object> requiredParams = new HashMap<>();
        requiredParams.put("mint",mint);
        TokenAccountInfo accountInfo = rpcApi.getTokenAccountsByOwner(fundAcc.getPublicKey(),requiredParams,new HashMap<>());
        if(accountInfo.getValue()==null || accountInfo.getValue().size()<=0)return;
        String uiAmountString = accountInfo.getValue().get(0).getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmountString();
        BigDecimal amount = new BigDecimal(uiAmountString).multiply(new BigDecimal(SPL_NUM));

        BigDecimal max_sol_cost = new BigDecimal(uiAmountString).multiply(quteSol).multiply(new BigDecimal("0.98"));
        max_sol_cost = max_sol_cost.multiply(new BigDecimal(SOL_NUM));
//         instruction = AssociatedTokenProgram.createIdempotentNew(fundAcc.getPublicKey(),associateUser.getAddress(),fundAcc.getPublicKey(),new PublicKey(mint));
//        transaction.addInstruction(instruction);
//
//        instruction = AssociatedTokenProgram.createIdempotent(fundAcc.getPublicKey(),sellAcc.getAddress(),new PublicKey(pumpAddress),new PublicKey(mint));
//        transaction.addInstruction(instruction);

        PumpToken2022PoolInfoV3 info = PumpDIumProgram.refPumpToken2022V3(mint,"So11111111111111111111111111111111111111112",
                associateUser.getAddress().toBase58(),fundAcc.getPublicKeyBase58(),"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

        instruction = PumpDIumProgram.makePumpTotalToken2022V3(info,1,amount.longValue(),max_sol_cost.longValue());
        transaction.addInstruction(instruction);


        String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
        if(simulatedTransaction.getValue().getErr()!= null)return;

        SplNonceAccountInfo nonceAccountInfo = rpcApi.getAccountNonceInfo(new PublicKey(nonceAddress));
        if(nonceAccountInfo==null || nonceAccountInfo.getValue()==null)return;

        blockHash =  nonceAccountInfo.getValue().getData().getParsed().getInfo().getBlockhash();
        String tx = jitRpcApi.sendTransaction(transaction,fundAcc,blockHash);

        System.out.println("sell:"+tx);


        //Gv2Ji4Nqe5EeLG35gG6dRu77M2kZUp2htd4DA8r9dfJg


    }


    /**
     * 关闭 token 2022
     * @throws RpcException
     */
    @Test
    public void closeToken2022() throws RpcException {
        //closeAccountToken2022
        String url = Cluster.HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        Account fundAcc = new Account(Base58
                .decode(Env.require("SOLANA_PUMP_ALTERNATE_PRIVATE_KEY_BASE58")));
        Transaction transaction  = new Transaction();
        List<JSONObject> list =  rpcApi.getTokenAccountsByOwnerNewPro(fundAcc.getPublicKey(),new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"));
        int k = 0;

        for (int i = 0; i < list.size(); i++) {
            int isWith = 0;
            if(list.get(i).getJSONObject("account").getJSONObject("data").getJSONObject("parsed").getJSONObject("info").getJSONObject("tokenAmount")
                    .getBigDecimal("uiAmount").compareTo(BigDecimal.ZERO)>0)continue;
            for (JSONObject object : list.get(i).getJSONObject("account").getJSONObject("data").getJSONObject("parsed").getJSONObject("info").getList("extensions", JSONObject.class)) {
                if(!object.getString("extension").equals("transferFeeAmount"))continue;
                if(object.getJSONObject("state").getIntValue("withheldAmount")<=0)continue;
                isWith = 1;
                //break;
                Transaction transactionV1  = new Transaction();
                transactionV1.addInstruction(TokenProgram.harvestWithheldTokensToMint(new PublicKey(list.get(i).getJSONObject("account").getJSONObject("data").getJSONObject("parsed").getJSONObject("info").getString("mint")),List.of(new PublicKey(list.get(i).getString("pubkey")))));
                String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
                SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transactionV1,fundAcc,blockHash);
                if(simulatedTransaction.getValue().getErr()!=null) {
                    System.out.println(simulatedTransaction.getValue().getErr().toString());
                    continue;
                }
                String tx = rpcApi.sendTransaction(transactionV1,fundAcc,blockHash);
                System.out.println(tx);
            }

            k = k+1;
            if(isWith==1)continue;
            //harvestWithheldTokensToMint
            transaction.addInstruction(TokenProgram.closeAccountToken2022(new PublicKey(list.get(i).getString("pubkey")),fundAcc.getPublicKey(),fundAcc.getPublicKey()));
            if(k==5){
                String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
                SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
                if(simulatedTransaction.getValue().getErr()!=null) {
                    System.out.println(simulatedTransaction.getValue().getErr().toString());
                    continue;
                }
                String tx = rpcApi.sendTransaction(transaction,fundAcc,blockHash);
                System.out.println(tx);
                k = 0;
                transaction  = new Transaction();
                continue;
            }

            if(list.size() != (i+1))continue;
            String blockHash =  rpcApi.getLatestBlockhash().getValue().getBlockhash();
            SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,fundAcc,blockHash);
            if(simulatedTransaction.getValue().getErr()!=null) {
                System.out.println(simulatedTransaction.getValue().getErr().toString());
                continue;
            }
            String tx = rpcApi.sendTransaction(transaction,fundAcc,blockHash);
            System.out.println(tx);
            k = 0;
        }
    }
}
