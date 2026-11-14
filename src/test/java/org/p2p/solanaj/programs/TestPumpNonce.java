package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;


import lombok.extern.slf4j.Slf4j;
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
import org.p2p.solanaj.rpc.types.SplNonceAccountInfo;
import org.p2p.solanaj.utils.TweetNaclFast;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
public class TestPumpNonce {

    private static final Long SOL_NUM = 1000000000L;

    /**
     * 获取Nonce 持续 区块地址
     */
    @Test
    public void sendNonceAddress() throws RpcException {

        for (int i = 0; i < 1; i++) {
            String url = Cluster.HELIUS.getEndpoint();
            RpcApi rpcApi = new RpcApi(new RpcClient(url));

            List<Account> accList = new ArrayList<>();
            Account fundAcc = new Account(Base58
                    .decode(Env.require("SOLANA_PUMP_V3_PRIVATE_KEY_BASE58")));
            Account newAcc = new Account();
            accList.add(fundAcc);
            accList.add(newAcc);
            Transaction transaction  = new Transaction();

            //2039280
            long lamports = new BigDecimal("1317264").longValue();
            PublicKey newPublic = newAcc.getPublicKey();

            TransactionInstruction instruction = SystemProgram.createNonceAccount(fundAcc.getPublicKey(), newPublic
                    , lamports);
            transaction.addInstruction(instruction);


            instruction = SystemProgram.initializeNonce(newPublic,fundAcc.getPublicKey());
            transaction.addInstruction(instruction);

            String recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
            SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction,accList,recentBlockHash);
            if(simulatedTransaction.getValue().getErr()!= null){

                System.out.println(simulatedTransaction.toString());

                return;
            }
            recentBlockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
            String tx = rpcApi.sendTransaction(transaction,accList,recentBlockHash);
            System.out.println(tx);
            System.out.println(transaction.signatures.get(0));

            System.out.println(111);
        }

    }

    //BSxjNPCsRpxXmf4f7Ccpa2ZVcXbLiUKJWTpk1tWvXVE8
    @Test
    public void sendNonceTransfer() throws RpcException {
        String url = Cluster.DEV_HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        String devAddress = "4LLZurE6H6UthKBXG3Lu8RpMZ5VkhWUD9UyCPQTNyut2";
        PublicKey noncePublic = new PublicKey("BSxjNPCsRpxXmf4f7Ccpa2ZVcXbLiUKJWTpk1tWvXVE8");
        Account fundAcc = Env.account();
        SplNonceAccountInfo nonceAccountInfo = rpcApi.getAccountNonceInfo(new PublicKey(noncePublic.toBase58()));
        String blockhash = nonceAccountInfo.getValue().getData().getParsed().getInfo().getBlockhash();

        Transaction transaction  = new Transaction();

        TransactionInstruction instruction = SystemProgram.nonceAdvance(noncePublic,fundAcc.getPublicKey());

        transaction.addInstruction(instruction);

        instruction = SystemProgram.transfer(fundAcc.getPublicKey(),new PublicKey(devAddress),1000000L);
        transaction.addInstruction(instruction);

        String tx = rpcApi.sendTransaction(transaction,fundAcc,blockhash);
        System.out.println(tx);
    }
}
