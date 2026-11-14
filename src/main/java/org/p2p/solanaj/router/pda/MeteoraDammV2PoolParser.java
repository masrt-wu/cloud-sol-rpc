package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.MeteoraLegs;

import java.util.Map;
import java.util.Objects;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.tokenProgram;

/** Meteora DAMM v2 Pool 直接转成精确输入路由对象。 */
public final class MeteoraDammV2PoolParser {
    private MeteoraDammV2PoolParser() { }

    /**
     * 不校验 owner/判别符；费用调度器是否需要 sysvar 由调用方按最新状态决定。
     * @param poolAddress 实际 DAMM v2 Pool 地址
     * @param data Pool 的原始账户字节
     * @param referralTokenAccount 可选转介费账户，不需要时传 null
     * @param includeRateLimiterSysvar 当前费用模式是否需要指令 sysvar
     * @return 可直接传给 MeteoraLegs.dammV2/buyDammV2/sellDammV2 的对象
     */
    public static MeteoraLegs.DammV2 parse(
            PublicKey poolAddress, byte[] data, PublicKey referralTokenAccount,
            boolean includeRateLimiterSysvar) {
        return new MeteoraLegs.DammV2(
                poolAddress, key(data, 168), key(data, 200), key(data, 232), key(data, 264),
                tokenProgram(data, 482), tokenProgram(data, 483),
                referralTokenAccount, includeRateLimiterSysvar);
    }

    /**
     * 使用已经解析的 Pool 字段构造 DAMM v2 路由对象。
     * @param poolAddress 实际 Pool 地址
     * @param state 已解析的 Pool 账户
     * @param referralTokenAccount 可选转介费账户
     * @param includeRateLimiterSysvar 当前费用模式是否要求指令 sysvar
     * @return DAMM v2 路由池对象
     */
    public static MeteoraLegs.DammV2 fromDexAccountData(
            PublicKey poolAddress, DexAccountData state, PublicKey referralTokenAccount,
            boolean includeRateLimiterSysvar) {
        return new MeteoraLegs.DammV2(
                poolAddress, state.publicKey("token_a_mint"), state.publicKey("token_b_mint"),
                state.publicKey("token_a_vault"), state.publicKey("token_b_vault"),
                tokenProgram(state.intValue("token_a_flag")),
                tokenProgram(state.intValue("token_b_flag")),
                referralTokenAccount, includeRateLimiterSysvar);
    }

    /**
     * 使用已经解析的 Pool 字段构造 DAMM v2 路由对象。
     * @param poolAddress 实际 Pool 地址
     * @param state 已解析的 Pool 账户
     * @param referralTokenAccount 可选转介费账户
     * @return DAMM v2 路由池对象
     */
    public static MeteoraLegs.DammV2 fromDexAccountData(
            PublicKey poolAddress, DexAccountData state, PublicKey referralTokenAccount) {
        //base_fee -> {Collections$UnmodifiableMap@3996}  size = 2
        @SuppressWarnings("unchecked") Map<String, Object> poolFees = (Map<String, Object>) state.get("pool_fees");

        @SuppressWarnings("unchecked") Map<String, Object> baseFee = (Map<String, Object>) poolFees.get("base_fee");

        @SuppressWarnings("unchecked") Map<String, Object> baseFeeInfo = (Map<String, Object>) baseFee.get("base_fee_info");
        byte[] data = (byte[]) baseFeeInfo.get("data");

        var includeRateLimiterSysvar =  includeRateLimiterSysvar(data);
        return new MeteoraLegs.DammV2(
                poolAddress, state.publicKey("token_a_mint"), state.publicKey("token_b_mint"),
                state.publicKey("token_a_vault"), state.publicKey("token_b_vault"),
                tokenProgram(state.intValue("token_a_flag")),
                tokenProgram(state.intValue("token_b_flag")),
                referralTokenAccount, includeRateLimiterSysvar);
    }

    /**
     * 判断 DAMM v2 是否需要 Instructions Sysvar。
     *
     * @param baseFeeInfo Pool.pool_fees.base_fee.base_fee_info
     * @return true = RateLimiter 模式，需要传 SYSVAR_INSTRUCTIONS_PUBKEY
     */
    public static boolean includeRateLimiterSysvar(byte[] baseFeeInfo) {

        if (baseFeeInfo == null || baseFeeInfo.length < 9) {
            throw new IllegalArgumentException("baseFeeInfo 长度至少 9 bytes");
        }

        int baseFeeMode =
                Byte.toUnsignedInt(baseFeeInfo[8]);

        return baseFeeMode == 2;
    }
}
