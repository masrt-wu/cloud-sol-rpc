package org.p2p.solanaj.utils.ray;

import cn.hutool.core.codec.Base64;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;
import com.alibaba.fastjson2.*;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;


@Data
public class LiquidityStateV4 {
    public long status;
    public long nonce;
    public long maxOrder;
    public long depth;
    public long baseDecimal;
    public long quoteDecimal;
    public long state;
    public long resetFlag;
    public long minSize;
    public long volMaxCutRatio;
    public long amountWaveRatio;
    public long baseLotSize;
    public long quoteLotSize;
    public long minPriceMultiplier;
    public long maxPriceMultiplier;
    public long systemDecimalValue;
    public long minSeparateNumerator;
    public long minSeparateDenominator;
    public long tradeFeeNumerator;
    public long tradeFeeDenominator;
    public long pnlNumerator;
    public long pnlDenominator;
    public long swapFeeNumerator;
    public long swapFeeDenominator;
    public long baseNeedTakePnl;
    public long quoteNeedTakePnl;
    public long quoteTotalPnl;
    public long baseTotalPnl;
    public long poolOpenTime;
    public long punishPcAmount;
    public long punishCoinAmount;
    public long orderbookToInitTime;

    public BigInteger swapBaseInAmount;
    public BigInteger swapQuoteOutAmount;
    public long swapBase2QuoteFee;
    public BigInteger swapQuoteInAmount;
    public BigInteger swapBaseOutAmount;
    public long swapQuote2BaseFee;

    public PublicKey baseVault;
    public PublicKey quoteVault;
    public PublicKey baseMint;
    public PublicKey quoteMint;
    public PublicKey lpMint;
    public PublicKey openOrders;
    public PublicKey marketId;
    public PublicKey marketProgramId;
    public PublicKey targetOrders;
    public PublicKey withdrawQueue;
    public PublicKey lpVault;
    public PublicKey owner;
    public long lpReserve;
    public long[] padding = new long[3];

    public static LiquidityStateV4 decode(byte[] accountData) {
        LiquidityStateV4 state = new LiquidityStateV4();

        ByteBuffer bb = ByteBuffer.wrap(accountData);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // 读取 u64 (8字节)
        state.status = bb.getLong();
        state.nonce = bb.getLong();
        state.maxOrder = bb.getLong();
        state.depth = bb.getLong();
        state.baseDecimal = bb.getLong();
        state.quoteDecimal = bb.getLong();
        state.state = bb.getLong();
        state.resetFlag = bb.getLong();
        state.minSize = bb.getLong();
        state.volMaxCutRatio = bb.getLong();
        state.amountWaveRatio = bb.getLong();
        state.baseLotSize = bb.getLong();
        state.quoteLotSize = bb.getLong();
        state.minPriceMultiplier = bb.getLong();
        state.maxPriceMultiplier = bb.getLong();
        state.systemDecimalValue = bb.getLong();
        state.minSeparateNumerator = bb.getLong();
        state.minSeparateDenominator = bb.getLong();
        state.tradeFeeNumerator = bb.getLong();
        state.tradeFeeDenominator = bb.getLong();
        state.pnlNumerator = bb.getLong();
        state.pnlDenominator = bb.getLong();
        state.swapFeeNumerator = bb.getLong();
        state.swapFeeDenominator = bb.getLong();
        state.baseNeedTakePnl = bb.getLong();
        state.quoteNeedTakePnl = bb.getLong();
        state.quoteTotalPnl = bb.getLong();
        state.baseTotalPnl = bb.getLong();
        state.poolOpenTime = bb.getLong();
        state.punishPcAmount = bb.getLong();
        state.punishCoinAmount = bb.getLong();
        state.orderbookToInitTime = bb.getLong();

        // u128需要读取16字节
        state.swapBaseInAmount = readU128(bb);
        state.swapQuoteOutAmount = readU128(bb);
        state.swapBase2QuoteFee = bb.getLong();
        state.swapQuoteInAmount = readU128(bb);
        state.swapBaseOutAmount = readU128(bb);
        state.swapQuote2BaseFee = bb.getLong();

        // publicKey 32字节
        state.baseVault = readPublicKey(bb);
        state.quoteVault = readPublicKey(bb);
        state.baseMint = readPublicKey(bb);
        state.quoteMint = readPublicKey(bb);
        state.lpMint = readPublicKey(bb);
        state.openOrders = readPublicKey(bb);
        state.marketId = readPublicKey(bb);
        state.marketProgramId = readPublicKey(bb);
        state.targetOrders = readPublicKey(bb);
        state.withdrawQueue = readPublicKey(bb);
        state.lpVault = readPublicKey(bb);
        state.owner = readPublicKey(bb);

        state.lpReserve = bb.getLong();
        for (int i = 0; i < 3; i++) {
            state.padding[i] = bb.getLong();
        }

        return state;
    }

    public static BigInteger readU128(ByteBuffer bb) {
        // 小端序读取16字节
        byte[] u128Bytes = new byte[16];
        bb.get(u128Bytes);
        // Java的BigInteger是大端，需要翻转字节数组
        reverse(u128Bytes);
        return new BigInteger(1, u128Bytes);
    }

