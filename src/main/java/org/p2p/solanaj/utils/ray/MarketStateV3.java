package org.p2p.solanaj.utils.ray;


import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


@Data
public class MarketStateV3 {
    // 先声明字段
    // blob(5)
    public byte[] blob5;

    // blob(8) (accountFlags)
    public byte[] accountFlags;

    public PublicKey ownAddress;

    public long vaultSignerNonce;

    public PublicKey baseMint;
    public PublicKey quoteMint;

    public PublicKey baseVault;
    public long baseDepositsTotal;
    public long baseFeesAccrued;

    public PublicKey quoteVault;
    public long quoteDepositsTotal;
    public long quoteFeesAccrued;

    public long quoteDustThreshold;

    public PublicKey requestQueue;
    public PublicKey eventQueue;

    public PublicKey bids;
    public PublicKey asks;

    public long baseLotSize;
    public long quoteLotSize;

    public long feeRateBps;

    public long referrerRebatesAccrued;

    // blob(7)
    public byte[] blob7;

    public static MarketStateV3 decode(byte[] accountData) {
        MarketStateV3 state = new MarketStateV3();
        ByteBuffer bb = ByteBuffer.wrap(accountData);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // blob(5)
        state.blob5 = new byte[5];
        bb.get(state.blob5);

        // blob(8) (accountFlags)
        state.accountFlags = new byte[8];
        bb.get(state.accountFlags);

        // publicKey('ownAddress')
        state.ownAddress = readPublicKey(bb);

        // u64('vaultSignerNonce')
        state.vaultSignerNonce = bb.getLong();

        // publicKey('baseMint')
        state.baseMint = readPublicKey(bb);

        // publicKey('quoteMint')
        state.quoteMint = readPublicKey(bb);

        // publicKey('baseVault')
        state.baseVault = readPublicKey(bb);

        // u64('baseDepositsTotal')
        state.baseDepositsTotal = bb.getLong();

        // u64('baseFeesAccrued')
        state.baseFeesAccrued = bb.getLong();

        // publicKey('quoteVault')
        state.quoteVault = readPublicKey(bb);

        // u64('quoteDepositsTotal')
        state.quoteDepositsTotal = bb.getLong();

        // u64('quoteFeesAccrued')
        state.quoteFeesAccrued = bb.getLong();

        // u64('quoteDustThreshold')
        state.quoteDustThreshold = bb.getLong();

        // publicKey('requestQueue')
        state.requestQueue = readPublicKey(bb);

        // publicKey('eventQueue')
        state.eventQueue = readPublicKey(bb);

        // publicKey('bids')
        state.bids = readPublicKey(bb);

        // publicKey('asks')
        state.asks = readPublicKey(bb);

        // u64('baseLotSize')
        state.baseLotSize = bb.getLong();

        // u64('quoteLotSize')
        state.quoteLotSize = bb.getLong();

        // u64('feeRateBps')
        state.feeRateBps = bb.getLong();

        // u64('referrerRebatesAccrued')
        state.referrerRebatesAccrued = bb.getLong();

        // blob(7)
        state.blob7 = new byte[7];
        bb.get(state.blob7);

        return state;
    }

    private static PublicKey readPublicKey(ByteBuffer bb) {
        byte[] pubKeyBytes = new byte[32];
        bb.get(pubKeyBytes);
        return new PublicKey(pubKeyBytes);
    }

}

