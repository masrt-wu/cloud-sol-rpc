package org.p2p.solanaj.programs;

import cn.hutool.core.codec.Base58;
import cn.hutool.core.codec.Base64;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ray.RayLaunchpadToken2022PoolInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RayLaunchLabUtils {

    public static final PublicKey PROGRAM_LAUNCHLAB = new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");

    public static final PublicKey TOKEN_PROGRAM = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    public static final PublicKey TOKEN_2022_PROGRAM = new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");

    /**
     * Raydium LaunchLab Token2022 BuyExactIn 地址生成
     *
     * @param mint           base mint，新发 Token2022 地址
     * @param quoteMint      quote mint，例如 DezX... / SOL / 其他 quote
     * @param userAddress    当前买入钱包地址
     * @param creator        Token 创建者地址，注意不是当前 buyer
     * @param platformConfig LaunchLab platform config
     * @param curveType      curve type，本交易为 0
     * @param configIndex    config index，本交易为 0
     */
    public static RayLaunchpadToken2022PoolInfo refLaunchLabToken2022(String mint, String quoteMint, String userAddress, String creator, String platformConfig, int curveType, int configIndex) {

        RayLaunchpadToken2022PoolInfo info = new RayLaunchpadToken2022PoolInfo();

        PublicKey mintKey = new PublicKey(mint);
        PublicKey quoteMintKey = new PublicKey(quoteMint);
        PublicKey userKey = new PublicKey(userAddress);
        PublicKey creatorKey = new PublicKey(creator);
        PublicKey platformConfigKey = new PublicKey(platformConfig);

        /*
         * authority
         *
         * seeds:
         * ["vault_auth_seed"]
         */
        PublicKey.ProgramDerivedAddress authority = PublicKey.findProgramAddress(Collections.singletonList("vault_auth_seed".getBytes(StandardCharsets.UTF_8)), PROGRAM_LAUNCHLAB);

        /*
         * global config
         *
         * seeds:
         * [
         *   "global_config",
         *   quoteMint,
         *   curveType u8,
         *   configIndex u16 BE
         * ]
         */
        PublicKey.ProgramDerivedAddress globalConfig = PublicKey.findProgramAddress(Arrays.asList("global_config".getBytes(StandardCharsets.UTF_8), quoteMintKey.toByteArray(), new byte[]{(byte) curveType}, u16BE(configIndex)), PROGRAM_LAUNCHLAB);

        /*
         * pool state
         *
         * seeds:
         * [
         *   "pool",
         *   baseMint,
         *   quoteMint
         * ]
         */
        PublicKey.ProgramDerivedAddress pool = PublicKey.findProgramAddress(Arrays.asList("pool".getBytes(StandardCharsets.UTF_8), mintKey.toByteArray(), quoteMintKey.toByteArray()), PROGRAM_LAUNCHLAB);

        /*
         * base vault
         *
         * seeds:
         * [
         *   "pool_vault",
         *   pool,
         *   baseMint
         * ]
         */
        PublicKey.ProgramDerivedAddress baseVault = PublicKey.findProgramAddress(Arrays.asList("pool_vault".getBytes(StandardCharsets.UTF_8), pool.getAddress().toByteArray(), mintKey.toByteArray()), PROGRAM_LAUNCHLAB);

        /*
         * quote vault
         *
         * seeds:
         * [
         *   "pool_vault",
         *   pool,
         *   quoteMint
         * ]
         */
        PublicKey.ProgramDerivedAddress quoteVault = PublicKey.findProgramAddress(Arrays.asList("pool_vault".getBytes(StandardCharsets.UTF_8), pool.getAddress().toByteArray(), quoteMintKey.toByteArray()), PROGRAM_LAUNCHLAB);

        /*
         * User Base ATA
         *
         * Token2022:
         * [
         *   user,
         *   TOKEN_2022_PROGRAM,
         *   baseMint
         * ]
         */
        PublicKey.ProgramDerivedAddress userBaseToken = PublicKey.findProgramAddress(Arrays.asList(userKey.toByteArray(), TOKEN_2022_PROGRAM.toByteArray(), mintKey.toByteArray()), AssociatedTokenProgram.PROGRAM_ID);

        /*
         * User Quote ATA
         *
         * 当前交易 quote 使用普通 SPL Token:
         * [
         *   user,
         *   TOKEN_PROGRAM,
         *   quoteMint
         * ]
         */
        PublicKey.ProgramDerivedAddress userQuoteToken = PublicKey.findProgramAddress(Arrays.asList(userKey.toByteArray(), TOKEN_PROGRAM.toByteArray(), quoteMintKey.toByteArray()), AssociatedTokenProgram.PROGRAM_ID);

        /*
         * platform fee vault
         *
         * seeds:
         * [
         *   platformConfig,
         *   quoteMint
         * ]
         */
        PublicKey.ProgramDerivedAddress platformFeeVault = PublicKey.findProgramAddress(Arrays.asList(platformConfigKey.toByteArray(), quoteMintKey.toByteArray()), PROGRAM_LAUNCHLAB);

        /*
         * creator fee vault
         *
         * seeds:
         * [
         *   creator,
         *   quoteMint
         * ]
         */
        PublicKey.ProgramDerivedAddress creatorFeeVault = PublicKey.findProgramAddress(Arrays.asList(creatorKey.toByteArray(), quoteMintKey.toByteArray()), PROGRAM_LAUNCHLAB);

        /*
         * event authority
         *
         * seeds:
         * ["__event_authority"]
         */
        PublicKey.ProgramDerivedAddress eventAuthority = PublicKey.findProgramAddress(Collections.singletonList("__event_authority".getBytes(StandardCharsets.UTF_8)), PROGRAM_LAUNCHLAB);

        info.setPayer(userAddress);

        info.setAuthority(authority.getAddress().toBase58());

        info.setGlobalConfig(globalConfig.getAddress().toBase58());

        info.setPlatformConfig(platformConfig);

        info.setPoolState(pool.getAddress().toBase58());

        info.setUserBaseToken(userBaseToken.getAddress().toBase58());

        info.setUserQuoteToken(userQuoteToken.getAddress().toBase58());

        info.setBaseVault(baseVault.getAddress().toBase58());

        info.setQuoteVault(quoteVault.getAddress().toBase58());

        info.setBaseMint(mint);
        info.setQuoteMint(quoteMint);

        info.setBaseTokenProgram(TOKEN_2022_PROGRAM.toBase58());

        info.setQuoteTokenProgram(TOKEN_PROGRAM.toBase58());

        info.setEventAuthority(eventAuthority.getAddress().toBase58());

        info.setProgram(PROGRAM_LAUNCHLAB.toBase58());

        info.setSystemProgram(SystemProgram.PROGRAM_ID.toBase58());

        info.setPlatformFeeVault(platformFeeVault.getAddress().toBase58());

        info.setCreatorFeeVault(creatorFeeVault.getAddress().toBase58());

        return info;
    }

    /**
     * Raydium SDK 的 u16ToBytes 是 BIG-ENDIAN。
     * <p>
     * 注意：
     * 这里是 PDA seed 的 configIndex，
     * 不是 BuyExactIn instruction 里的 u64。
     */
    private static byte[] u16BE(int value) {

        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("configIndex out of u16 range: " + value);
        }

        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) value).array();
    }

    /**
     * Raydium LaunchLab Token2022
     * <p>
     * type:
     * 0 = BuyExactIn
     * 1 = SellExactIn
     *
     * @param info         pool相关地址
     * @param type         0购买 1卖出
     * @param amount       amountIn，注意是最小单位 raw amount
     * @param minAmountOut 最少收到数量，raw amount
     */
    public static TransactionInstruction makeRayLaunchLabTotalToken2022(RayLaunchpadToken2022PoolInfo info, int type, Long amount, Long minAmountOut) {

        if (type == 0) {

            /*
             * BuyExactIn discriminator
             *
             * HEX:
             * FA EA 0D 7B D5 9C 13 EC
             */
            byte[] bytes = new byte[]{(byte) 0xFA, (byte) 0xEA, (byte) 0x0D, (byte) 0x7B, (byte) 0xD5, (byte) 0x9C, (byte) 0x13, (byte) 0xEC};

            return makeRayLaunchLabBuyToken2022(info, bytes, amount, minAmountOut);
        }

        if (type == 1) {

            /*
             * SellExactIn discriminator
             *
             * HEX:
             * 95 27 DE 9B D3 7C 98 1A
             */
            byte[] bytes = new byte[]{(byte) 0x95, (byte) 0x27, (byte) 0xDE, (byte) 0x9B, (byte) 0xD3, (byte) 0x7C, (byte) 0x98, (byte) 0x1A};

            return makeRayLaunchLabSellToken2022(info, bytes, amount, minAmountOut);
        }

        throw new IllegalArgumentException("type must be 0(buy) or 1(sell)");
    }

    /**
     * Raydium LaunchLab Token2022 买入
     * <p>
     * amount:
     * quote token amountIn
     * <p>
     * minAmountOut:
     * 最少得到的 base token 数量
     */
    private static TransactionInstruction makeRayLaunchLabBuyToken2022(RayLaunchpadToken2022PoolInfo poolInfo, byte[] bytes, Long amount, Long minAmountOut) {

        /*
         * Instruction data:
         *
         * 8 bytes discriminator
         * u64 amountIn
         * u64 minAmountOut
         * u64 shareFeeRate
         *
         * 全部数字 LITTLE_ENDIAN
         */
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8 + 8);

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // discriminator
        buffer.put(bytes);

        // amountIn
        buffer.putLong(amount);

        // minimumAmountOut
        buffer.putLong(minAmountOut);

        // shareFeeRate
        // 当前按照你给的交易写 0
        buffer.putLong(0L);

        byte[] data = buffer.array();


        List<AccountMeta> keys = new ArrayList<>();

        /*
         * #0 payer
         * signer + writable
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPayer()), true, true));

        /*
         * #1 authority
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getAuthority()), false, false));

        /*
         * #2 global config
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobalConfig()), false, false));

        /*
         * #3 platform config
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPlatformConfig()), false, false));

        /*
         * #4 pool state
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPoolState()), false, true));

        /*
         * #5 user base token account
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserBaseToken()), false, true));

        /*
         * #6 user quote token account
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserQuoteToken()), false, true));

        /*
         * #7 base vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseVault()), false, true));

        /*
         * #8 quote vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteVault()), false, true));

        /*
         * #9 base mint
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseMint()), false, false));

        /*
         * #10 quote mint
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteMint()), false, false));

        /*
         * #11 Token2022
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseTokenProgram()), false, false));

        /*
         * #12 普通 SPL Token Program
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteTokenProgram()), false, false));

        /*
         * #13 event authority
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()), false, false));

        /*
         * #14 LaunchLab Program
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgram()), false, false));

        /*
         * #15 System Program
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()), false, false));

        /*
         * #16 Platform Fee Vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPlatformFeeVault()), false, true));

        /*
         * #17 Creator Fee Vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getCreatorFeeVault()), false, true));

        return new TransactionInstruction(new PublicKey(poolInfo.getProgram()), keys, data);
    }


    /**
     * Raydium LaunchLab Token2022 卖出
     *
     * @param amount       要卖出的 Base Token 数量
     *                     raw amount
     * @param minAmountOut 最少收到的 Quote Token
     *                     raw amount
     */
    private static TransactionInstruction makeRayLaunchLabSellToken2022(RayLaunchpadToken2022PoolInfo poolInfo, byte[] bytes, Long amount, Long minAmountOut) {

        /*
         * SellExactIn data:
         *
         * discriminator      8 bytes
         * amountIn           u64
         * minAmountOut       u64
         * shareFeeRate       u64
         *
         * 共 32 bytes
         */
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8 + 8);

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        /*
         * 95 27 DE 9B D3 7C 98 1A
         */
        buffer.put(bytes);

        /*
         * base token amountIn
         */
        buffer.putLong(amount);

        /*
         * 最少接受的 quote token
         */
        buffer.putLong(minAmountOut);

        /*
         * 当前这条交易为 0
         */
        buffer.putLong(0L);

        byte[] data = buffer.array();


        List<AccountMeta> keys = new ArrayList<>();

        /*
         * #0 payer / seller
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPayer()), true, true));

        /*
         * #1 authority
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getAuthority()), false, false));

        /*
         * #2 global config
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getGlobalConfig()), false, false));

        /*
         * #3 platform config
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPlatformConfig()), false, false));

        /*
         * #4 pool state
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPoolState()), false, true));

        /*
         * #5 用户的 Base Token2022 ATA
         *
         * 卖币的时候 token 从这里出去
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserBaseToken()), false, true));

        /*
         * #6 用户 Quote Token ATA
         *
         * 卖出得到 quote token 到这里
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getUserQuoteToken()), false, true));

        /*
         * #7 Base Vault
         *
         * 用户卖出的 base 进入这里
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseVault()), false, true));

        /*
         * #8 Quote Vault
         *
         * quote 从这里支付给用户
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteVault()), false, true));

        /*
         * #9 Base Mint
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseMint()), false, false));

        /*
         * #10 Quote Mint
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteMint()), false, false));

        /*
         * #11 Base Token Program
         *
         * Token2022
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getBaseTokenProgram()), false, false));

        /*
         * #12 Quote Token Program
         *
         * 普通 SPL Token
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getQuoteTokenProgram()), false, false));

        /*
         * #13 Event Authority
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getEventAuthority()), false, false));

        /*
         * #14 LaunchLab Program
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getProgram()), false, false));

        /*
         * #15 System Program
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getSystemProgram()), false, false));

        /*
         * #16 Platform Fee Vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getPlatformFeeVault()), false, true));

        /*
         * #17 Creator Fee Vault
         */
        keys.add(new AccountMeta(new PublicKey(poolInfo.getCreatorFeeVault()), false, true));

        return new TransactionInstruction(new PublicKey(poolInfo.getProgram()), keys, data);
    }

    public static void main(String[] args) {
        byte[] bytes = new byte[]{(byte) 0xFA, (byte) 0xEA, (byte) 0x0D, (byte) 0x7B, (byte) 0xD5, (byte) 0x9C, (byte) 0x13, (byte) 0xEC};

        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8 + 8);

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // discriminator
        buffer.put(bytes);

        // amountIn
        buffer.putLong(3690306800000L);

        // minimumAmountOut
        buffer.putLong(1L);

        // shareFeeRate
        // 当前按照你给的交易写 0
        buffer.putLong(0L);

        byte[] data = buffer.array();

        System.out.println(Base58.encode(data));

        bytes = new byte[]{(byte) 0x95, (byte) 0x27, (byte) 0xDE, (byte) 0x9B, (byte) 0xD3, (byte) 0x7C, (byte) 0x98, (byte) 0x1A};


        ByteBuffer sellBuff = ByteBuffer.allocate(8 + 8 + 8 + 8);

        sellBuff.order(ByteOrder.LITTLE_ENDIAN);

        sellBuff.put(bytes);

        /*
         * base token amountIn
         */
        sellBuff.putLong(981525728019L);

        /*
         * 最少接受的 quote token
         */
        sellBuff.putLong(94368075912L);

        /*
         * 当前这条交易为 0
         */
        sellBuff.putLong(0L);

        data = sellBuff.array();

        System.out.println(Base58.encode(data));

        PublicKey.ProgramDerivedAddress associateUser = PublicKey.findProgramAddress(
                Arrays.asList(new PublicKey("DHtDqpz1xV36tMWErpKcRZf9YAuL6D71YBZ3w4iLWnCv").toByteArray(),
                        TokenProgram.PROGRAM_ID.toByteArray(),
                        new PublicKey("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263").toByteArray()
                ),AssociatedTokenProgram.PROGRAM_ID);

        System.out.println(associateUser.getAddress().toBase58());
    }
}
