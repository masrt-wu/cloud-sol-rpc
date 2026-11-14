package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/** 构造 PumpSwap AMM 的买卖 CPI；这里不是 Pump.fun 未毕业 bonding curve。 */
public final class PumpAmmLegs {
    /** PumpSwap AMM 程序地址，不是具体池地址。 */
    public static final PublicKey PROGRAM = key("pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA");
    /** PumpSwap 的全局配置账户。 */
    private static final PublicKey GLOBAL = key("ADyA8hdefvWN2dbGGWFotbzWxrAvLW83WG6QCVXvJKqw");
    /** PumpSwap 事件权限账户。 */
    private static final PublicKey EVENT_AUTHORITY = key("GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR");
    /** PumpSwap 全局交易量记录账户。 */
    private static final PublicKey GLOBAL_VOLUME = key("C2aFPdENg4A2HQsmrd5rTw5TaYBX5Ku887cWjbFKtZpw");
    /** PumpSwap 的费用配置账户。 */
    private static final PublicKey FEE_CONFIG = key("5PHirr8joyTMp9JMm6nW7hNDVyEYdkzDqazxPD7RaTjx");
    /** PumpSwap 的费用程序地址。 */
    private static final PublicKey FEE_PROGRAM = key("pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ");
    /** 以固定 quote 输入买入 base 的指令判别符。 */
    private static final byte[] BUY_EXACT_QUOTE_IN = {(byte)198, 46, 21, 82,
            (byte)180, (byte)217, (byte)232, 112};
    /** 卖出 base 的指令判别符。 */
    private static final byte[] SELL = {51, (byte)230, (byte)133, (byte)164,
            1, 127, (byte)131, (byte)173};

    /** 工具类不允许实例化。 */
    private PumpAmmLegs() { }

    /**
     * PumpSwap 池快照；费用接收方、创建者与返现相关账户须由调用方核实。
     * @param pool 具体 PumpSwap 池账户
     * @param baseMint base 侧代币 mint
     * @param quoteMint quote 侧代币 mint
     * @param baseVault base 侧池金库
     * @param quoteVault quote 侧池金库
     * @param baseTokenProgram base 侧代币程序
     * @param quoteTokenProgram quote 侧代币程序
     * @param protocolFeeRecipient 协议费用接收账户
     * @param coinCreatorVaultAta 创建者金库的关联代币账户
     * @param coinCreatorVaultAuthority 创建者金库权限账户
     * @param buybackFeeRecipient 回购费用接收账户
     * @param coinCreator 可选的代币创建者账户
     * @param cashbackCoin 是否启用返现相关账户
     */
    public record  Pool(PublicKey pool, PublicKey baseMint, PublicKey quoteMint,
                       PublicKey baseVault, PublicKey quoteVault,
                       PublicKey baseTokenProgram, PublicKey quoteTokenProgram,
                       PublicKey protocolFeeRecipient, PublicKey coinCreatorVaultAta,
                       PublicKey coinCreatorVaultAuthority, PublicKey buybackFeeRecipient,
                       PublicKey coinCreator, boolean cashbackCoin) { }

    /**
     * 使用固定 quote 输入量买入 base，并设置最低 base 输出。
     * @param pool 已解析的 PumpSwap AMM 池
     * @param user 交易用户公钥
     * @param quoteIn 精确支付的 quote 数量
     * @param minBaseOut 最低收到的 base 数量
     * @return PumpSwap 买入一跳
     */
    public static RouterLeg buy(Pool pool, PublicKey user, long quoteIn, long minBaseOut) {
        Objects.requireNonNull(pool, "pool");
        positive(quoteIn, "quoteIn"); positive(minBaseOut, "minBaseOut");
        ByteArrayOutputStream data = RaydiumLegs.discriminatorAndAmounts(
                BUY_EXACT_QUOTE_IN, quoteIn, minBaseOut);
        data.write(1); // 启用交易量记录。
        return new RouterLeg(PROGRAM, accounts(pool, user, true), data.toByteArray(),
                pool.quoteMint(), pool.baseMint(), quoteIn, minBaseOut);
    }

