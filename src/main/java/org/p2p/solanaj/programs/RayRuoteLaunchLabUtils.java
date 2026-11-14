package org.p2p.solanaj.programs;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ray.RayAmmV4RouteKeys;
import org.p2p.solanaj.utils.ray.RayRotToken2022PoolInfo;
import org.p2p.solanaj.utils.ray.RayRouteToken2022PoolInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Raydium Routing instructions for the buy path
 * SOL -&gt; WSOL -&gt; AMM V4 quote token -&gt; LaunchLab Token-2022 token.
 *
 * <p>The returned instructions reproduce the router flow used by Raydium:</p>
 * <ol>
 *     <li>create/sync the payer's WSOL ATA,</li>
 *     <li>execute an exact-input routed swap,</li>
 *     <li>close the WSOL ATA and return its rent to the payer.</li>
 * </ol>
 */
public final class RayRuoteLaunchLabUtils {

    public static final PublicKey ROUTE_PROGRAM_ID = new PublicKey("routeUGWgWzqBWFcrCfv8tritsqukccJPu3q5GPP3xS");

    public static final PublicKey ROUTE_PRIVATE_PROGRAM_ID = new PublicKey("CmNFUmRJL7YcnVn22oZzwG5Xg5WJqbcHEc6BK5mzDNR8");
    public static final PublicKey WSOL_MINT = new PublicKey("So11111111111111111111111111111111111111112");

    private static final byte ROUTE_SWAP_BASE_IN = 0;
    private static final byte CREATE_SYNC_NATIVE = 5;
    private static final byte CLOSE_TOKEN_ACCOUNT = 6;

    private RayRuoteLaunchLabUtils() {
    }

    /**
     * Builds the complete three-instruction buy flow and derives the payer's WSOL ATA.
     *
     * @param launchLabInfo    LaunchLab accounts; {@code userQuoteToken} is the intermediate
     *                         quote-token ATA and {@code userBaseToken} is the bought token ATA
     * @param ammV4Keys        AMM V4 pool accounts for WSOL/quote-token
     * @param amountIn         SOL/WSOL input amount in lamports
     * @param minimumAmountOut minimum final Token-2022 output, in raw token units
     * @param ammV4IdOnly      use the router's id-only AMM V4 account layout (pool status != 1)
     * @return create/sync WSOL, routed swap, and close WSOL instructions, in execution order
     */
    public static List<TransactionInstruction> makeBuyInstructions(RayRotToken2022PoolInfo launchLabInfo, RayAmmV4RouteKeys ammV4Keys, long amountIn, long minimumAmountOut, boolean ammV4IdOnly) {
        PublicKey payer = key(require(launchLabInfo, "launchLabInfo").getPayer(), "payer");
        PublicKey userWsol = AssociatedTokenProgram.findAssociatedTokenAddress(payer, WSOL_MINT);
        return makeBuyInstructions(launchLabInfo, ammV4Keys, userWsol, amountIn, minimumAmountOut, ammV4IdOnly);
    }

    /**
     * Builds the complete three-instruction buy flow with an explicitly supplied WSOL account.
     */
    public static List<TransactionInstruction> makeBuyInstructions(RayRotToken2022PoolInfo launchLabInfo, RayAmmV4RouteKeys ammV4Keys, PublicKey userWsol,
                                                                   long amountIn, long minimumAmountOut, boolean ammV4IdOnly) {
        require(launchLabInfo, "launchLabInfo");
        require(ammV4Keys, "ammV4Keys");
        require(userWsol, "userWsol");
        checkAmounts(amountIn, minimumAmountOut);

        PublicKey payer = key(launchLabInfo.getPayer(), "payer");
        List<TransactionInstruction> instructions = new ArrayList<>(3);
        instructions.add(makeCreateSyncNativeInstruction(payer, userWsol, amountIn));
        instructions.add(makeRouteSwapBaseInInstruction(launchLabInfo, ammV4Keys, userWsol, amountIn, minimumAmountOut, ammV4IdOnly));
        instructions.add(makeCloseWsolInstruction(payer, userWsol));
        return List.copyOf(instructions);
    }

