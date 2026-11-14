package org.p2p.solanaj.programs;


import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.utils.StringUtils;
import org.p2p.solanaj.utils.ray.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ray 交易指令
 */
public class RayDIumProgram {


    /**
     * 交易指令 SPL 代币
     */
    private static final PublicKey TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    private static final PublicKey ModelDataPubKey = new PublicKey("CDSr3ssLcRB6XYPJwAfFt18MZvEZp4LjHcvzBVZ45duo");

    private static final PublicKey PAYER = new PublicKey("11111111111111111111111111111111");

    /**
     * ray  buy 交易指令
     * @param apiPoolInfoV5 池子对象
     * @param tokenAccountIn 买入钱包地址
     * @param tokenAccountOut 卖出钱包地址
     * @param amountIn 买入金额
     * @param minAmountOut 最小输出金额
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedInInstruction(RayApiPoolInfoV5 apiPoolInfoV5, PublicKey tokenAccountIn, PublicKey tokenAccountOut,
                                                                    Long amountIn, Long minAmountOut, PublicKey owner){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)
        ByteBuffer buffer = ByteBuffer.allocate(1 + 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte)9); // instruction = 9
        buffer.putLong(amountIn);
        buffer.putLong(minAmountOut);
        byte[] data = buffer.array();
        //System.out.println(HexUtil.encodeHex(data));
        // 构建keys列表
        List<AccountMeta> keys = new ArrayList<>();
        // system / token program
        keys.add(new AccountMeta(TOKEN_PROGRAM_ID, false,false));
        // amm
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getId()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getAuthority()), false,false));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getOpenOrders()), false,true));
        if (apiPoolInfoV5.getVersion() == 4) {
            keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getTargetOrders()), false,true));
        }
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getBaseVault()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getQuoteVault()), false,true));
        if (apiPoolInfoV5.getVersion() == 5) {
            keys.add(new AccountMeta(ModelDataPubKey, false,true));
        }
        // serum
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketProgramId()), false,false));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketId()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketBids()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketAsks()), false,true));

        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketEventQueue()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketBaseVault()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketQuoteVault()), false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getMarketAuthority()), false,false));

        // user
        keys.add(new AccountMeta(tokenAccountIn, false,true));
        keys.add(new AccountMeta(tokenAccountOut, false,true));
        keys.add(new AccountMeta(owner, true,false));
        // 构建指令

        return new TransactionInstruction(
                new PublicKey(apiPoolInfoV5.getProgramId()),
                keys,
                data
        );
    }



    /**
     * RAY out 卖出 指令
     * @param apiPoolInfoV5 池子对象
     * @param tokenAccountIn 买入钱包地址
     * @param tokenAccountOut 卖出钱包地址
     * @param amountIn 买入金额
     * @param minAmountOut 最小输出金额
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedOutInstruction(RayApiPoolInfoV5 apiPoolInfoV5, PublicKey tokenAccountIn, PublicKey tokenAccountOut,
                                                                     Long amountIn, Long minAmountOut, PublicKey owner){
        return makeSwapFixedInInstruction(apiPoolInfoV5, tokenAccountIn, tokenAccountOut, amountIn, minAmountOut, owner);
    }


    /**
     * Cpmm 池子 卖出
     * @param info
     * @param amountIn
     * @param minAmountOut
     * @return
     */
    public static TransactionInstruction makeSwapCPmmBaseOutInInstruction(RayCPmmPoolInfo info,Long amountIn, Long minAmountOut){
        String vaultA= info.getVaultA();
        String vaultB= info.getVaultB();
        String mintA = info.getMintA();
        String mintB = info.getMintB();
        info.setMintB(mintA);
        info.setMintA(mintB);
        info.setVaultA(vaultB);
        info.setVaultB(vaultA);
        return makeSwapCPmmBaseInInInstruction(info, amountIn, minAmountOut);
    }
    /**
     * CPmm 池子交易
     * @param info
     * @param amountIn 买入 SOL 数量
     * @param minAmountOut 买入 数量 需要计算
     * @return
     */
    public static TransactionInstruction makeSwapCPmmBaseInInInstruction(RayCPmmPoolInfo info,Long amountIn, Long minAmountOut){
        byte[] swapBaseInput = new byte[]{(byte) 143, (byte) 190, (byte) 90, (byte) 218, (byte) 196, (byte) 30, (byte) 51, (byte) 222};
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(swapBaseInput);
        buffer.putLong(amountIn);
        buffer.putLong(minAmountOut);
        byte[] data = buffer.array();
        List<AccountMeta> keys = new ArrayList<>();

//        const keys: Array<AccountMeta> = [
//        { pubkey: payer, isSigner: true, isWritable: false },
//        { pubkey: authority, isSigner: false, isWritable: false },
//        { pubkey: configId, isSigner: false, isWritable: false },
//        { pubkey: poolId, isSigner: false, isWritable: true },
//        { pubkey: userInputAccount, isSigner: false, isWritable: true },
//        { pubkey: userOutputAccount, isSigner: false, isWritable: true },
//        { pubkey: inputVault, isSigner: false, isWritable: true },
//        { pubkey: outputVault, isSigner: false, isWritable: true },
//        { pubkey: inputTokenProgram, isSigner: false, isWritable: false },
//        { pubkey: outputTokenProgram, isSigner: false, isWritable: false },
//        { pubkey: inputMint, isSigner: false, isWritable: false },
//        { pubkey: outputMint, isSigner: false, isWritable: false },
//        { pubkey: observationId, isSigner: false, isWritable: true },];

        keys.add(new AccountMeta(new PublicKey(info.getPayer()),true,false));
        keys.add(new AccountMeta(new PublicKey(info.getAuthority()),false,false));

        keys.add(new AccountMeta(new PublicKey(info.getConfigId()),false,false));
        keys.add(new AccountMeta(new PublicKey(info.getPoolId()),false,true));

        keys.add(new AccountMeta(new PublicKey(info.getTokenAccountIn()),false,true));
        keys.add(new AccountMeta(new PublicKey(info.getTokenAccountOut()),false,true));

        keys.add(new AccountMeta(new PublicKey(info.getVaultA()),false,true));
        keys.add(new AccountMeta(new PublicKey(info.getVaultB()),false,true));

        keys.add(new AccountMeta(new PublicKey(info.getMintProgramA()),false,false));
        keys.add(new AccountMeta(new PublicKey(info.getMintProgramB()),false,false));

        keys.add(new AccountMeta(new PublicKey(info.getMintA()),false,false));
        keys.add(new AccountMeta(new PublicKey(info.getMintB()),false,false));

        keys.add(new AccountMeta(new PublicKey(info.getObservationId()),false,true));
        return new TransactionInstruction(
                new PublicKey(info.getProgramId()),
                keys,
                data
        );

    }
    /**
     *
     * @param rpcApi
     * @param id
     * @return
     * @throws RpcException
     */
    public static RayApiPoolInfoV5 refRay(RpcApi rpcApi, String id,byte[] accountData,String owner) throws RpcException {
        //String id = "GknhA8jCwiL29wDM6JedmhQ6iuQcgURbjvpzx3smHPyv";

        PublicKey poolId = new PublicKey(id);
        LiquidityStateV4 info = null;
        if(accountData==null || accountData.length<=0){
            AccountInfo accountInfo = rpcApi.getAccountInfo(poolId);
            if(accountInfo==null || accountInfo.getValue()==null) return null;
            info = LiquidityStateV4.decode(Base64.decode(accountInfo.getValue().getData().get(0)));
            owner = accountInfo.getValue().getOwner();
        }else{
            info = LiquidityStateV4.decode(Base64.decode(accountData));
        }
        if(StringUtils.isEmpty(owner)) return null;
        AccountInfo marketAccount = rpcApi.getAccountInfo(info.getMarketId());
        if(marketAccount==null || marketAccount.getValue()==null) return null;

        MarketStateV3 marketInfo = MarketStateV3.decode(Base64.decode(marketAccount.getValue().getData().get(0)));

        AccountInfo lpMintAccount = rpcApi.getAccountInfo(info.getLpMint());
        if(lpMintAccount==null || lpMintAccount.getValue()==null) return null;
        SplMintLayout lpMintInfo = SplMintLayout.decode(Base64.decode(lpMintAccount.getValue().getData().get(0)));
        RayApiPoolInfoV5 rayApiPoolInfoV5 = new RayApiPoolInfoV5();
        rayApiPoolInfoV5.setId(id);
        rayApiPoolInfoV5.setBaseMint(info.getBaseMint().toBase58());
        rayApiPoolInfoV5.setQuoteMint(info.getQuoteMint().toBase58());
        rayApiPoolInfoV5.setLpMint(info.getLpMint().toBase58());
        rayApiPoolInfoV5.setBaseDecimals(info.getBaseDecimal());
        rayApiPoolInfoV5.setQuoteDecimals(info.getQuoteDecimal());
        rayApiPoolInfoV5.setLpDecimals((long) lpMintInfo.getDecimals());
        rayApiPoolInfoV5.setVersion(4);
        rayApiPoolInfoV5.setProgramId(owner);

        rayApiPoolInfoV5.setAuthority(PublicKey.getAssociatedAuthority(new PublicKey(owner)).getAddress().toBase58());
        rayApiPoolInfoV5.setOpenOrders(info.getOpenOrders().toBase58());
        rayApiPoolInfoV5.setTargetOrders(info.getTargetOrders().toBase58());
        rayApiPoolInfoV5.setBaseVault(info.getBaseVault().toBase58());
        rayApiPoolInfoV5.setQuoteVault(info.getQuoteVault().toBase58());
        rayApiPoolInfoV5.setWithdrawQueue(info.getWithdrawQueue().toBase58());
        rayApiPoolInfoV5.setLpVault(info.getLpVault().toBase58());
        rayApiPoolInfoV5.setMarketVersion(3);
        rayApiPoolInfoV5.setMarketProgramId(info.getMarketProgramId().toBase58());
        rayApiPoolInfoV5.setMarketId(info.getMarketId().toBase58());
        rayApiPoolInfoV5.setMarketAuthority(PublicKey.getAssociatedAuthority(info.getMarketProgramId(),info.getMarketId()).getAddress().toBase58());
        rayApiPoolInfoV5.setMarketBaseVault(marketInfo.getBaseVault().toBase58());
        rayApiPoolInfoV5.setMarketQuoteVault(marketInfo.getQuoteVault().toBase58());
        rayApiPoolInfoV5.setMarketBids(marketInfo.getBids().toBase58());
        rayApiPoolInfoV5.setMarketAsks(marketInfo.getAsks().toBase58());
        rayApiPoolInfoV5.setMarketEventQueue(marketInfo.getEventQueue().toBase58());

        rayApiPoolInfoV5.setLookupTableAccount(PAYER.toBase58());

        //RayDIumProgram.makeSwapFixedInInstruction(rayApiPoolInfoV5);
        ////System.out.println(rayApiPoolInfoV5);
        return rayApiPoolInfoV5;
    }


