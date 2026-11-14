package org.p2p.solanaj.programs;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ray.RayRouteCpmmClmmPoolInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Instructions for Raydium's standard AMM Routing program.
 */
public final class RayRoutingProgram {

    public static final PublicKey PROGRAM_ID = new PublicKey("routeUGWgWzqBWFcrCfv8tritsqukccJPu3q5GPP3xS");

    private static final byte ROUTE_SWAP_BASE_IN = 0;

    private RayRoutingProgram() {
    }

    /**
     * Builds a standard exact-input sell route whose first hop is CPMM and second hop is CLMM.
     *
     * <p>This method only builds the routing instruction. The output stays in
     * {@code destinationTokenAccount}; it does not close or unwrap a WSOL account.</p>
     *
     * @param info             accounts in actual swap direction
     * @param amountIn         input token amount in raw units
     * @param minimumAmountOut minimum final output in raw units
     */
    public static TransactionInstruction makeCpmmToClmmSellInstruction(RayRouteCpmmClmmPoolInfo info, long amountIn, long minimumAmountOut) {
        checkAmounts(amountIn, minimumAmountOut);

        List<String> remainingAccounts = info.getClmmRemainingAccounts();
        List<AccountMeta> keys = new ArrayList<>(27 + size(remainingAccounts));

        // Router common accounts.
        keys.add(readonly(TokenProgram.PROGRAM_ID));
        keys.add(readonly(TokenProgram.TOKEN_2022));
        keys.add(readonly(AssociatedTokenProgram.PROGRAM_ID));
        keys.add(readonly(SystemProgram.PROGRAM_ID));
        keys.add(writableSigner(key(info.getPayer(), "payer")));
        keys.add(writable(key(info.getSourceTokenAccount(), "sourceTokenAccount")));
        keys.add(writable(key(info.getDestinationTokenAccount(), "destinationTokenAccount")));

        // Hop 1: source token -> route token through CPMM SwapBaseInput.
        keys.add(readonly(key(info.getCpmmProgram(), "cpmm.program")));
        keys.add(writable(key(info.getRouteTokenAccount(), "routeTokenAccount")));
        keys.add(writable(key(info.getInputMint(), "inputMint")));
        keys.add(writable(key(info.getRouteMint(), "routeMint")));
        keys.add(writable(key(info.getCpmmAuthority(), "cpmm.authority")));
        keys.add(writable(key(info.getCpmmConfig(), "cpmm.config")));
        keys.add(writable(key(info.getCpmmPool(), "cpmm.pool")));
        keys.add(writable(key(info.getCpmmInputVault(), "cpmm.inputVault")));
        keys.add(writable(key(info.getCpmmOutputVault(), "cpmm.outputVault")));
        keys.add(writable(key(info.getCpmmObservation(), "cpmm.observation")));

        // Hop 2: route token -> output token through CLMM SwapV2.
        keys.add(readonly(key(info.getClmmProgram(), "clmm.program")));
        keys.add(writable(key(info.getDestinationTokenAccount(), "destinationTokenAccount")));
        keys.add(writable(key(info.getRouteMint(), "routeMint")));
        keys.add(writable(key(info.getOutputMint(), "outputMint")));
        keys.add(writable(key(info.getClmmAmmConfig(), "clmm.ammConfig")));
        keys.add(writable(key(info.getClmmPool(), "clmm.pool")));
        keys.add(writable(key(info.getClmmInputVault(), "clmm.inputVault")));
        keys.add(writable(key(info.getClmmOutputVault(), "clmm.outputVault")));
        keys.add(writable(key(info.getClmmObservation(), "clmm.observation")));
        keys.add(readonly(key(info.getMemoProgram(), "memoProgram")));
        addWritableAccounts(keys, remainingAccounts);

        return new TransactionInstruction(PROGRAM_ID, keys, ByteBuffer.allocate(1 + Long.BYTES * 2).order(ByteOrder.LITTLE_ENDIAN).put(ROUTE_SWAP_BASE_IN).putLong(amountIn).putLong(minimumAmountOut).array());
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

    private static void addWritableAccounts(List<AccountMeta> keys, List<String> accounts) {
        if (accounts == null) {
            return;
        }
        for (int i = 0; i < accounts.size(); i++) {
            keys.add(writable(key(accounts.get(i), "clmm.remainingAccounts[" + i + "]")));
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
}