    /**
     * Builds the complete sell flow:
     * Token-2022 -&gt; LaunchLab quote token -&gt; USDC -&gt; WSOL -&gt; SOL.
     *
     * @param routeInfo        all LaunchLab and CLMM accounts for the three-hop route
     * @param amountIn         Token-2022 amount to sell, in raw units
     * @param minimumAmountOut minimum WSOL/SOL output, in lamports
     * @return routed swap and close-WSOL instructions, in execution order
     */
    public static List<TransactionInstruction> makeSellInstructions(RayRouteToken2022PoolInfo routeInfo, long amountIn, long minimumAmountOut) {
        require(routeInfo, "routeInfo");
        checkAmounts(amountIn, minimumAmountOut);

        PublicKey payer = key(routeInfo.getPayer(), "payer");
        PublicKey userWsol = key(routeInfo.getUserWsol(), "userWsol");
        return List.of(makeRouteSellBaseInInstruction(routeInfo, amountIn, minimumAmountOut), makeCloseWsolInstruction(payer, userWsol));
    }

    /**
     * Router instruction 0: exact-input LaunchLab -&gt; CLMM -&gt; CLMM routed sell.
     */
    public static TransactionInstruction makeRouteSellBaseInInstruction(RayRouteToken2022PoolInfo routeInfo, long amountIn, long minimumAmountOut) {
        require(routeInfo, "routeInfo");
        checkAmounts(amountIn, minimumAmountOut);

        PublicKey payer = key(routeInfo.getPayer(), "payer");
        PublicKey userBaseToken = key(routeInfo.getUserBaseToken(), "userBaseToken");
        PublicKey userBonk = key(routeInfo.getUserBonk(), "userBonk");
        PublicKey userUsdc = key(routeInfo.getUserUsdc(), "userUsdc");
        PublicKey userWsol = key(routeInfo.getUserWsol(), "userWsol");
        PublicKey baseMint = key(routeInfo.getBaseMint(), "baseMint");
        PublicKey bonkMint = key(routeInfo.getBonkMint(), "bonkMint");
        PublicKey usdcMint = key(routeInfo.getUsdcMint(), "usdcMint");
        PublicKey wsolMint = key(routeInfo.getWsolMint(), "wsolMint");
        PublicKey clmmProgram = key(routeInfo.getClmmProgram(), "clmmProgram");

        int remainingAccountCount = size(routeInfo.getBonkUsdcRemainingAccounts()) + size(routeInfo.getUsdcWsolRemainingAccounts());
        List<AccountMeta> keys = new ArrayList<>(40 + remainingAccountCount);

        // Router common accounts.
        keys.add(readonly(TokenProgram.PROGRAM_ID));
        keys.add(readonly(TokenProgram.TOKEN_2022));
        keys.add(readonly(AssociatedTokenProgram.PROGRAM_ID));
        keys.add(readonly(SystemProgram.PROGRAM_ID));
        keys.add(writableSigner(payer));
        keys.add(writable(userBaseToken));
        keys.add(writable(userWsol));

        // Hop 1: Token-2022 base token -> BONK through LaunchLab SellExactIn.
        keys.add(readonly(key(routeInfo.getLaunchProgram(), "launchProgram")));
        keys.add(writable(userBonk));
        keys.add(writable(baseMint));
        keys.add(writable(bonkMint));
        keys.add(readonly(key(routeInfo.getAuthority(), "launch.authority")));
        keys.add(readonly(key(routeInfo.getGlobalConfig(), "launch.globalConfig")));
        keys.add(readonly(key(routeInfo.getPlatformConfig(), "launch.platformConfig")));
        keys.add(writable(key(routeInfo.getPoolState(), "launch.poolState")));
        keys.add(writable(key(routeInfo.getBaseVault(), "launch.baseVault")));
        keys.add(writable(key(routeInfo.getQuoteVault(), "launch.quoteVault")));
        keys.add(readonly(key(routeInfo.getEventAuthority(), "launch.eventAuthority")));
        keys.add(writable(key(routeInfo.getPlatformFeeVault(), "launch.platformFeeVault")));
        keys.add(writable(key(routeInfo.getCreatorFeeVault(), "launch.creatorFeeVault")));

        // Hop 2: BONK -> USDC through CLMM SwapV2.
        keys.add(readonly(clmmProgram));
        keys.add(writable(userUsdc));
        keys.add(writable(bonkMint));
        keys.add(writable(usdcMint));
        keys.add(writable(key(routeInfo.getBonkUsdcAmmConfig(), "bonkUsdc.ammConfig")));
        keys.add(writable(key(routeInfo.getBonkUsdcPoolState(), "bonkUsdc.poolState")));
        keys.add(writable(key(routeInfo.getBonkUsdcBonkVault(), "bonkUsdc.bonkVault")));
        keys.add(writable(key(routeInfo.getBonkUsdcUsdcVault(), "bonkUsdc.usdcVault")));
        keys.add(writable(key(routeInfo.getBonkUsdcObservationState(), "bonkUsdc.observationState")));
        keys.add(readonly(key(routeInfo.getMemoProgram(), "memoProgram")));
        addWritableAccounts(keys, routeInfo.getBonkUsdcRemainingAccounts(), "bonkUsdc.remainingAccounts");

        // Hop 3: USDC -> WSOL through CLMM SwapV2.
        keys.add(readonly(clmmProgram));
        keys.add(writable(userWsol));
        keys.add(writable(usdcMint));
        keys.add(writable(wsolMint));
        keys.add(writable(key(routeInfo.getUsdcWsolAmmConfig(), "usdcWsol.ammConfig")));
        keys.add(writable(key(routeInfo.getUsdcWsolPoolState(), "usdcWsol.poolState")));
        keys.add(writable(key(routeInfo.getUsdcWsolUsdcVault(), "usdcWsol.usdcVault")));
        keys.add(writable(key(routeInfo.getUsdcWsolWsolVault(), "usdcWsol.wsolVault")));
        keys.add(writable(key(routeInfo.getUsdcWsolObservationState(), "usdcWsol.observationState")));
        keys.add(readonly(key(routeInfo.getMemoProgram(), "memoProgram")));
        addWritableAccounts(keys, routeInfo.getUsdcWsolRemainingAccounts(), "usdcWsol.remainingAccounts");

        return new TransactionInstruction(ROUTE_PROGRAM_ID, keys, u8U64U64(ROUTE_SWAP_BASE_IN, amountIn, minimumAmountOut));
    }

