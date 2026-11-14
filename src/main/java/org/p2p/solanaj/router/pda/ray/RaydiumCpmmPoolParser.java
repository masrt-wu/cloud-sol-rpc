package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.RaydiumLegs;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;

/** Raydium CPMM PoolState 快速转成路由对象，包括随机 signer 创建的池。 */
public final class RaydiumCpmmPoolParser {
    private RaydiumCpmmPoolParser() { }

    /**
     * 不按 PDA 重新推测地址，直接使用传入池地址和池中保存的账户。
     * 返回对象的 base/quote 分别表示链上的 token0/token1；
     * 若交易方向不确定，优先调用 RaydiumLegs.cpmm 并明确输入 mint。
     * @param poolAddress 实际 PoolState 地址
     * @param data PoolState 原始账户字节
     * @return 可直接传给 RaydiumLegs.cpmm/buyCpmm/sellCpmm 的对象
     */
    public static RaydiumLegs.Cpmm parse(PublicKey poolAddress, byte[] data) {
        return new RaydiumLegs.Cpmm(
                key(data, 8), poolAddress, key(data, 168), key(data, 200),
                key(data, 72), key(data, 104), key(data, 232), key(data, 264), key(data, 296));
    }

    /**
     * 将已解析的 PoolState 转换为 CPMM 路由对象，保留实际池地址。
     * @param poolAddress 实际 PoolState 地址
     * @param state 已解析的 PoolState 账户
     * @return CPMM 路由池对象
     */
    public static RaydiumLegs.Cpmm fromDexAccountData(PublicKey poolAddress, DexAccountData state) {
        return new RaydiumLegs.Cpmm(
                state.publicKey("amm_config"), poolAddress,
                state.publicKey("token_0_mint"), state.publicKey("token_1_mint"),
                state.publicKey("token_0_vault"), state.publicKey("token_1_vault"),
                state.publicKey("token_0_program"), state.publicKey("token_1_program"),
                state.publicKey("observation_key"));
    }
}
