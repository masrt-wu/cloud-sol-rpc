package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.RaydiumLegs;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;

/** Raydium AMM V4 的 752 字节 AmmInfo 快速转成路由对象。 */
public final class RaydiumAmmV4PoolParser {
    private RaydiumAmmV4PoolParser() { }

    /**
     * 不做 owner/长度/状态重复校验；只取链上保存的 mint 与 vault。
     * 该传统 AMM V4 池使用普通 SPL Token Program。
     * @param ammAddress 实际 AmmInfo 账户地址，不是程序地址
     * @param data AmmInfo 原始账户字节
     * @return 可直接传给 RaydiumLegs.ammV4/buyAmmV4/sellAmmV4 的对象
     */
    public static RaydiumLegs.AmmV4 parse(PublicKey ammAddress, byte[] data) {
        return new RaydiumLegs.AmmV4(ammAddress,
                key(data, 400), key(data, 432), key(data, 336), key(data, 368),
                TokenProgram.PROGRAM_ID);
    }

    /**
     * 将已解析的 AmmInfo 直接转换成路由对象。
     * @param ammAddress 实际 AmmInfo 地址
     * @param state 已解析的 AmmInfo 账户
     * @return AMM V4 路由池对象
     */
    public static RaydiumLegs.AmmV4 fromDexAccountData(PublicKey ammAddress, DexAccountData state) {
        return new RaydiumLegs.AmmV4(ammAddress,
                state.publicKey("base_mint"), state.publicKey("quote_mint"),
                state.publicKey("base_vault"), state.publicKey("quote_vault"),
                TokenProgram.PROGRAM_ID);
    }
}
