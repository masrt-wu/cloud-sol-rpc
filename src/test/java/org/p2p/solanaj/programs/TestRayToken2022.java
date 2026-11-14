package org.p2p.solanaj.programs;

import org.p2p.solanaj.utils.Env;

import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.utils.ray.RayAmmV4RouteKeys;
import org.p2p.solanaj.utils.ray.RayLaunchpadToken2022PoolInfo;
import org.p2p.solanaj.utils.ray.RayRotToken2022PoolInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

@Slf4j
public class TestRayToken2022 {

    private static final String[] JIT_ADDRESS = new String[]{"ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt", "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL", "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5", "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT", "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY", "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh", "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe", "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49"};

    private static final long SOL_NUM = 1000000000L;

    /**
     * Only constructs and prints a one-hop quote-token -> StonkFun/LaunchLab buy.
     * No keypair, signing, simulation, or sendTransaction is used here.
     * VM options: -Drouter.rpcUrl=... -Drouter.payer=... -Drouter.amountInRaw=...
     *             -Drouter.minOutRaw=...
     * RPC_URL / ROUTER_PAYER / ROUTER_AMOUNT_IN_RAW / ROUTER_MIN_OUT_RAW also work.
     */
    @Test
    public void constructStonkQuoteBuyRouterInstruction() throws RpcException {
        String rpcUrl = routeSetting("router.rpcUrl", "RPC_URL");
        String payerValue = routeSetting("router.payer", "ROUTER_PAYER");
        String amountValue = routeSetting("router.amountInRaw", "ROUTER_AMOUNT_IN_RAW");
        String minOutValue = routeSetting("router.minOutRaw", "ROUTER_MIN_OUT_RAW");
        Assumptions.assumeTrue(rpcUrl != null && payerValue != null
                        && amountValue != null && minOutValue != null,
                "Set RPC_URL, ROUTER_PAYER, ROUTER_AMOUNT_IN_RAW and ROUTER_MIN_OUT_RAW to construct the route");

        long amountIn = Long.parseLong(amountValue);
        long minOut = Long.parseLong(minOutValue);
        if (amountIn <= 0 || minOut <= 0) {
            throw new IllegalArgumentException("Raw amountIn and minOut must both be positive");
        }

        PublicKey router = new PublicKey("CmNFUmRJL7YcnVn22oZzwG5Xg5WJqbcHEc6BK5mzDNR8");
        PublicKey launchLab = new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
        PublicKey baseMint = new PublicKey("C3h7pCxgv2PBrtYVT7xiES3i8RMJM7dBBRRZu8JcSe7W");
        PublicKey quoteMint = new PublicKey("Xsv9hRk1z5ystj9MhnA7Lq4vjSsLwzL2nxrwmwtD3re");
        PublicKey payer = new PublicKey(payerValue);
        RpcApi rpc = new RpcApi(new RpcClient(rpcUrl));

        PublicKey pool = routePda(launchLab, "pool", baseMint, quoteMint);
        AccountInfo poolAccount = rpc.getAccountInfo(pool);
        byte[] poolData = routeAccountData(poolAccount, launchLab, "LaunchLab pool");
        Assertions.assertTrue(poolData.length >= 429, "LaunchLab pool data is too short");
        Assertions.assertArrayEquals(new byte[]{(byte) 247, (byte) 237, (byte) 227, (byte) 245,
                (byte) 215, (byte) 195, (byte) 222, 70}, Arrays.copyOf(poolData, 8));

        // PoolState Borsh layout: discriminator(8), scalars(133), then seven pubkeys.
        PublicKey globalConfig = PublicKey.readPubkey(poolData, 141);
        PublicKey platformConfig = PublicKey.readPubkey(poolData, 173);
        Assertions.assertTrue(platformConfig.toBase58().equals("4E876qZTE9FJMrBzgVtBrSrzz2TLivB5Y5QXPjB4gZL7")
                || platformConfig.toBase58().equals("6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt"),
                "Pool is not a recognized StonkFun platform");
        Assertions.assertEquals(baseMint, PublicKey.readPubkey(poolData, 205), "Pool base mint mismatch");
        Assertions.assertEquals(quoteMint, PublicKey.readPubkey(poolData, 237), "Pool quote mint mismatch");
        PublicKey baseVault = PublicKey.readPubkey(poolData, 269);
        PublicKey quoteVault = PublicKey.readPubkey(poolData, 301);
        PublicKey creator = PublicKey.readPubkey(poolData, 333);
        Assertions.assertEquals(routePda(launchLab, "pool_vault", pool, baseMint), baseVault);
        Assertions.assertEquals(routePda(launchLab, "pool_vault", pool, quoteMint), quoteVault);

        PublicKey baseTokenProgram = new PublicKey(routeExistingAccount(rpc, baseMint, "base mint").getOwner());
        PublicKey quoteTokenProgram = new PublicKey(routeExistingAccount(rpc, quoteMint, "quote mint").getOwner());
        PublicKey tokenProgram = TokenProgram.PROGRAM_ID;
        PublicKey token2022Program = TokenProgram.TOKEN_2022;
        Assertions.assertTrue(baseTokenProgram.equals(tokenProgram) || baseTokenProgram.equals(token2022Program));
        Assertions.assertTrue(quoteTokenProgram.equals(tokenProgram) || quoteTokenProgram.equals(token2022Program));

        PublicKey config = routePda(router, "config");
        byte[] configData = routeAccountData(rpc.getAccountInfo(config), router, "router config");
        Assertions.assertTrue(configData.length >= 80, "Router config data is too short");
        Assertions.assertEquals("ROUTCFG1", new String(configData, 0, 8, StandardCharsets.US_ASCII));
        Assertions.assertEquals(0, Byte.toUnsignedInt(configData[75]), "Router is paused");
        PublicKey feeRecipient = PublicKey.readPubkey(configData, 40);
        int feeBps = (configData[72] & 0xff) | ((configData[73] & 0xff) << 8);
        Assertions.assertTrue(feeBps <= 1000, "Invalid router fee bps");
        long fee = (amountIn / 10_000) * feeBps + ((amountIn % 10_000) * feeBps) / 10_000;
        long legAmountIn = amountIn - fee;
        Assertions.assertTrue(legAmountIn > 0, "Router fee consumes the input");

        PublicKey userBaseAta = routeAta(payer, baseMint, baseTokenProgram);
        PublicKey userQuoteAta = routeAta(payer, quoteMint, quoteTokenProgram);
        PublicKey feeDestination = routeAta(feeRecipient, quoteMint, quoteTokenProgram);

        RayLaunchpadToken2022PoolInfo info = new RayLaunchpadToken2022PoolInfo();
        info.setPayer(payer.toBase58());
        info.setAuthority("WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh");
        info.setGlobalConfig(globalConfig.toBase58());
        info.setPlatformConfig(platformConfig.toBase58());
        info.setPoolState(pool.toBase58());
        info.setUserBaseToken(userBaseAta.toBase58());
        info.setUserQuoteToken(userQuoteAta.toBase58());
        info.setBaseVault(baseVault.toBase58());
        info.setQuoteVault(quoteVault.toBase58());
        info.setBaseMint(baseMint.toBase58());
        info.setQuoteMint(quoteMint.toBase58());
        info.setBaseTokenProgram(baseTokenProgram.toBase58());
        info.setQuoteTokenProgram(quoteTokenProgram.toBase58());
        info.setEventAuthority("2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr");
        info.setProgram(launchLab.toBase58());
        info.setSystemProgram(SystemProgram.PROGRAM_ID.toBase58());
        info.setPlatformFeeVault(routePda(launchLab, platformConfig, quoteMint).toBase58());
        info.setCreatorFeeVault(routePda(launchLab, creator, quoteMint).toBase58());

        TransactionInstruction leg = RayLaunchLabUtils.makeRayLaunchLabTotalToken2022(
                info, 0, legAmountIn, minOut);
        Assertions.assertEquals(18, leg.getKeys().size());
        Assertions.assertEquals(32, leg.getData().length);

        TransactionInstruction route = wrapStonkQuoteBuyRoute(router, payer, config, feeDestination,
                userQuoteAta, userBaseAta, quoteTokenProgram, leg, amountIn, minOut);
        Assertions.assertEquals(86, route.getData().length);
        Assertions.assertEquals(25, route.getKeys().size());
        System.out.println("ROUTE ONLY - not signed or submitted; pool=" + pool);
        System.out.println("feeBps=" + feeBps + ", quoteInput=" + amountIn + ", launchLabInput=" + legAmountIn);
        System.out.println("routeDataHex=" + HexFormat.of().formatHex(route.getData()));
        for (int i = 0; i < route.getKeys().size(); i++) {
            AccountMeta key = route.getKeys().get(i);
            System.out.printf("%02d %s signer=%s writable=%s%n", i, key.getPublicKey(), key.isSigner(), key.isWritable());
        }
        // No sendTransaction or simulateTransaction call: minOut and graduation clamp are caller inputs.
    }

