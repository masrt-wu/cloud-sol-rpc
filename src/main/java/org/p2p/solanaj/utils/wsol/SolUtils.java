package org.p2p.solanaj.utils.wsol;


import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.rpc.RpcApi;

import java.util.ArrayList;
import java.util.List;

public class SolUtils {

    private static final PublicKey NESTED_MINT = new PublicKey("So11111111111111111111111111111111111111112"); // Wrapped SOL mint

    /**
     *  SOL To WSol
     * @param fundingAccount 转入钱包地址
     * @return
     */
    public static List<TransactionInstruction>  SolToWSolUtils(PublicKey fundingAccount, RpcApi rpcApi, long lamports){
        List<TransactionInstruction> list = new ArrayList<>();
        try {
            PublicKey newPublicKey = rpcApi.getTokenAccountsByOwnerNew(fundingAccount, NESTED_MINT);
            if(newPublicKey == null){
                TransactionInstruction instruction = AssociatedTokenProgram.createIdempotent(fundingAccount, fundingAccount, NESTED_MINT);
                newPublicKey = instruction.getKeys().get(1).getPublicKey();
                //newPublicKey  = new Account().getPublicKey();
                //TransactionInstruction instruction = SystemProgram.createAccount(fundingAccount,newPublicKey,2039280L,165L,SystemProgram.PROGRAM_ID);
                //SystemProgram.createAccountWithSeedInstruction(fundingAccount.toBase58(),newPublicKey.toBase58(),fundingAccount.toBase58())
                list.add(instruction);
            }
            list.add(SystemProgram.transfer(fundingAccount, newPublicKey, lamports));
//            TransactionInstruction instruction = TokenProgram.closeAccount(newPublicKey,fundingAccount,fundingAccount);
//            list.add(instruction);
            return list;
        }catch (Exception e){
            throw new RuntimeException("error: {}",e);
        }
    }

    /**
     *  wSol 转sol
     * @param fundAcc 钱包地址
     * @param rpcApi
     * @return
     */
    public static List<TransactionInstruction> WSolToSolUtils(Account fundAcc, RpcApi rpcApi){
        List<TransactionInstruction> list = new ArrayList<>();
        try {
            PublicKey newPublicKey = rpcApi.getTokenAccountsByOwnerNew(fundAcc.getPublicKey(), NESTED_MINT);
            if(newPublicKey==null) throw new RuntimeException("数据为空");
            TransactionInstruction instruction = TokenProgram.closeAccount(newPublicKey,fundAcc.getPublicKey(),fundAcc.getPublicKey());
            list.add(instruction);
            return list;
        }catch (Exception e){
            throw new RuntimeException("error: {}",e);
        }
    }
}
