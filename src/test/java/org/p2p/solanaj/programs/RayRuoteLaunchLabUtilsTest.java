package org.p2p.solanaj.programs;

import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ray.RayAmmV4RouteKeys;
import org.p2p.solanaj.utils.ray.RayRotToken2022PoolInfo;
import org.p2p.solanaj.utils.ray.RayRouteToken2022PoolInfo;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RayRuoteLaunchLabUtilsTest {

    private static final String PAYER = "B3s5jQffBUH1cAwUQMhVPVz2QMUFeTmktmyR1Btny4j3";
    private static final String USER_WSOL = "CHmPBhZMtzypQdMb9UVCRwdP79wwgJ9AZZW7W4epXSEb";
    private static final String USER_BONK = "25GZpYwrUZLR5V3y6VmCkxt1TP5GWH1BkpSZgLmwW2N2";
    private static final String USER_BASE = "9Ay3pXKEHirVYgZUeWSt8BLETpiWt8xXdADqtV5vXTKT";
    private static final String AMM_POOL = "HVNwzt7Pxfu76KHCMQPTLuTCLTm6WnQ1esLv4eizseSv";

    @Test
    void rayRouBuy() {
        List<TransactionInstruction> instructions = RayRuoteLaunchLabUtils.makeBuyInstructions(
                launchLabInfo(),
                ammV4Keys(),
                new PublicKey(USER_WSOL),
                1_000_000_000L,
                24_256_082_371_591L,
                true
        );

        assertEquals(3, instructions.size());
        assertInstruction(instructions.get(0), "4hEHJgasXAA3", List.of(
                PAYER,
                USER_WSOL,
                "So11111111111111111111111111111111111111112",
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111"
        ));
        assertInstruction(instructions.get(1), "116fkno7Mbxmd9N5nrMm4es", List.of(
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111",
                PAYER,
                USER_WSOL,
                USER_BASE,
                "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
                USER_BONK,
                "So11111111111111111111111111111111111111112",
                "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
                AMM_POOL,
                "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1",
                AMM_POOL,
                AMM_POOL,
                AMM_POOL,
                "7KFdXKA5WkZBspxwqd9kSrDGTg9WhiX5TptUB3yRwEaE",
                "GehmCo7EgzkB4xxyviW6xdUhm1Ed2nN98QcfcRWQCfA9",
                AMM_POOL,
                AMM_POOL,
                AMM_POOL,
                AMM_POOL,
                AMM_POOL,
                AMM_POOL,
                "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj",
                USER_BASE,
                "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
                "5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ",
                "WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh",
                "7G6Gg6rCnaegAhPBdZnRKCkzDWB6K6j4vmhtivSJhVtA",
                "6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt",
                "Bv3dcBWqTapef8Cd7RjkK6xzK18KD3eY1Re4W9pQ3XLw",
                "8Ty7vwg4BdaCWkxiBwpvbhBEMX8VtwvfHx4qUjFVFFLs",
                "F2i1ksJk9Yv2ZSbGpFs1XrjQizDJUGcN65fZK4k13rtn",
                "2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr",
                "EoHk1RAMRmodrUWNe8jvPrPy1DVfhfhUC1f7bKCmN5vy",
                "CsDoqYxi5RbSg12FMCf7t9HHvu8PLGxaEiQzft6pWW44"
        ));
        assertInstruction(instructions.get(2), "7", List.of(
                PAYER,
                USER_WSOL,
                PAYER,
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111"
        ));

        List<AccountMeta> routeKeys = instructions.get(1).getKeys();
        assertTrue(routeKeys.get(4).isSigner());
        assertTrue(routeKeys.get(4).isWritable());
        assertFalse(routeKeys.get(12).isWritable());
        assertFalse(routeKeys.get(24).isWritable());
        assertTrue(routeKeys.get(35).isWritable());
    }

    @Test
    void derivesTheObservedWsolAta() {
        List<TransactionInstruction> instructions = RayRuoteLaunchLabUtils.makeBuyInstructions(
                launchLabInfo(),
                ammV4Keys(),
                1_000_000_000L,
                24_256_082_371_591L,
                true
        );

        assertEquals(USER_WSOL, instructions.get(0).getKeys().get(1).getPublicKey().toBase58());
    }

    @Test
    void rayRouSell() {
        List<TransactionInstruction> instructions = RayRuoteLaunchLabUtils.makeSellInstructions(
                sellRouteInfo(),
                25_006_270_486_177L,
                793_389_630L
        );

        assertEquals(2, instructions.size());
        assertInstruction(instructions.get(0), "1LxKNxR3VebQddbPbv5vQtF", List.of(
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111",
                PAYER,
                USER_BASE,
                USER_WSOL,
                "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj",
                USER_BONK,
                "5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ",
                "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
                "WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh",
                "7G6Gg6rCnaegAhPBdZnRKCkzDWB6K6j4vmhtivSJhVtA",
                "6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt",
                "Bv3dcBWqTapef8Cd7RjkK6xzK18KD3eY1Re4W9pQ3XLw",
                "8Ty7vwg4BdaCWkxiBwpvbhBEMX8VtwvfHx4qUjFVFFLs",
                "F2i1ksJk9Yv2ZSbGpFs1XrjQizDJUGcN65fZK4k13rtn",
                "2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr",
                "EoHk1RAMRmodrUWNe8jvPrPy1DVfhfhUC1f7bKCmN5vy",
                "CsDoqYxi5RbSg12FMCf7t9HHvu8PLGxaEiQzft6pWW44",
                "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
                "FKfwQXhxawRkn8nfr3L59fB22G38zmnmTeh1UWZ5TDAy",
                "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
                "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "J8u7HvA1g1p2CdhBFdsnTxDzGkekRpdw4GrL9MKU2D3U",
                "3UwfrdLTpAjxTRni1boc5HUWe6hzc4HgE5yLdvEp2Noc",
                "FwK83er9aFjLJ18WonFT84mf4spkcaHoqTwurS5KDXPq",
                "J8ruhUVvWHSUDExTmwdLJYtsRbQX8GCvX5yiGSWazjUY",
                "FHe9fyjnEgJjCLZNCLD3aERJC1L7PG94m7XdhkgZ8fWs",
                "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
                "4omhGHGx2R7TnuXP3ZTVskt4WqNneb4EVg2qz87UtqSe",
                "AmFSDPHPPw9sBJJUVn9Drp16QoQxoMvbKwUJqNgu9N5C",
                "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
                USER_WSOL,
                "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "So11111111111111111111111111111111111111112",
                "EdPxg8QaeFSrTYqdWJn6Kezwy9McWncTYueD9eMGCuzR",
                "CYbD9RaToYMtWKA7QZyoLahnHdWq553Vm62Lh6qWtuxq",
                "3bWPj5eepJm8CxUzk5MMFMN2CFJkntxKvbmy4zwwtpJd",
                "GviiXg2Xc1xCpyNY36r7h1EAy7uvse5UMkiiyHjRDU6Z",
                "AA5RaVvyGyZgtmAsJJHT5ZVBxVPtAXuYaMwfgeFJW4Mk",
                "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
                "72jQFwjd14BEhyDfdQsH7D2hS5dN1H6bzsikjkyHyx2D",
                "2rLWDnq3YGgyAtXy4PSUFYQoRySfVx2WBi2JE11bybms"
        ));
        assertInstruction(instructions.get(1), "7", List.of(
                PAYER,
                USER_WSOL,
                PAYER,
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111"
        ));

        List<AccountMeta> routeKeys = instructions.get(0).getKeys();
        assertEquals(44, routeKeys.size());
        Set<Integer> readonlyIndexes = Set.of(0, 1, 2, 3, 7, 11, 12, 13, 17, 20, 29, 32, 41);
        for (int i = 0; i < routeKeys.size(); i++) {
            assertEquals(i == 4, routeKeys.get(i).isSigner(), "unexpected signer flag at index " + i);
            assertEquals(!readonlyIndexes.contains(i), routeKeys.get(i).isWritable(), "unexpected writable flag at index " + i);
        }
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () ->
                RayRuoteLaunchLabUtils.makeBuyInstructions(
                        launchLabInfo(), ammV4Keys(), new PublicKey(USER_WSOL), 0, 1, true));
        assertThrows(IllegalArgumentException.class, () ->
                RayRuoteLaunchLabUtils.makeBuyInstructions(
                        launchLabInfo(), ammV4Keys(), new PublicKey(USER_WSOL), 1, -1, true));
    }

    private static void assertInstruction(
            TransactionInstruction instruction,
            String expectedDataBase58,
            List<String> expectedAccounts) {
        assertEquals(RayRuoteLaunchLabUtils.ROUTE_PROGRAM_ID, instruction.getProgramId());
        assertArrayEquals(Base58.decode(expectedDataBase58), instruction.getData());
        assertEquals(expectedAccounts, instruction.getKeys().stream()
                .map(meta -> meta.getPublicKey().toBase58())
                .toList());
    }

    private static RayAmmV4RouteKeys ammV4Keys() {
        RayAmmV4RouteKeys keys = new RayAmmV4RouteKeys();
        keys.setProgramId("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
        keys.setPoolId(AMM_POOL);
        keys.setAuthority("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1");
        keys.setVaultA("7KFdXKA5WkZBspxwqd9kSrDGTg9WhiX5TptUB3yRwEaE");
        keys.setVaultB("GehmCo7EgzkB4xxyviW6xdUhm1Ed2nN98QcfcRWQCfA9");
        return keys;
    }

    private static RayRotToken2022PoolInfo launchLabInfo() {
        RayRotToken2022PoolInfo info = new RayRotToken2022PoolInfo();
        info.setPayer(PAYER);
        info.setAuthority("WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh");
        info.setGlobalConfig("7G6Gg6rCnaegAhPBdZnRKCkzDWB6K6j4vmhtivSJhVtA");
        info.setPlatformConfig("6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt");
        info.setPoolState("Bv3dcBWqTapef8Cd7RjkK6xzK18KD3eY1Re4W9pQ3XLw");
        info.setUserBaseToken(USER_BASE);
        info.setUserQuoteToken(USER_BONK);
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

    private static RayRouteToken2022PoolInfo sellRouteInfo() {
        RayRouteToken2022PoolInfo info = new RayRouteToken2022PoolInfo();
        info.setPayer(PAYER);
        info.setRouteProgram("routeUGWgWzqBWFcrCfv8tritsqukccJPu3q5GPP3xS");
        info.setTokenProgram("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        info.setToken2022Program("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");
        info.setAssociatedTokenProgram("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
        info.setSystemProgram("11111111111111111111111111111111");
        info.setMemoProgram("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
        info.setUserBaseToken(USER_BASE);
        info.setUserBonk(USER_BONK);
        info.setUserUsdc("FKfwQXhxawRkn8nfr3L59fB22G38zmnmTeh1UWZ5TDAy");
        info.setUserWsol(USER_WSOL);
        info.setBaseMint("5HcMuG7toPaAEQLJSsWZVZG9v4wtTUca5MZvBZeoXQxQ");
        info.setBonkMint("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263");
        info.setUsdcMint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        info.setWsolMint("So11111111111111111111111111111111111111112");
        info.setLaunchProgram("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
        info.setAuthority("WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh");
        info.setGlobalConfig("7G6Gg6rCnaegAhPBdZnRKCkzDWB6K6j4vmhtivSJhVtA");
        info.setPlatformConfig("6BwHHDg3u1854jC8PDLXvR4spTcLNaoBxLJNGC4nTESt");
        info.setPoolState("Bv3dcBWqTapef8Cd7RjkK6xzK18KD3eY1Re4W9pQ3XLw");
        info.setBaseVault("8Ty7vwg4BdaCWkxiBwpvbhBEMX8VtwvfHx4qUjFVFFLs");
        info.setQuoteVault("F2i1ksJk9Yv2ZSbGpFs1XrjQizDJUGcN65fZK4k13rtn");
        info.setEventAuthority("2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr");
        info.setPlatformFeeVault("EoHk1RAMRmodrUWNe8jvPrPy1DVfhfhUC1f7bKCmN5vy");
        info.setCreatorFeeVault("CsDoqYxi5RbSg12FMCf7t9HHvu8PLGxaEiQzft6pWW44");
        info.setClmmProgram("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");
        info.setBonkUsdcAmmConfig("J8u7HvA1g1p2CdhBFdsnTxDzGkekRpdw4GrL9MKU2D3U");
        info.setBonkUsdcPoolState("3UwfrdLTpAjxTRni1boc5HUWe6hzc4HgE5yLdvEp2Noc");
        info.setBonkUsdcBonkVault("FwK83er9aFjLJ18WonFT84mf4spkcaHoqTwurS5KDXPq");
        info.setBonkUsdcUsdcVault("J8ruhUVvWHSUDExTmwdLJYtsRbQX8GCvX5yiGSWazjUY");
        info.setBonkUsdcObservationState("FHe9fyjnEgJjCLZNCLD3aERJC1L7PG94m7XdhkgZ8fWs");
        info.setBonkUsdcRemainingAccounts(List.of(
                "4omhGHGx2R7TnuXP3ZTVskt4WqNneb4EVg2qz87UtqSe",
                "AmFSDPHPPw9sBJJUVn9Drp16QoQxoMvbKwUJqNgu9N5C"
        ));
        info.setUsdcWsolAmmConfig("EdPxg8QaeFSrTYqdWJn6Kezwy9McWncTYueD9eMGCuzR");
        info.setUsdcWsolPoolState("CYbD9RaToYMtWKA7QZyoLahnHdWq553Vm62Lh6qWtuxq");
        info.setUsdcWsolUsdcVault("3bWPj5eepJm8CxUzk5MMFMN2CFJkntxKvbmy4zwwtpJd");
        info.setUsdcWsolWsolVault("GviiXg2Xc1xCpyNY36r7h1EAy7uvse5UMkiiyHjRDU6Z");
        info.setUsdcWsolObservationState("AA5RaVvyGyZgtmAsJJHT5ZVBxVPtAXuYaMwfgeFJW4Mk");
        info.setUsdcWsolRemainingAccounts(List.of(
                "72jQFwjd14BEhyDfdQsH7D2hS5dN1H6bzsikjkyHyx2D",
                "2rLWDnq3YGgyAtXy4PSUFYQoRySfVx2WBi2JE11bybms"
        ));
        return info;
    }
}