    @Test
    public void constructStonkQuoteBuyRouterInstructionAbiOffline() {
        PublicKey router = new PublicKey("CmNFUmRJL7YcnVn22oZzwG5Xg5WJqbcHEc6BK5mzDNR8");
        PublicKey launchLab = new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
        PublicKey placeholder = SystemProgram.PROGRAM_ID;
        List<AccountMeta> legKeys = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) legKeys.add(new AccountMeta(placeholder, false, false));
        legKeys.set(9, new AccountMeta(new PublicKey("C3h7pCxgv2PBrtYVT7xiES3i8RMJM7dBBRRZu8JcSe7W"), false, false));
        legKeys.set(10, new AccountMeta(new PublicKey("Xsv9hRk1z5ystj9MhnA7Lq4vjSsLwzL2nxrwmwtD3re"), false, false));
        byte[] legData = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[]{(byte) 250, (byte) 234, 13, 123, (byte) 213, (byte) 156, 19, (byte) 236})
                .putLong(100_000_000L).putLong(10_000L).putLong(0L).array();
        TransactionInstruction leg = new TransactionInstruction(launchLab, legKeys, legData);

        TransactionInstruction route = wrapStonkQuoteBuyRoute(router, placeholder, placeholder,
                placeholder, placeholder, placeholder, TokenProgram.PROGRAM_ID,
                leg, 100_000_000L, 10_000L);
        ByteBuffer bytes = ByteBuffer.wrap(route.getData()).order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertEquals(router, route.getProgramId());
        Assertions.assertEquals(86, route.getData().length);
        Assertions.assertEquals(25, route.getKeys().size());
        Assertions.assertEquals(2, Byte.toUnsignedInt(bytes.get(0)));
        Assertions.assertEquals(100_000_000L, bytes.getLong(1));
        Assertions.assertEquals(10_000L, bytes.getLong(9));
        Assertions.assertEquals(1, Byte.toUnsignedInt(bytes.get(17)));
        Assertions.assertEquals(1, Byte.toUnsignedInt(bytes.get(18)));
        Assertions.assertArrayEquals(launchLab.toByteArray(), Arrays.copyOfRange(route.getData(), 19, 51));
        Assertions.assertEquals(18, Byte.toUnsignedInt(bytes.get(51)));
        Assertions.assertEquals(32, Short.toUnsignedInt(bytes.getShort(52)));
        Assertions.assertArrayEquals(legData, Arrays.copyOfRange(route.getData(), 54, 86));
    }

    private static TransactionInstruction wrapStonkQuoteBuyRoute(
            PublicKey router, PublicKey payer, PublicKey config, PublicKey feeDestination,
            PublicKey quoteAta, PublicKey baseAta, PublicKey quoteTokenProgram,
            TransactionInstruction leg, long amountIn, long minOut) {
        if (leg.getKeys().size() != 18 || leg.getData().length != 32) {
            throw new IllegalArgumentException("Expected the 18-account, 32-byte LaunchLab BuyExactIn leg");
        }
        List<AccountMeta> keys = new ArrayList<>(25);
        keys.add(new AccountMeta(payer, true, true));
        keys.add(new AccountMeta(config, false, false));
        keys.add(new AccountMeta(feeDestination, false, true));
        keys.add(new AccountMeta(quoteAta, false, true));
        keys.add(new AccountMeta(baseAta, false, true));
        keys.add(new AccountMeta(quoteTokenProgram, false, false));
        keys.addAll(leg.getKeys()); // Do not deduplicate: router slices exactly 18 leg accounts.
        keys.add(new AccountMeta(leg.getProgramId(), false, false)); // CPI program account.

        ByteBuffer data = ByteBuffer.allocate(86).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 2); // Route tag.
        data.putLong(amountIn); // Gross quote input, including router fee.
        data.putLong(minOut); // Final base-token minimum.
        data.put((byte) 1); // Token fee asset (quote token).
        data.put((byte) 1); // One LaunchLab leg.
        data.put(leg.getProgramId().toByteArray());
        data.put((byte) leg.getKeys().size());
        data.putShort((short) leg.getData().length);
        data.put(leg.getData());
        return new TransactionInstruction(router, keys, data.array());
    }

    private static String routeSetting(String property, String env) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(env);
        return value == null || value.isBlank() ? null : value;
    }

    private static AccountInfo.Value routeExistingAccount(RpcApi rpc, PublicKey key, String label) throws RpcException {
        AccountInfo account = rpc.getAccountInfo(key);
        if (account == null || account.getValue() == null) {
            throw new IllegalStateException(label + " does not exist: " + key);
        }
        return account.getValue();
    }

    private static byte[] routeAccountData(AccountInfo account, PublicKey expectedOwner, String label) {
        if (account == null || account.getValue() == null) {
            throw new IllegalStateException(label + " does not exist");
        }
        Assertions.assertEquals(expectedOwner.toBase58(), account.getValue().getOwner(), label + " owner mismatch");
        return account.getDecodedData();
    }

    private static PublicKey routePda(PublicKey program, String seed, PublicKey... keys) {
        List<byte[]> seeds = new ArrayList<>(keys.length + 1);
        seeds.add(seed.getBytes(StandardCharsets.UTF_8));
        for (PublicKey key : keys) seeds.add(key.toByteArray());
        return PublicKey.findProgramAddress(seeds, program).getAddress();
    }

    private static PublicKey routePda(PublicKey program, PublicKey first, PublicKey second) {
        return PublicKey.findProgramAddress(List.of(first.toByteArray(), second.toByteArray()), program).getAddress();
    }

    private static PublicKey routeAta(PublicKey owner, PublicKey mint, PublicKey tokenProgram) {
        return PublicKey.findProgramAddress(
                List.of(owner.toByteArray(), tokenProgram.toByteArray(), mint.toByteArray()),
                AssociatedTokenProgram.PROGRAM_ID).getAddress();
    }


    @Test
    public void sendBuy() throws RpcException {
        Account fundAcc = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://64.130.57.42:8899";
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length - 1)];
        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();


        RayAmmV4RouteKeys ammV4RouteKeys = new RayAmmV4RouteKeys();


        String mint = "5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ";
        String basePro = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
        String quoteMint = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263";
        String quotePro = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
        String plat = "6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt";
        String creator = "DHtDqpz1xV36tMWErpKcRZf9YAuL6D71YBZ3w4iLWnCv";

        ammV4RouteKeys.setProgramId("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
        ammV4RouteKeys.setPoolId("HVNwzt7Pxfu76KHCMQPTLuTCLTm6WnQ1esLv4eizseSv");
        ammV4RouteKeys.setAuthority("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1");
        ammV4RouteKeys.setVaultA("7KFdXKA5WkZBspxwqd9kSrDGTg9WhiX5TptUB3yRwEaE");
        ammV4RouteKeys.setVaultB("GehmCo7EgzkB4xxyviW6xdUhm1Ed2nN98QcfcRWQCfA9");


        String solCreate = PublicKey.findProgramAddress(List.of(fundAcc.getPublicKey().toByteArray(), new PublicKey(quotePro).toByteArray(), new PublicKey("So11111111111111111111111111111111111111112").toByteArray()), AssociatedTokenProgram.PROGRAM_ID).getAddress().toBase58();

        TransactionV1 transaction = new TransactionV1();

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(), new PublicKey(jitAddress), tipPirce);
        transaction.addInstruction(tranIn);

        PublicKey.ProgramDerivedAddress baseUser = PublicKey.findProgramAddress(Arrays.asList(fundAcc.getPublicKey().toByteArray(), new PublicKey(basePro).toByteArray(), new PublicKey(mint).toByteArray()), AssociatedTokenProgram.PROGRAM_ID);

        PublicKey.ProgramDerivedAddress quoteUser = PublicKey.findProgramAddress(Arrays.asList(fundAcc.getPublicKey().toByteArray(), new PublicKey(quotePro).toByteArray(), new PublicKey(quoteMint).toByteArray()), AssociatedTokenProgram.PROGRAM_ID);


        //var ref = RayLaunchLabUtils.refLaunchLabToken2022(mint,quoteMint,fundAcc.getPublicKey().toBase58(),creator,plat,0,0);
        //System.out.println(ref);
        BigDecimal solPrice = new BigDecimal("112.66");
        //价格计算公式  买入数量 /  价格 =  买入数量
        // 买入数量
        BigDecimal quteSol = new BigDecimal("0.000002962");
        // 买入数量
        BigDecimal buySol = new BigDecimal("0.001");
        long amountIn = buySol.movePointRight(9).longValue();
        // 最大可支付数量
        long minAmountOut = buySol.divide(quteSol, 9, RoundingMode.HALF_UP).movePointRight(6).longValue();
        //instruction = RayLaunchLabUtils.makeRayLaunchLabTotalToken2022(ref,0,amount.longValue(),minAmountOut);
        //transaction.addInstruction(instruction);

        List<TransactionInstruction> instructions = RayRuoteLaunchLabUtils.makeBuyInstructions(
                launchLabInfo(fundAcc.getPublicKeyBase58(),
                        baseUser.getAddress().toBase58(),
                        quoteUser.getAddress().toBase58()),
                ammV4RouteKeys,
                new PublicKey(solCreate),
                amountIn, minAmountOut, true);
        //transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(25820));
        //transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(178000));
        TransactionConfigV1 config = new TransactionConfigV1()
                .setComputeUnitLimit(178_000)
                .setPriorityFeeLamports(4_596)
                .setLoadedAccountsDataSizeLimit(10 * 1024 * 1024)
                //.setHeapSize(32768)
                ;

        transaction.setConfig(config);
        for (TransactionInstruction transactionInstruction : instructions) {
            transaction.addInstruction(transactionInstruction);
        }


        String blockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransactionV1(transaction, fundAcc, blockHash);
        if (simulatedTransaction.getValue().getErr() != null) return;

