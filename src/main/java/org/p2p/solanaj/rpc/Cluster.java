package org.p2p.solanaj.rpc;

public enum Cluster {
    DEVNET("https://api.devnet.solana.com"),
    TESTNET("https://api.testnet.solana.com"),
    MAINNET("https://api.mainnet-beta.solana.com"),
    ANKR("https://rpc.ankr.com/solana"),
    QUINET("https://fittest-cold-crater.solana-mainnet.quiknode.pro"),
    HELIUS("https://mainnet.helius-rpc.com/?api-key="),
    DEV_HELIUS("https://devnet.helius-rpc.com/?api-key="),
    K2_NET("https://staked-rpc-frankfurt.constant-k.com/?api-key=");

    private String endpoint;

    Cluster(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
