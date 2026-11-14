package org.p2p.solanaj.utils.ray;

import lombok.Data;

@Data
public class RayAmmV4RouteKeys {

    private String programId;

    private String poolId;
    private String authority;

    private String marketProgramId;
    private String marketAuthority;

    private String openOrders;

    private String vaultA;
    private String vaultB;

    private String marketId;
    private String marketBids;
    private String marketAsks;
    private String marketEventQueue;

    private String marketBaseVault;
    private String marketQuoteVault;

    // getter setter
}
