package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.PumpDIumProgram;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.PumpAmmLegs;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;

/** PumpSwap Pool 原始数据直接转成 Router Pool；外部费用账户由调用方提供。 */
public final class PumpAmmPoolParser {
    private PumpAmmPoolParser() { }

    /**
     * 不重复校验 owner/判别符，只读取交易所需字段；不会读 JSON 或 RPC。
     * 旧 Pool 没有追加的 coinCreator/cashback 字段时按 null/false 处理。
     * @param poolAddress 实际 Pool 账户地址，不是 PumpSwap 程序地址
     * @param data 该 Pool 的原始账户字节
     * @param baseTokenProgram base mint 的实际 Token 程序
     * @param quoteTokenProgram quote mint 的实际 Token 程序
     * @param protocolFeeRecipient 当前全局配置选定的协议费接收方
     * @param coinCreatorVaultAta 创建者金库 ATA
     * @param coinCreatorVaultAuthority 创建者金库权限地址
     * @param buybackFeeRecipient 当前回购费接收方
     * @return 可直接传给 PumpAmmLegs.buy/sell 的池对象
     */
    public static PumpAmmLegs.Pool parse(
            PublicKey poolAddress, byte[] data,
            PublicKey baseTokenProgram, PublicKey quoteTokenProgram,
            PublicKey protocolFeeRecipient, PublicKey coinCreatorVaultAta,
            PublicKey coinCreatorVaultAuthority, PublicKey buybackFeeRecipient) {
        return new PumpAmmLegs.Pool(
                poolAddress, key(data, 43), key(data, 75), key(data, 139), key(data, 171),
                baseTokenProgram, quoteTokenProgram, protocolFeeRecipient,
                coinCreatorVaultAta, coinCreatorVaultAuthority, buybackFeeRecipient,
                data.length >= 243 ? key(data, 211) : null,
                data.length > 244 && data[244] != 0);
    }

    /**
     * 将已解析的 PumpSwap Pool 直接转换为路由对象，不重复解析账户字节。
     * @param poolAddress 实际 Pool 地址
     * @param state 已解析的 Pool 账户
     * @param baseTokenProgram base mint 所属 Token 程序
     * @param quoteTokenProgram quote mint 所属 Token 程序
     * @param protocolFeeRecipient 当前协议费接收方
     * @param coinCreatorVaultAta 创建者金库 ATA
     * @param coinCreatorVaultAuthority 创建者金库权限地址
     * @param buybackFeeRecipient 当前回购费接收方
     * @return PumpSwap 路由池对象
     */
    public static PumpAmmLegs.Pool fromDexAccountData(
            PublicKey poolAddress, DexAccountData state,
            PublicKey baseTokenProgram, PublicKey quoteTokenProgram,
            PublicKey protocolFeeRecipient, PublicKey coinCreatorVaultAta,
            PublicKey coinCreatorVaultAuthority, PublicKey buybackFeeRecipient) {
        return new PumpAmmLegs.Pool(
                poolAddress, state.publicKey("base_mint"), state.publicKey("quote_mint"),
                state.publicKey("pool_base_token_account"), state.publicKey("pool_quote_token_account"),
                baseTokenProgram, quoteTokenProgram, protocolFeeRecipient,
                coinCreatorVaultAta, coinCreatorVaultAuthority, buybackFeeRecipient,
                state.defaultedFields().contains("coin_creator") ? null : state.publicKey("coin_creator"),
                state.flag("is_cashback_coin"));
    }
    /**
     * 将已解析的 PumpSwap Pool 直接转换为路由对象，不重复解析账户字节。
     * @param poolAddress 实际 Pool 地址
     * @param state 已解析的 Pool 账户
     * @param baseTokenProgram base mint 所属 Token 程序
     * @param quoteTokenProgram quote mint 所属 Token 程序
     * @return PumpSwap 路由池对象
     */
    public static PumpAmmLegs.Pool fromDexAccountData(
            PublicKey poolAddress, DexAccountData state,
            PublicKey baseTokenProgram, PublicKey quoteTokenProgram) {
        Random random = new Random();
        String protocolFeeRecipient = PumpDIumProgram.PUMP_ADDRESS[random.nextInt(PumpDIumProgram.PUMP_ADDRESS.length-1)];
        var coinCreatorVaultAuthority = PublicKey.findProgramAddress(List.of("creator_vault".getBytes(StandardCharsets.UTF_8),
                state.publicKey("coin_creator").toByteArray()), PumpDIumProgram.ProgramId).getAddress();
        var coinCreatorVaultAta = PublicKey.findProgramAddress(
                List.of(
                        coinCreatorVaultAuthority.toByteArray(),
                        quoteTokenProgram.toByteArray(),
                        state.publicKey("quote_mint").toByteArray()
                ),
                AssociatedTokenProgram.PROGRAM_ID
        ).getAddress();
        String buybackFeeRecipient = PumpDIumProgram.BUY_FEC_ADDRESS[random.nextInt(PumpDIumProgram.BUY_FEC_ADDRESS.length-1)];

        return new PumpAmmLegs.Pool(
                poolAddress, state.publicKey("base_mint"), state.publicKey("quote_mint"),
                state.publicKey("pool_base_token_account"), state.publicKey("pool_quote_token_account"),
                baseTokenProgram, quoteTokenProgram, new PublicKey(protocolFeeRecipient),
                coinCreatorVaultAta, coinCreatorVaultAuthority, new PublicKey(buybackFeeRecipient),
                state.defaultedFields().contains("coin_creator") ? null : state.publicKey("coin_creator"),
                state.flag("is_cashback_coin"));
    }
}
