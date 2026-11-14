package org.p2p.solanaj.programs;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.utils.StringUtils;
import org.p2p.solanaj.utils.ray.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * pump 交易指令
 */
public class PumpDIumProgram {

    private static final PublicKey FEE_Program = new PublicKey("pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ");
    public static final PublicKey Program_PUMP = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
    /**
     * 交易指令 SPL 代币
     */
    private static final PublicKey TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    private static final PublicKey PAYER = new PublicKey("11111111111111111111111111111111");


    public static final PublicKey GLOBE_CONFIG = new PublicKey("ADyA8hdefvWN2dbGGWFotbzWxrAvLW83WG6QCVXvJKqw");

    private static final PublicKey EventAuthority = new PublicKey("GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR");

    public  static final PublicKey ProgramId = new PublicKey("pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA");

    private static final PublicKey NESTED_MINT = new PublicKey("So11111111111111111111111111111111111111112"); // Wrapped SOL mint


    // 未打满的 权限 地址
    private static final PublicKey EventAuISCom = new PublicKey("Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1");

    public static final PublicKey GLOBE_CONFIG_IsCOM = new PublicKey("4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf");


    // Pump.fun buy_v3
    // HEX: 07051dc4f5176550
    private static final byte[] BUY_V3 = {
            7, 5, 29, (byte) 196,
            (byte) 245, 23, 101, 80
    };

    // Pump.fun sell_v3
    // HEX: 1c92de7726c469d5
    private static final byte[] SELL_V3 = {
            28, (byte) 146, (byte) 222, 119,
            38, (byte) 196, 105, (byte) 213
    };


    public static final String[] PUMP_ADDRESS = new String[]{
            "7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ",
            "7hTckgnGnLQR6sdH7YkqFTAA7VwTfYFaZ6EhEsU3saCX",
            "9rPYyANsfQZw3DnDmKE3YCQF5E8oD89UXoHn9JFEhJUz",
            "AVmoTthdrX6tKt4nDjco2D775W2YK3sDhxPcMmzUAmTY",
            "CebN5WGQ4jvEPvsVU4EoHEpgzq1VV7AbicfhtW4xC9iM",
            "FWsW1xNtWscwNmKv6wVsU1iTzRN6wmmk3MjxRP5tT7hz",
            "G5UZAVbAf46s7cKWoyKu8kYTip9DGTpbLZ2qa9Aq69dP"
    };
    //[
    //  "7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ",
    //  "7hTckgnGnLQR6sdH7YkqFTAA7VwTfYFaZ6EhEsU3saCX",
    //  "9rPYyANsfQZw3DnDmKE3YCQF5E8oD89UXoHn9JFEhJUz",
    //  "AVmoTthdrX6tKt4nDjco2D775W2YK3sDhxPcMmzUAmTY",
    //  "CebN5WGQ4jvEPvsVU4EoHEpgzq1VV7AbicfhtW4xC9iM",
    //  "FWsW1xNtWscwNmKv6wVsU1iTzRN6wmmk3MjxRP5tT7hz",
    //  "G5UZAVbAf46s7cKWoyKu8kYTip9DGTpbLZ2qa9Aq69dP"
    //]
    public static final String[] BUY_FEC_ADDRESS = new String[]{
            "5YxQFdt3Tr9zJLvkFccqXVUwhdTWJQc1fFg2YPbxvxeD",
            "9M4giFFMxmFGXtc3feFzRai56WbBqehoSeRE5GK7gf7",
          "GXPFM2caqTtQYC2cJ5yJRi9VDkpsYZXzYdwYpGnLmtDL",
          "3BpXnfJaUTiwXnJNe7Ej1rcbzqTTQUvLShZaWazebsVR",
          "5cjcW9wExnJJiqgLjq7DEG75Pm6JBgE1hNv4B2vHXUW6",
          "EHAAiTxcdDwQ3U4bU6YcMsQGaekdzLS3B5SmYo46kJtL",
          "5eHhjP8JaYkz83CWwvGU2uMUXefd3AazWGx4gpcuEEYD",
          "A7hAgCzFw14fejgCp387JUJRMNyz4j89JKnhtKU8piqW"
    };

    