//        String tx = jitRpcApi.sendTransaction(transaction,fundAcc,blockHash);
//        System.out.println(tx);

        System.out.println(1111);
    }

    private static RayRotToken2022PoolInfo launchLabInfo(String par, String userBase, String userQuote) {
        RayRotToken2022PoolInfo info = new RayRotToken2022PoolInfo();
        info.setPayer(par);
        info.setAuthority("WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh");
        info.setGlobalConfig("7G6Gg6rCnaegAhPBdZnRKCkzDWB6K6j4vmhtivSJhVtA");
        info.setPlatformConfig("6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt");
        info.setPoolState("Bv3dcBWqTapef8Cd7RjkK6xzK18KD3eY1Re4W9pQ3XLw");
        info.setUserBaseToken(userBase);
        info.setUserQuoteToken(userQuote);
        info.setBaseVault("8Ty7vwg4BdaCWkxiBwpvbhBEMX8VtwvfHx4qUjFVFFLs");
        info.setQuoteVault("F2i1ksJk9Yv2ZSbGpFs1XrjQizDJUGcN65fZK4k13rtn");
        info.setBaseMint("5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ");
        info.setQuoteMint("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263");
        info.setBaseTokenProgram("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");
        info.setQuoteTokenProgram("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        info.setEventAuthority("2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr");
        info.setProgram("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
        info.setSystemProgram("11111111111111111111111111111111");
        info.setPlatformFeeVault("EoHk1RAMRmodrUWNe8jvPrPy1DVfhfhUC1f7bKCmN5vy");
        info.setCreatorFeeVault("CsDoqYxi5RbSg12FMCf7t9HHvu8PLGxaEiQzft6pWW44");
        return info;
    }

    @Test
    public void sendSell() throws RpcException {
        Account fundAcc = Env.account();
        String url = Cluster.HELIUS.getEndpoint();
        RpcApi rpcApi = new RpcApi(new RpcClient(url));
        RpcApi jitRpcApi = new RpcApi(new RpcClient("https://mainnet.block-engine.jito.wtf/api/v1/transactions"));
        String jitAddress = JIT_ADDRESS[new Random().nextInt(JIT_ADDRESS.length - 1)];
        long tipPirce = new BigDecimal("0.000001").multiply(new BigDecimal(SOL_NUM)).longValue();


        RayAmmV4RouteKeys ammV4RouteKeys = new RayAmmV4RouteKeys();


        String mint = "5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ";
        String basePro = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
        String quoteMint = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263";
        String quotePro = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
        String plat = "6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt";
        String creator = "DHtDqpz1xV36tMWErpKcRZf9YAuL6D71YBZ3w4iLWnCv";

        ammV4RouteKeys.setProgramId("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
        ammV4RouteKeys.setPoolId("HVNwzt7Pxfu76KHCMQPTLuTCLTm6WnQ1esLv4eizseSv");
        ammV4RouteKeys.setAuthority("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1");
        ammV4RouteKeys.setVaultA("7KFdXKA5WkZBspxwqd9kSrDGTg9WhiX5TptUB3yRwEaE");
        ammV4RouteKeys.setVaultB("GehmCo7EgzkB4xxyviW6xdUhm1Ed2nN98QcfcRWQCfA9");


        String solCreate = PublicKey.findProgramAddress(List.of(fundAcc.getPublicKey().toByteArray(), new PublicKey(quotePro).toByteArray(), new PublicKey("So11111111111111111111111111111111111111112").toByteArray()), AssociatedTokenProgram.PROGRAM_ID).getAddress().toBase58();

        Transaction transaction = new Transaction();

        TransactionInstruction tranIn = SystemProgram.transfer(fundAcc.getPublicKey(), new PublicKey(jitAddress), tipPirce);
        transaction.addInstruction(tranIn);

        PublicKey.ProgramDerivedAddress baseUser = PublicKey.findProgramAddress(Arrays.asList(fundAcc.getPublicKey().toByteArray(), new PublicKey(basePro).toByteArray(), new PublicKey(mint).toByteArray()), AssociatedTokenProgram.PROGRAM_ID);

        PublicKey.ProgramDerivedAddress quoteUser = PublicKey.findProgramAddress(Arrays.asList(fundAcc.getPublicKey().toByteArray(), new PublicKey(quotePro).toByteArray(), new PublicKey(quoteMint).toByteArray()), AssociatedTokenProgram.PROGRAM_ID);


        //var ref = RayLaunchLabUtils.refLaunchLabToken2022(mint,quoteMint,fundAcc.getPublicKey().toBase58(),creator,plat,0,0);
        //System.out.println(ref);
        BigDecimal solPrice = new BigDecimal("112.66");
        //价格计算公式  买入数量 /  价格 =  买入数量
        // 买入数量
        BigDecimal quteSol = new BigDecimal("0.000002962");
        // 买入数量
        BigDecimal buySol = new BigDecimal("0.001");
        // 最大可支付数量
        long minAmountOut = 1L;
        //instruction = RayLaunchLabUtils.makeRayLaunchLabTotalToken2022(ref,0,amount.longValue(),minAmountOut);
        //transaction.addInstruction(instruction);

        List<TransactionInstruction> instructions = RayRuoteLaunchLabUtils.makeBuyInstructions(launchLabInfo(fundAcc.getPublicKeyBase58(), baseUser.getAddress().toBase58(), quoteUser.getAddress().toBase58()), ammV4RouteKeys, new PublicKey(solCreate), buySol.movePointRight(9).longValue(), minAmountOut, true);
        for (TransactionInstruction transactionInstruction : instructions) {
            transaction.addInstruction(transactionInstruction);
        }

        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(25820));
        transaction.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(178000));

        String blockHash = rpcApi.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulatedTransaction = rpcApi.simulateTransaction(transaction, fundAcc, blockHash);
        if (simulatedTransaction.getValue().getErr() != null) return;

        String tx = jitRpcApi.sendTransaction(transaction, fundAcc, blockHash);
        System.out.println(tx);
    }

}