    /**
     * 获取 CPmm 信息
     * @param rpcApi
     * @param poolId  CPMM 池子
     * @return
     */
    public static RayCPmmPoolInfo refCPmmRay(RpcApi rpcApi, String poolId) throws RpcException {
        RayCPmmPoolInfo poolInfo = new RayCPmmPoolInfo();
        poolInfo.setAuthority("GpMZbSM2GgvTKHJirzeGfMFoaZ8UR2X7F4v8vHTvxFbL");
        poolInfo.setPoolId(poolId);
        AccountInfo accountInfo = rpcApi.getAccountInfo(new PublicKey(poolId));
        if(accountInfo==null || accountInfo.getValue()==null) return null;
        LiquidityCPmmPoolInfo cPmmPoolInfo = LiquidityCPmmPoolInfo.decode(accountInfo.getDecodedData());
        poolInfo.setConfigId(cPmmPoolInfo.getConfigId().toBase58());
        poolInfo.setVaultA(cPmmPoolInfo.getVaultA().toBase58());
        poolInfo.setVaultB(cPmmPoolInfo.getVaultB().toBase58());
        poolInfo.setMintProgramA(cPmmPoolInfo.getMintProgramA().toBase58());
        poolInfo.setMintProgramB(cPmmPoolInfo.getMintProgramB().toBase58());
        poolInfo.setMintA(cPmmPoolInfo.getMintA().toBase58());
        poolInfo.setMintB(cPmmPoolInfo.getMintB().toBase58());
        poolInfo.setObservationId(cPmmPoolInfo.getObservationId().toBase58());
        poolInfo.setProgramId(accountInfo.getValue().getOwner());
        poolInfo.setBump(cPmmPoolInfo.getBump());
        poolInfo.setStatus((int) cPmmPoolInfo.getStatus());
        poolInfo.setLpDecimals((int) cPmmPoolInfo.getLpDecimals());
        poolInfo.setMintDecimalA((int) cPmmPoolInfo.getMintDecimalA());
        poolInfo.setMintDecimalB((int) cPmmPoolInfo.getMintDecimalB());
        poolInfo.setLpAmount(cPmmPoolInfo.getLpAmount());
        poolInfo.setProtocolFeesMintA(cPmmPoolInfo.getProtocolFeesMintA());
        poolInfo.setProtocolFeesMintB(cPmmPoolInfo.getProtocolFeesMintB());
        poolInfo.setFundFeesMintA(cPmmPoolInfo.getFundFeesMintA());
        poolInfo.setFundFeesMintB(cPmmPoolInfo.getFundFeesMintB());
        poolInfo.setOpenTime(cPmmPoolInfo.getOpenTime());
        poolInfo.setPoolCreator(cPmmPoolInfo.getPoolCreator().toBase58());
        poolInfo.setMintLp(cPmmPoolInfo.getMintLp().toBase58());
        return poolInfo;
    }







}