    /**
     * PUMP 对应的 地址
     */
    public static final Map<String ,String > PUMP_ADDRESS_MAP = new HashMap<>();

    static {
        PUMP_ADDRESS_MAP.put("CebN5WGQ4jvEPvsVU4EoHEpgzq1VV7AbicfhtW4xC9iM","CGEWR6pxwgQvYKeX4pZDqpZtWYPvyTjiAsw86SNzJtGy");

        PUMP_ADDRESS_MAP.put("7VtfL8fvgNfhz17qKRMjzQEXgbdpnHHHQRh54R9jP2RJ","7GFUN3bWzJMKMRZ34JLsvcqdssDbXnp589SiE33KVwcC");
        PUMP_ADDRESS_MAP.put("7hTckgnGnLQR6sdH7YkqFTAA7VwTfYFaZ6EhEsU3saCX","X5QPJcpph4mBAJDzc4hRziFftSbcygV59kRb2Fu6Je1");
        PUMP_ADDRESS_MAP.put("9rPYyANsfQZw3DnDmKE3YCQF5E8oD89UXoHn9JFEhJUz","Bvtgim23rfocUzxVX9j9QFxTbBnH8JZxnaGLCEkXvjKS");
        PUMP_ADDRESS_MAP.put("AVmoTthdrX6tKt4nDjco2D775W2YK3sDhxPcMmzUAmTY","FGptqdxjahafaCzpZ1T6EDtCzYMv7Dyn5MgBLyB3VUFW");

        PUMP_ADDRESS_MAP.put("FWsW1xNtWscwNmKv6wVsU1iTzRN6wmmk3MjxRP5tT7hz","7xQYoUjUJF1Kg6WVczoTAkaNhn5syQYcbvjmFrhjWpx");
        PUMP_ADDRESS_MAP.put("G5UZAVbAf46s7cKWoyKu8kYTip9DGTpbLZ2qa9Aq69dP","BWXT6RUhit9FfJQM3pBmqeFLPYmuxgmyhMGC5sGr8RbA");
        PUMP_ADDRESS_MAP.put("JCRGumoE9Qi5BBgULTgdgTLjSgkCMSbF62ZZfGs84JeU","DWpvfqzGWuVy9jVSKSShdM2733nrEsnnhsUStYbkj6Nn");

    }


