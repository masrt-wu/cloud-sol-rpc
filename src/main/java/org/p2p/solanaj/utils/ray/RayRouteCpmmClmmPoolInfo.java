package org.p2p.solanaj.utils.ray;

import lombok.Data;

import java.util.List;

/**
 * Accounts for a standard Raydium two-hop CPMM -&gt; CLMM route.
 *
 * <p>The mint and vault fields must be supplied in swap direction: input first,
 * output second. CLMM remaining accounts are ordered as required by the current
 * pool state, normally bitmap extension followed by one or more tick arrays.</p>
 */
@Data
public class RayRouteCpmmClmmPoolInfo {

    private String payer;

    private String sourceTokenAccount;
    private String routeTokenAccount;
    private String destinationTokenAccount;

    private String inputMint;
    private String routeMint;
    private String outputMint;

    private String cpmmProgram;
    private String cpmmAuthority;
    private String cpmmConfig;
    private String cpmmPool;
    private String cpmmInputVault;
    private String cpmmOutputVault;
    private String cpmmObservation;

    private String clmmProgram;
    private String clmmAmmConfig;
    private String clmmPool;
    private String clmmInputVault;
    private String clmmOutputVault;
    private String clmmObservation;
    private String memoProgram;
    private List<String> clmmRemainingAccounts;
}