    /**
     * 卖出固定 base 数量，要求至少收到指定 quote 数量。
     * @param pool 已解析的 PumpSwap AMM 池
     * @param user 交易用户公钥
     * @param baseIn 精确卖出的 base 数量
     * @param minQuoteOut 最低收到的 quote 数量
     * @return PumpSwap 卖出一跳
     */
    public static RouterLeg sell(Pool pool, PublicKey user, long baseIn, long minQuoteOut) {
        Objects.requireNonNull(pool, "pool");
        positive(baseIn, "baseIn"); positive(minQuoteOut, "minQuoteOut");
        ByteArrayOutputStream data = RaydiumLegs.discriminatorAndAmounts(SELL, baseIn, minQuoteOut);
        return new RouterLeg(PROGRAM, accounts(pool, user, false), data.toByteArray(),
                pool.baseMint(), pool.quoteMint(), baseIn, minQuoteOut);
    }

    /**
     * 按买卖方向拼装 PumpSwap 所需账户及可选返现、创建者账户。
     * @param pool 已解析的 PumpSwap AMM 池
     * @param user 交易用户公钥
     * @param buy 为真时执行 quote 到 base 的买入路径
     * @return 按 PumpSwap CPI 顺序排列的账户列表
     */
    private static List<AccountMeta> accounts(Pool pool, PublicKey user, boolean buy) {
        tokenProgram(pool.baseTokenProgram()); tokenProgram(pool.quoteTokenProgram());
        Objects.requireNonNull(pool.protocolFeeRecipient(), "protocolFeeRecipient");
        Objects.requireNonNull(pool.buybackFeeRecipient(), "buybackFeeRecipient");
        PublicKey userVolume = pda(PROGRAM,
                "user_volume_accumulator".getBytes(StandardCharsets.US_ASCII), user.toByteArray());
        List<AccountMeta> accounts = new ArrayList<>(28);
        accounts.addAll(List.of(
                writable(pool.pool()), writableSigner(user), readonly(GLOBAL),
                readonly(pool.baseMint()), readonly(pool.quoteMint()),
                writable(ata(user, pool.baseMint(), pool.baseTokenProgram())),
                writable(ata(user, pool.quoteMint(), pool.quoteTokenProgram())),
                writable(pool.baseVault()), writable(pool.quoteVault()),
                readonly(pool.protocolFeeRecipient()),
                // 手续费以池的 quote 结算，不能套用仅适用于 WSOL 的固定地址映射。
                writable(ata(pool.protocolFeeRecipient(), pool.quoteMint(), pool.quoteTokenProgram())),
                readonly(pool.baseTokenProgram()), readonly(pool.quoteTokenProgram()),
                readonly(SystemProgram.PROGRAM_ID), readonly(AssociatedTokenProgram.PROGRAM_ID),
                readonly(EVENT_AUTHORITY), readonly(PROGRAM),
                writable(pool.coinCreatorVaultAta()), readonly(pool.coinCreatorVaultAuthority())
        ));
        if (buy) {
            accounts.add(readonly(GLOBAL_VOLUME));
            accounts.add(writable(userVolume));
        }
        accounts.add(readonly(FEE_CONFIG));
        accounts.add(readonly(FEE_PROGRAM));
        if (pool.cashbackCoin()) {
            accounts.add(writable(ata(userVolume, pool.quoteMint(), pool.quoteTokenProgram())));
            if (!buy) accounts.add(writable(userVolume));
        }
        if (pool.coinCreator() != null && !pool.coinCreator().equals(SystemProgram.PROGRAM_ID)) {
            PublicKey poolV2 = pda(PROGRAM,
                    "pool-v2".getBytes(StandardCharsets.US_ASCII), pool.baseMint().toByteArray());
            accounts.add(readonly(poolV2));
        }
        accounts.add(readonly(pool.buybackFeeRecipient()));
        accounts.add(writable(ata(pool.buybackFeeRecipient(), pool.quoteMint(), pool.quoteTokenProgram())));
        return accounts;
    }
}