    /**
     * pump  buy 交易指令
     * @param apiPoolInfoV5 池子对象
     * @param tokenAccountBase 买入钱包地址
     * @param tokenAccountQuote 卖出钱包地址
     * @param amountIn 买入金额
     * @param minAmountOut 最小输出金额
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedInInstruction(PumpApiPoolInfo apiPoolInfoV5, PublicKey tokenAccountQuote, PublicKey tokenAccountBase,
                                                                    Long amountIn, Long minAmountOut, PublicKey owner){
        byte[] bytes = new byte[]{102, 6, 61, 18, 1, -38, -21, -22};
        return makeSwapFixedTotalInstruction(apiPoolInfoV5, tokenAccountQuote, tokenAccountBase, amountIn, minAmountOut, owner,bytes);
    }

    /**
     * 未打满的 pump buy 指令
     * @param poolInfo
     * @param tokenAccountBase
     * @param amountIn
     * @param minAmountOut
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedInInstruction(PumpApiIsComPoolInfo poolInfo,
                                                                    PublicKey tokenAccountBase,
                                                                    Long amountIn, Long minAmountOut, PublicKey owner){
        byte[] bytes = new byte[]{102, 6, 61, 18, 1, -38, -21, -22};
        return makeSwapIsComTotalInstruction(poolInfo,tokenAccountBase,amountIn,minAmountOut,owner,bytes);
    }





    /**
     * pump  通用 交易指令
     * @param apiPoolInfoV5 池子对象
     * @param tokenAccountBase 买入钱包地址
     * @param tokenAccountQuote 卖出钱包地址
     * @param amountIn 买入金额
     * @param minAmountOut 最小输出金额
     * @param owner
     * @return
     */
    private static TransactionInstruction makeSwapFixedTotalInstruction(PumpApiPoolInfo apiPoolInfoV5, PublicKey tokenAccountQuote, PublicKey tokenAccountBase,
                                                                    Long amountIn, Long minAmountOut, PublicKey owner,byte[] bytes){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        buffer.put(bytes);
        buffer.putLong(minAmountOut);

        // quote amount
        buffer.putLong(amountIn);
        byte[] data = buffer.array();
        // 构建keys列表
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getPoolAddress()),false,false));

        keys.add(new AccountMeta(owner,true,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getGlobalConfig()),false,true));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getBaseMint()),false,false));
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getQuoteMint()),false,false));

        // BASE MINT 地址
        keys.add(new AccountMeta(tokenAccountBase,false,true));

        // QUOTE mint 地址
        keys.add(new AccountMeta(tokenAccountQuote,false,true));

        // Pool Base Token Account
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getPoolBaseTokenAccount()),false,true));
        // Pool Quote Token Account
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getPoolQuoteTokenAccount()),false,true));


        //  Protocol Fee Recipient
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getProtocolFeeRecipient()),false,true));
        // Protocol Fee Recipient Token Account
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getProtocolFeeRecipientTokenAccount()),false,true));

        // Base Token Program
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getBaseTokenProgram()),false,false));
        // Quote Token Program
        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getQuoteTokenProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getSystemProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getAssociatedTokenProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getEventAuthority()),false,false));

        keys.add(new AccountMeta(new PublicKey(apiPoolInfoV5.getProgramId()),false,false));
        return new TransactionInstruction(
                new PublicKey(apiPoolInfoV5.getProgramId()),
                keys,
                data
        );
    }


    /**
     * pump 未打满的 数据
     * @param poolInfo
     * @param tokenAccountBase 包装后的
     * @param amountIn
     * @param minAmountOut
     * @param owner
     * @param bytes
     * @return
     */
    public static TransactionInstruction makeSwapIsComTotalInstruction(PumpApiIsComPoolInfo poolInfo,
                                                                        PublicKey tokenAccountBase,
                                                                       Long amountIn, Long minAmountOut, PublicKey owner,byte[] bytes){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        buffer.put(bytes);
        buffer.putLong(minAmountOut);

        // quote amount
        buffer.putLong(amountIn);
        byte[] data = buffer.array();
        // 构建keys列表
        List<AccountMeta> keys = new ArrayList<>();

        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobalConfig()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getProtocolFeeRecipient()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getMint()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedBondingCurve()),false,true));

        // 包装后的 地址
        keys.add(new AccountMeta(tokenAccountBase,false,true));

        keys.add(new AccountMeta(owner,true,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getTokenProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getRentProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgramId()),false,false));
        return new TransactionInstruction(
                new PublicKey(poolInfo.getProgramId()),
                keys,
                data
        );
    }

    /**
     * 未打满的  pump sell 卖出指令
     * @param poolInfo
     * @param tokenAccountBase
     * @param amountIn
     * @param minAmountOut
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedOutInstruction(PumpApiIsComPoolInfo poolInfo,
                                                                     PublicKey tokenAccountBase,
                                                                     Long amountIn, Long minAmountOut, PublicKey owner){
        byte[] bytes = new byte[]{51, -26, -123, -92, 1, 127, -125, -83};
        return makeSwapIsComTotalInstruction(poolInfo,tokenAccountBase,amountIn,minAmountOut,owner,bytes);
    }

    /**
     * pump  sell 交易指令
     * @param apiPoolInfoV5 池子对象
     * @param tokenAccountBase 买入钱包地址
     * @param tokenAccountQuote 卖出钱包地址
     * @param quoteAmountIn 买入金额
     * @param baseMinAmountOut 最小输出金额
     * @param owner
     * @return
     */
    public static TransactionInstruction makeSwapFixedOutInstruction(PumpApiPoolInfo apiPoolInfoV5, PublicKey tokenAccountQuote, PublicKey tokenAccountBase,
                                                                     Long quoteAmountIn, Long baseMinAmountOut, PublicKey owner){
        byte[] bytes = new byte[]{51, -26, -123, -92, 1, 127, -125, -83};
        return makeSwapFixedTotalInstruction(apiPoolInfoV5, tokenAccountQuote, tokenAccountBase, quoteAmountIn, baseMinAmountOut, owner,bytes);
    }









    /**
     *
     * @param rpcApi
     * @param lpAddress
     * @return
     */
    public static  PumpApiPoolInfo refPumpInfo(RpcApi rpcApi,String lpAddress) throws RpcException {
        Random random = new Random();
        String pumpAddress = PUMP_ADDRESS[random.nextInt(PUMP_ADDRESS.length-1)];
        PublicKey protocolFeeRecipientTokenAccount = rpcApi.getTokenAccountsByOwnerNew(new PublicKey(pumpAddress),NESTED_MINT);
        if(protocolFeeRecipientTokenAccount==null) return null;
        AccountInfo accountInfo = rpcApi.getAccountInfo(new PublicKey(lpAddress));
        if(accountInfo==null || accountInfo.getValue()==null) return null;
        return refPumpInfo(accountInfo.getDecodedData(),lpAddress,pumpAddress,protocolFeeRecipientTokenAccount);
    }


    public static PumpApiPoolInfo refPumpInfo(byte[] data,String lpAddress,String pumpAddress,PublicKey protocolFeeRecipientTokenAccount){
        PumpApiPoolInfo poolInfo = new PumpApiPoolInfo();
        LiquidityPumpPoolInfo liquidityPumpPoolInfo = LiquidityPumpPoolInfo.decode(data);
        poolInfo.setProgramId(ProgramId.toBase58());
        poolInfo.setPoolAddress(lpAddress);
        poolInfo.setTokenAddress(liquidityPumpPoolInfo.getBaseMint().toBase58());
        poolInfo.setGlobalConfig(GLOBE_CONFIG.toBase58());
        poolInfo.setBaseMint(liquidityPumpPoolInfo.getBaseMint().toBase58());
        poolInfo.setQuoteMint(liquidityPumpPoolInfo.getQuoteMint().toBase58());
        poolInfo.setPoolBaseTokenAccount(liquidityPumpPoolInfo.getPoolBaseTokenAccount().toBase58());
        poolInfo.setPoolQuoteTokenAccount(liquidityPumpPoolInfo.getPoolQuoteTokenAccount().toBase58());
        poolInfo.setProtocolFeeRecipient(pumpAddress);
        poolInfo.setProtocolFeeRecipientTokenAccount(protocolFeeRecipientTokenAccount.toBase58());
        return getPumpApiPoolInfo(poolInfo);
    }

    @NotNull
    private static PumpApiPoolInfo getPumpApiPoolInfo(PumpApiPoolInfo poolInfo) {
        poolInfo.setBaseTokenProgram(TOKEN_PROGRAM_ID.toBase58());
        poolInfo.setQuoteTokenProgram(TOKEN_PROGRAM_ID.toBase58());
        poolInfo.setSystemProgram(PAYER.toBase58());
        poolInfo.setAssociatedTokenProgram(AssociatedTokenProgram.PROGRAM_ID.toBase58());
        poolInfo.setEventAuthority(EventAuthority.toBase58());
        poolInfo.setProgram(ProgramId.toBase58());
        return poolInfo;
    }

    public static PumpApiPoolInfo refPumpInfo(byte[] data,String lpAddress){
        Random random = new Random();
        String pumpAddress = PUMP_ADDRESS[random.nextInt(PUMP_ADDRESS.length-1)];
        String protocolFeeRecipientTokenAccount = PUMP_ADDRESS_MAP.get(pumpAddress);
        PumpApiPoolInfo poolInfo = new PumpApiPoolInfo();
        LiquidityPumpPoolInfo liquidityPumpPoolInfo = LiquidityPumpPoolInfo.decode(data);
        poolInfo.setProgramId(ProgramId.toBase58());
        poolInfo.setPoolAddress(lpAddress);
        poolInfo.setTokenAddress(liquidityPumpPoolInfo.getBaseMint().toBase58());
        poolInfo.setGlobalConfig(GLOBE_CONFIG.toBase58());
        poolInfo.setBaseMint(liquidityPumpPoolInfo.getBaseMint().toBase58());
        poolInfo.setQuoteMint(liquidityPumpPoolInfo.getQuoteMint().toBase58());
        poolInfo.setPoolBaseTokenAccount(liquidityPumpPoolInfo.getPoolBaseTokenAccount().toBase58());
        poolInfo.setPoolQuoteTokenAccount(liquidityPumpPoolInfo.getPoolQuoteTokenAccount().toBase58());
        poolInfo.setProtocolFeeRecipient(pumpAddress);
        poolInfo.setProtocolFeeRecipientTokenAccount(protocolFeeRecipientTokenAccount);
        return getPumpApiPoolInfo(poolInfo);
    }

    /**
     *  获取未打满的
     * @param mint
     * @param bondingCurve
     * @param associatedBondingCurve
     * @param isType  0 是buy  1是 sell
     * @return
     */
    public static PumpApiIsComPoolInfo refIsComPumpInfo(String mint,String bondingCurve,String associatedBondingCurve,int isType){
        PumpApiIsComPoolInfo poolInfo = new PumpApiIsComPoolInfo();
        Random random = new Random();
        String pumpAddress = PUMP_ADDRESS[random.nextInt(PUMP_ADDRESS.length-1)];
        poolInfo.setProgramId("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        poolInfo.setGlobalConfig(GLOBE_CONFIG_IsCOM.toBase58());
        poolInfo.setProtocolFeeRecipient(pumpAddress);
        poolInfo.setMint(mint);
        poolInfo.setBondingCurve(bondingCurve);
        poolInfo.setAssociatedBondingCurve(associatedBondingCurve);
        poolInfo.setSystemProgram(PAYER.toBase58());
        poolInfo.setTokenProgram(TOKEN_PROGRAM_ID.toBase58());
        poolInfo.setRentProgram(TokenProgram.SYSVAR_RENT_PUBKEY.toBase58());
        if(isType==1){
            poolInfo.setRentProgram(TOKEN_PROGRAM_ID.toBase58());
            poolInfo.setTokenProgram(AssociatedTokenProgram.PROGRAM_ID.toBase58());
        }
        poolInfo.setEventAuthority(EventAuISCom.toBase58());
        return poolInfo;
    }


    /**
     * 创建 buy token2022
     * @param mint token地址
     * @param associateUser  创建当前地址 ACC地址
     * @param userAddress 签名地址
     * @param creator  创建者地址
     * @return
     */
    public static PumpApiIsComToken2022PoolInfo refPumpToken2022(String mint,
                                                                 String associateUser,
                                                                 String userAddress,
                                                                 String creator,
                                                                 String sellAddress){
        PumpApiIsComToken2022PoolInfo info = new PumpApiIsComToken2022PoolInfo();
        Random random = new Random();
        String feeRecipient = PUMP_ADDRESS[random.nextInt(PUMP_ADDRESS.length-1)];

        String buyRecAddress = BUY_FEC_ADDRESS[random.nextInt(BUY_FEC_ADDRESS.length-1)];
        PublicKey.ProgramDerivedAddress creatorVault = PublicKey.findProgramAddress(
                Arrays.asList("creator-vault".getBytes(),
                        new PublicKey(creator).toByteArray()),
                Program_PUMP
        );
        PublicKey.ProgramDerivedAddress globalVolumeAccumulator = PublicKey.findProgramAddress(
                Collections.singletonList("global_volume_accumulator".getBytes()),
                Program_PUMP
        );
        PublicKey.ProgramDerivedAddress userVolumeAccumulator = PublicKey.findProgramAddress(
                Arrays.asList("user_volume_accumulator".getBytes(),
                        new PublicKey(userAddress).toByteArray()),
                Program_PUMP
        );
        PublicKey.ProgramDerivedAddress feeConfig = PublicKey.findProgramAddress(
                Arrays.asList("fee_config".getBytes(),
                        Program_PUMP.toByteArray()),
                FEE_Program
        );

        PublicKey.ProgramDerivedAddress lpAddress = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey(mint).toByteArray()),
                Program_PUMP
        );

        PublicKey.ProgramDerivedAddress lpAddressVault = PublicKey.findProgramAddress(
                Arrays.asList(lpAddress.getAddress().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),

                AssociatedTokenProgram.PROGRAM_ID
        );

        PublicKey.ProgramDerivedAddress remainingAccount = PublicKey.findProgramAddress(
                Arrays.asList(
                       "bonding-curve-v2".getBytes(),
                        new PublicKey(mint).toByteArray()
                ),

                PumpDIumProgram.Program_PUMP
        );

        if(StringUtils.isEmpty(sellAddress))
            sellAddress = userVolumeAccumulator.getAddress().toBase58();


        info.setGlobal(GLOBE_CONFIG_IsCOM.toBase58());
        info.setFeeRecipient(feeRecipient);
        info.setMint(mint);
        info.setBondingCurve(lpAddress.getAddress().toBase58());
        info.setAssociatedBondingCurve(lpAddressVault.getAddress().toBase58());
        info.setAssociateUser(associateUser);
        info.setUser(userAddress);
        info.setSystemProgram(SystemProgram.PROGRAM_ID.toBase58());
        info.setCreatorVault(creatorVault.getAddress().toBase58());
        info.setTokenProgram(TokenProgram.TOKEN_2022.toBase58());
        info.setEventAuthority(EventAuISCom.toBase58());
        info.setProgram(Program_PUMP.toBase58());
        info.setGlobalVolumeAccumulator(globalVolumeAccumulator.getAddress().toBase58());
        info.setUserVolumeAccumulator(userVolumeAccumulator.getAddress().toBase58());
        info.setFeeConfig(feeConfig.getAddress().toBase58());
        info.setFeeProgram(FEE_Program.toBase58());
        info.setSellAccount(sellAddress);
        info.setRemainingAccount(remainingAccount.getAddress().toBase58());
        info.setBuyFeeAccount(buyRecAddress);
        return info;
    }

    /**
     * 创建 buy token2022
     * @param mint token地址
     * @param quoteMint quote 地址
     * @param associateUser  创建当前地址 ACC地址
     * @param userAddress 签名地址
     * @return
     */
    public static PumpToken2022PoolInfoV3 refPumpToken2022V3(String mint,
                                                                 String quoteMint,
                                                                 String associateUser,
                                                                 String userAddress,
                                                             String baseTokenProgram,
                                                             String quoteTokenProgram){
        PumpToken2022PoolInfoV3 info = new PumpToken2022PoolInfoV3();
        Random random = new Random();


        String buyRecAddress = BUY_FEC_ADDRESS[random.nextInt(BUY_FEC_ADDRESS.length-1)];
        PublicKey.ProgramDerivedAddress userVolumeAccumulator = PublicKey.findProgramAddress(
                Arrays.asList("user_volume_accumulator".getBytes(),
                        new PublicKey(userAddress).toByteArray()),
                Program_PUMP
        );
        PublicKey.ProgramDerivedAddress feeConfig = PublicKey.findProgramAddress(
                Arrays.asList("fee_config".getBytes(),
                        Program_PUMP.toByteArray()),
                FEE_Program
        );

        PublicKey.ProgramDerivedAddress lpAddress = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey(mint).toByteArray()),
                Program_PUMP
        );



        PublicKey.ProgramDerivedAddress associatedBaseBondingCurve = PublicKey.findProgramAddress(
                Arrays.asList(lpAddress.getAddress().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey(mint).toByteArray()
                ),

                AssociatedTokenProgram.PROGRAM_ID
        );

        var associatedQuoteBondingCurve = PublicKey.findProgramAddress(
                List.of(lpAddress.getAddress().toByteArray(),
                        TokenProgram.PROGRAM_ID.toByteArray(),
                        new PublicKey(quoteMint).toByteArray()),
                AssociatedTokenProgram.PROGRAM_ID
        );


        var associatedQuoteUser = PublicKey.findProgramAddress(
                List.of(new PublicKey(userAddress).toByteArray(),
                        TokenProgram.PROGRAM_ID.toByteArray(),
                        new PublicKey(quoteMint).toByteArray()),
                AssociatedTokenProgram.PROGRAM_ID
        );



        info.setGlobal(GLOBE_CONFIG_IsCOM.toBase58());
        info.setBaseMint(mint);
        info.setQuoteMint(quoteMint);
        info.setBaseTokenProgram(baseTokenProgram);
        info.setQuoteTokenProgram(quoteTokenProgram);

        info.setBondingCurve(lpAddress.getAddress().toBase58());
        info.setAssociatedBaseBondingCurve(associatedBaseBondingCurve.getAddress().toBase58());
        info.setAssociatedQuoteBondingCurve(associatedQuoteBondingCurve.getAddress().toBase58());
        info.setUser(userAddress);
        info.setAssociatedBaseUser(associateUser);
        info.setAssociatedQuoteUser(associatedQuoteUser.getAddress().toBase58());
        info.setUserVolumeAccumulator(userVolumeAccumulator.getAddress().toBase58());
        info.setFeeConfig(feeConfig.getAddress().toBase58());

        info.setBuybackFeeRecipient(buyRecAddress);
        info.setSystemProgram(SystemProgram.PROGRAM_ID.toBase58());

        info.setEventAuthority(EventAuISCom.toBase58());

        info.setProgram(Program_PUMP.toBase58());
        return info;
    }

    /**
     *
     * @param info
     * @param type 0 购买 1 卖出
     * @param amount SPL 买的数量
     * @param max_sol_cost SOL cost
     * @return
     */
    public static TransactionInstruction makePumpTotalToken2022(PumpApiIsComToken2022PoolInfo info,int type,
                                                                Long amount,Long max_sol_cost){
        byte[] bytes = new byte[]{102, 6, 61, 18, 1, -38, -21, -22};

        if(type==0){
            return makePumpBuyToken2022(info,bytes,amount,max_sol_cost);
        }
        bytes = new byte[]{51, -26, -123, -92, 1, 127, -125, -83};
        return makePumpSellToken2022(info,bytes,amount,max_sol_cost);
    }

    /**
     * pump V3 购买
     * @param info
     * @param type 0 购买 1 卖出
     * @param amount SPL 买的数量
     * @param max_sol_cost SOL cost
     * @return
     */
    public static TransactionInstruction makePumpTotalToken2022V3(PumpToken2022PoolInfoV3 info,int type,
                                                                Long amount,Long max_sol_cost){

        if(type==0) return makePumpV3Token2022(info,BUY_V3,amount,max_sol_cost);

        return makePumpV3Token2022(info,SELL_V3,amount,max_sol_cost);
    }
    /**
     * pump TOKEN2022 购买
     * @param
     * @param poolInfo
     * @return
     */
    private static   TransactionInstruction makePumpBuyToken2022(PumpApiIsComToken2022PoolInfo poolInfo,byte[] bytes,
                                                                Long amount,Long max_sol_cost){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        buffer.put(bytes);
        buffer.putLong(amount);

        // quote amount
        buffer.putLong(max_sol_cost);
        byte[] data = buffer.array();

        //byte[] command,
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobal()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeRecipient()),false,true));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getMint()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBondingCurve()),false,true));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociateUser()),false,true));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getUser()),true,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getTokenProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getCreatorVault()),false,true));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgram()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobalVolumeAccumulator()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserVolumeAccumulator()),false,true));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeConfig()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeProgram()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getRemainingAccount()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBuyFeeAccount()),false,true));
        return new TransactionInstruction(
                Program_PUMP,
                keys,
                data
        );
    }




    private static   TransactionInstruction makePumpSellToken2022(PumpApiIsComToken2022PoolInfo poolInfo,byte[] bytes,
                                                                 Long amount,Long max_sol_cost){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        buffer.put(bytes);
        buffer.putLong(amount);

        // quote amount
        buffer.putLong(max_sol_cost);
        byte[] data = buffer.array();

        //byte[] command,
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobal()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeRecipient()),false,true));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getMint()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociateUser()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getUser()),true,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getCreatorVault()),false,true));
        //
        keys.add(new AccountMeta(new PublicKey(poolInfo.getTokenProgram()),false,false));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgram()),false,false));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeConfig()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeProgram()),false,false));

        if(poolInfo.getSellAccount()!=null)
            keys.add(new AccountMeta(new PublicKey(poolInfo.getSellAccount()),false,true));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getRemainingAccount()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBuyFeeAccount()),false,true));
        return new TransactionInstruction(
                Program_PUMP,
                keys,
                data
        );
    }

    /**
     * pump v3 通用买卖
     * @param
     * @param poolInfo
     * @return
     */
    private static   TransactionInstruction makePumpV3Token2022(PumpToken2022PoolInfoV3 poolInfo,byte[] bytes,
                                                                 Long amount,Long max_sol_cost){
        // 指令数据布局： u8(instruction) + u64(amountIn) + u64(minAmountOut)

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        buffer.put(bytes);
        buffer.putLong(amount);

        // quote amount
        buffer.putLong(max_sol_cost);
        byte[] data = buffer.array();

        //byte[] command,
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobal()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseMint()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteMint()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseTokenProgram()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteTokenProgram()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedBaseBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedQuoteBondingCurve()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getUser()),true,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedBaseUser()),false,true));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getAssociatedQuoteUser()),false,true));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserVolumeAccumulator()),false,true));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getFeeConfig()),false,false));

        keys.add(new AccountMeta(new PublicKey(poolInfo.getBuybackFeeRecipient()),false,true));


        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()),false,false));
        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgram()),false,false));
        return new TransactionInstruction(
                Program_PUMP,
                keys,
                data
        );
    }








    public static void main(String[] args) throws RpcException {
        //66063d1201daebea750f0428a3030000403fe21100000000
        byte[] bytes = HexUtil.decodeHex("66063d1201daebeac31a8bb90800000000f9150000000000");
        System.out.println(Arrays.toString(bytes));

        //33e685a4017f83ad52dad6782e0f00009f81581b00000000
        bytes = HexUtil.decodeHex("33e685a4017f83ad52dad6782e0f00009f81581b00000000");
        //33e685a4017f83ad52dad6782e0f00009f81581b00000000
        System.out.println(Arrays.toString(bytes));

        ByteBuffer buffer = ByteBuffer.allocate( 8+ 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // base amount
        bytes = new byte[]{51, -26, -123, -92, 1, 127, -125, -83};
        buffer.put(bytes);
        buffer.putLong(16692270258770L);

        // quote amount
        buffer.putLong(458785183L);
        byte[] data = buffer.array();
        System.out.println(HexUtil.encodeHexStr(data));
        PublicKey.ProgramDerivedAddress creatorVault = PublicKey.findProgramAddress(
                Arrays.asList("sharing-config".getBytes(),
                        new PublicKey("ErSVUDot8wVTTLBauxn63or3WyzyBV7vrSXbZ8knpump").toByteArray()),
                FEE_Program
        );
        //7LjoCYh1gR5iGfzmiHSNJtYzYC5u8Pm9fKtffvUfFsYB
        System.out.println(creatorVault.getAddress().toBase58());

        //[115, 104, 97, 114, 105, 110, 103, 45, 99, 111, 110, 102, 105, 103]
        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(new PublicKey("Gk43iXMVPFXC3KgCq6TLvchzcujnzUpgY6NXsFLFSVc9").toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey("3qnwpNAPhzDW8nvmwohSDHwVUs46YnRbwH3DuYMopump").toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);
        PublicKey.ProgramDerivedAddress userVolumeAccumulator = PublicKey.findProgramAddress(Arrays.asList("user_volume_accumulator".getBytes(), associateUser.getAddress().toByteArray()), Program_PUMP);
        System.out.println(associateUser.getAddress().toBase58());
        System.out.println(userVolumeAccumulator.getAddress().toBase58());
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://64.130.57.42:8899";
        BigDecimal buySol = new BigDecimal("0.001");
        RpcApi rpc = new RpcApi(new RpcClient(url));
        Random random = new Random();
        String pumpAddress = PUMP_ADDRESS[random.nextInt(PUMP_ADDRESS.length-1)];
        PublicKey protocolFeeRecipientTokenAccount = rpc.getTokenAccountsByOwnerNew(new PublicKey(pumpAddress),NESTED_MINT);
        System.out.println(protocolFeeRecipientTokenAccount);
    }






}