    /**
     * Router instruction 5: idempotently create the WSOL ATA, transfer lamports and sync it.
     */
    public static TransactionInstruction makeCreateSyncNativeInstruction(PublicKey payer, PublicKey userWsol, long amountIn) {
        require(payer, "payer");
        require(userWsol, "userWsol");
        if (amountIn <= 0) {
            throw new IllegalArgumentException("amountIn must be greater than zero");
        }

        List<AccountMeta> keys = List.of(writableSigner(payer), writable(userWsol), writable(WSOL_MINT), readonly(TokenProgram.PROGRAM_ID), readonly(AssociatedTokenProgram.PROGRAM_ID), readonly(SystemProgram.PROGRAM_ID));
        return new TransactionInstruction(ROUTE_PROGRAM_ID, keys, u8U64(CREATE_SYNC_NATIVE, amountIn));
    }

    /**
     * Router instruction 0: exact-input AMM V4 -&gt; LaunchLab routed buy.
     *
     * <p>When {@code ammV4IdOnly} is true the legacy OpenBook/market slots are filled with
     * the AMM pool id, exactly as required by pools whose status enables id-only routing.</p>
     */
    public static TransactionInstruction makeRouteSwapBaseInInstruction(RayRotToken2022PoolInfo launchLabInfo, RayAmmV4RouteKeys ammV4Keys, PublicKey userWsol, long amountIn, long minimumAmountOut, boolean ammV4IdOnly) {
        require(launchLabInfo, "launchLabInfo");
        require(ammV4Keys, "ammV4Keys");
        require(userWsol, "userWsol");
        checkAmounts(amountIn, minimumAmountOut);

        PublicKey payer = key(launchLabInfo.getPayer(), "payer");
        PublicKey userBaseToken = key(launchLabInfo.getUserBaseToken(), "userBaseToken");
        PublicKey userQuoteToken = key(launchLabInfo.getUserQuoteToken(), "userQuoteToken");
        PublicKey quoteMint = key(launchLabInfo.getQuoteMint(), "quoteMint");
        PublicKey baseMint = key(launchLabInfo.getBaseMint(), "baseMint");
        PublicKey ammPool = key(ammV4Keys.getPoolId(), "ammV4.poolId");

        List<AccountMeta> keys = new ArrayList<>(37);

        // Router common accounts.
        keys.add(readonly(TokenProgram.PROGRAM_ID));
        keys.add(readonly(TokenProgram.TOKEN_2022));
        keys.add(readonly(AssociatedTokenProgram.PROGRAM_ID));
        keys.add(readonly(SystemProgram.PROGRAM_ID));
        keys.add(writableSigner(payer));
        keys.add(writable(userWsol));
        keys.add(writable(userBaseToken));

        // Hop 1: WSOL -> LaunchLab quote token through AMM V4.
        keys.add(readonly(key(ammV4Keys.getProgramId(), "ammV4.programId")));
        keys.add(writable(userQuoteToken));
        keys.add(writable(WSOL_MINT));
        keys.add(writable(quoteMint));
        keys.add(writable(ammPool));
        keys.add(readonly(key(ammV4Keys.getAuthority(), "ammV4.authority")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketProgramId(), "ammV4.marketProgramId")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketAuthority(), "ammV4.marketAuthority")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getOpenOrders(), "ammV4.openOrders")));
        keys.add(writable(key(ammV4Keys.getVaultA(), "ammV4.vaultA")));
        keys.add(writable(key(ammV4Keys.getVaultB(), "ammV4.vaultB")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketId(), "ammV4.marketId")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketBids(), "ammV4.marketBids")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketAsks(), "ammV4.marketAsks")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketEventQueue(), "ammV4.marketEventQueue")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketBaseVault(), "ammV4.marketBaseVault")));
        keys.add(writable(ammV4IdOnly ? ammPool : key(ammV4Keys.getMarketQuoteVault(), "ammV4.marketQuoteVault")));

        // Hop 2: quote token -> Token-2022 base token through LaunchLab BuyExactIn.
        keys.add(readonly(key(launchLabInfo.getProgram(), "launchProgram")));
        keys.add(writable(userBaseToken));
        keys.add(writable(quoteMint));
        keys.add(writable(baseMint));
        keys.add(readonly(key(launchLabInfo.getAuthority(), "launch.authority")));
        keys.add(readonly(key(launchLabInfo.getGlobalConfig(), "launch.globalConfig")));
        keys.add(readonly(key(launchLabInfo.getPlatformConfig(), "launch.platformConfig")));
        keys.add(writable(key(launchLabInfo.getPoolState(), "launch.poolState")));
        keys.add(writable(key(launchLabInfo.getBaseVault(), "launch.baseVault")));
        keys.add(writable(key(launchLabInfo.getQuoteVault(), "launch.quoteVault")));
        keys.add(readonly(key(launchLabInfo.getEventAuthority(), "launch.eventAuthority")));
        keys.add(writable(key(launchLabInfo.getPlatformFeeVault(), "launch.platformFeeVault")));
        keys.add(writable(key(launchLabInfo.getCreatorFeeVault(), "launch.creatorFeeVault")));

        return new TransactionInstruction(ROUTE_PROGRAM_ID, keys, u8U64U64(ROUTE_SWAP_BASE_IN, amountIn, minimumAmountOut));
    }

    /**
     * Router instruction 6: close the WSOL token account and return lamports to the payer.
     */
    public static TransactionInstruction makeCloseWsolInstruction(PublicKey payer, PublicKey userWsol) {
        require(payer, "payer");
        require(userWsol, "userWsol");
        List<AccountMeta> keys = List.of(writableSigner(payer), writable(userWsol), writableSigner(payer), readonly(TokenProgram.PROGRAM_ID), readonly(AssociatedTokenProgram.PROGRAM_ID), readonly(SystemProgram.PROGRAM_ID));
        return new TransactionInstruction(ROUTE_PROGRAM_ID, keys, new byte[]{CLOSE_TOKEN_ACCOUNT});
    }

    private static byte[] u8U64(byte instruction, long value) {
        return ByteBuffer.allocate(1 + Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).put(instruction).putLong(value).array();
    }

    private static byte[] u8U64U64(byte instruction, long first, long second) {
        return ByteBuffer.allocate(1 + Long.BYTES * 2).order(ByteOrder.LITTLE_ENDIAN).put(instruction).putLong(first).putLong(second).array();
    }

    private static void checkAmounts(long amountIn, long minimumAmountOut) {
        if (amountIn <= 0) {
            throw new IllegalArgumentException("amountIn must be greater than zero");
        }
        if (minimumAmountOut < 0) {
            throw new IllegalArgumentException("minimumAmountOut must not be negative");
        }
    }

    private static int size(List<String> accounts) {
        return accounts == null ? 0 : accounts.size();
    }

    private static void addWritableAccounts(List<AccountMeta> keys, List<String> accounts, String name) {
        if (accounts == null) {
            return;
        }
        for (int i = 0; i < accounts.size(); i++) {
            keys.add(writable(key(accounts.get(i), name + "[" + i + "]")));
        }
    }

    private static AccountMeta writableSigner(PublicKey key) {
        return new AccountMeta(key, true, true);
    }

    private static AccountMeta writable(PublicKey key) {
        return new AccountMeta(key, false, true);
    }

    private static AccountMeta readonly(PublicKey key) {
        return new AccountMeta(key, false, false);
    }

    private static PublicKey key(String value, String name) {
//        if (value == null || value.isBlank()) {
//            throw new IllegalArgumentException(name + " must not be blank");
//        }
        return new PublicKey(value);
    }

    private static <T> T require(T value, String name) {
        //return Objects.requireNonNull(value, name + " must not be null");
        return value;
    }


}