    public static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes);
        return new PublicKey(pubKeyBytes);
    }

    public static void reverse(byte[] arr) {
        int i = 0;
        int j = arr.length - 1;
        while (i < j) {
            byte temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
            i++;
            j--;
        }
    }

    public static void main(String[] args) throws RpcException {
        String base64 = "BgAAAAAAAAD+AAAAAAAAAAcAAAAAAAAAAwAAAAAAAAAFAAAAAAAAAAkAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAACAxqR+jQMA9AEAAAAAAAAAAAAAAAAAAADh9QUAAAAAAQAAAAAAAAABAAAAAAAAAADKmjsAAAAAABCl1OgAAAAFAAAAAAAAABAnAAAAAAAAGQAAAAAAAAAQJwAAAAAAAAwAAAAAAAAAZAAAAAAAAAAZAAAAAAAAABAnAAAAAAAAAAAAAAAAAAAAAAAAAAAAAH6MTSJHAAAAEHpzAPfZAADA6aZjAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGA1m/RhHF4BAAAAAAAAAAA4aZR27HkAAAAAAAAAAAAA+lGjMk4AAACQt7MYL3oAAAAAAAAAAAAACLNkrZ1JXQEAAAAAAAAAACUUOyoS4AAAXdMeM7lW1LN3xsPP7eFzL1E4P6ooJCSDc2AtXOfjFcvoh+I7I+CBf8v3Kx1LhqEbTCKkQJAZlVte6pg3l7ECMrwHxW5grT0/F3OC6sZUj7of0yz9kMoCs+fPoYX9znOYBpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAH0W5XSF0RhyPkosMVIlVZncTUEzj/ITYrELe1Pw+633kg+XO4LG1gMZCLSKvfZra9kro+Dz397PwgHiLF7B+Ga+pMz4tCBX83nNsI82pFLdqYfHWyO97lBPXH7Y19MdIwNB1GoKC2mEwX+KZw3uZjlhHHbETUDcxD4vhBFpgr27kq5TNINpIq64WjT2cXDd2DSDAAABM+1vXMp9Ah8nRZ2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOW2K2XLO72m9WiI5m/ujmTcVWAZnA+IsR/ic70FnoqhkEu1+rAPBAAAAAAAAAAAAA0EAAAAAAAAAAAAAAAAAAA=";

        RpcApi  rpcApi = new RpcApi(new RpcClient(Cluster.HELIUS.getEndpoint()));

        LiquidityStateV4 info = LiquidityStateV4.decode(Base64.decode(base64));
        AccountInfo marketAccount = rpcApi.getAccountInfo(info.getMarketId());
        if(marketAccount==null || marketAccount.getValue()==null) return ;

        MarketStateV3 marketInfo = MarketStateV3.decode(Base64.decode(marketAccount.getValue().getData().get(0)));

        AccountInfo lpMintAccount = rpcApi.getAccountInfo(info.getLpMint());
        if(lpMintAccount==null || lpMintAccount.getValue()==null) return ;
        SplMintLayout lpMintInfo = SplMintLayout.decode(Base64.decode(lpMintAccount.getValue().getData().get(0)));
        RayApiPoolInfoV5 rayApiPoolInfoV5 = new RayApiPoolInfoV5();
        rayApiPoolInfoV5.setId("4RhSfoLjLyARaj1PWdSVi2ECTPHTcPtzMPhAC8MXvgj3");
        rayApiPoolInfoV5.setBaseMint(info.getBaseMint().toBase58());
        rayApiPoolInfoV5.setQuoteMint(info.getQuoteMint().toBase58());
        rayApiPoolInfoV5.setLpMint(info.getLpMint().toBase58());
        rayApiPoolInfoV5.setBaseDecimals(info.getBaseDecimal());
        rayApiPoolInfoV5.setQuoteDecimals(info.getQuoteDecimal());
        rayApiPoolInfoV5.setLpDecimals((long) lpMintInfo.getDecimals());
        rayApiPoolInfoV5.setVersion(4);
        //rayApiPoolInfoV5.setProgramId(marketAccount.getValue().getOwner());

        //rayApiPoolInfoV5.setAuthority(PublicKey.getAssociatedAuthority(new PublicKey(accountInfo.getValue().getOwner())).getAddress().toBase58());
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

        //rayApiPoolInfoV5.setLookupTableAccount(PAYER.toBase58());

        //RayDIumProgram.makeSwapFixedInInstruction(rayApiPoolInfoV5);
        //System.out.println(rayApiPoolInfoV5);
        System.out.println(JSONObject.toJSONString(rayApiPoolInfoV5));

        var AMM_V4 = new PublicKey("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
        var baseVault = PublicKey.findProgramAddress(
                List.of(
                new PublicKey("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8").toByteArray(),
                new PublicKey("8BnEgHoWFysVcuFFX7QztDmzuH8r5ZFvyP3sYwn1XTh6").toByteArray(),
                "coin_vault_associated_seed".getBytes())
                ,AMM_V4 );
        System.out.println(baseVault.getAddress().toBase58());

        var quoteVault = PublicKey.findProgramAddress(
                List.of(
                        new PublicKey("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8").toByteArray(),
                        new PublicKey("8BnEgHoWFysVcuFFX7QztDmzuH8r5ZFvyP3sYwn1XTh6").toByteArray(),
                        "pc_vault_associated_seed".getBytes())
                ,AMM_V4 );
        System.out.println(quoteVault.getAddress().toBase58());

    }

}
