package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.LaunchLabLegs;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;

/** Raydium LaunchLab PoolState 直接转为多跳路由所需的轻量池对象。 */
public final class LaunchLabRouterPoolParser {
    private LaunchLabRouterPoolParser() { }

    /**
     * 不重复解码完整 PoolState；平台/创建者费账户不在 PoolState 中，调用方提供。
     * 现有 LaunchLabLegs.Pool 构造器自身仍保留必要的账户约束。
     * @param poolAddress 实际 PoolState 地址
     * @param data PoolState 原始账户字节
     * @param platformFeeVault 平台费用 vault 地址
     * @param creatorFeeVault 创建者费用 vault 地址
     * @return 可直接传给 LaunchLabLegs.buy/sell 的对象
     */
    public static LaunchLabLegs.Pool parse(
            PublicKey poolAddress, byte[] data,
            PublicKey platformFeeVault, PublicKey creatorFeeVault) {
        int flags = data[365] & 0xff;
        PublicKey baseProgram = (flags & 1) == 0 ? TokenProgram.PROGRAM_ID : TokenProgram.TOKEN_2022;
        PublicKey quoteProgram = (flags & 2) == 0 ? TokenProgram.PROGRAM_ID : TokenProgram.TOKEN_2022;
        return new LaunchLabLegs.Pool(
                key(data, 205), key(data, 237), baseProgram, quoteProgram,
                poolAddress, key(data, 269), key(data, 301), key(data, 173),
                platformFeeVault, creatorFeeVault, key(data, 141));
    }

    /**
     * 使用已解析的 LaunchLab PoolState 构造路由对象，不重复解码池账户。
     * @param poolAddress 实际 PoolState 地址
     * @param state 已解析的 PoolState 账户
     * @param platformFeeVault 当前平台费用 vault 地址
     * @param creatorFeeVault 当前创建者费用 vault 地址
     * @return LaunchLab 路由池对象
     */
    public static LaunchLabLegs.Pool fromDexAccountData(
            PublicKey poolAddress, DexAccountData state,
            PublicKey platformFeeVault, PublicKey creatorFeeVault) {
        int flags = state.intValue("token_program_flag");
        PublicKey baseProgram = (flags & 1) == 0 ? TokenProgram.PROGRAM_ID : TokenProgram.TOKEN_2022;
        PublicKey quoteProgram = (flags & 2) == 0 ? TokenProgram.PROGRAM_ID : TokenProgram.TOKEN_2022;
        return new LaunchLabLegs.Pool(
                state.publicKey("base_mint"), state.publicKey("quote_mint"), baseProgram, quoteProgram,
                poolAddress, state.publicKey("base_vault"), state.publicKey("quote_vault"),
                state.publicKey("platform_config"), platformFeeVault, creatorFeeVault,
                state.publicKey("global_config"));
    }
}
