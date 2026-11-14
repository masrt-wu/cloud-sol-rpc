package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/**
 * Pump.fun 未毕业内盘 V2 的 SPL quote 买卖构造器，对应 Rust 的两个 V2 精确输入方法。
 * 仅本地构造，不查询 RPC、不报价、不创建交易、不提交；不是 PumpSwap 外盘。
 * 原生 SOL 配对内盘使用不同结算方式，不支持通过本类当作 WSOL ATA 路由。
 */
public final class PumpFunLegs {
    /** Pump.fun 内盘程序地址，不是具体 bonding curve 地址。 */
    public static final PublicKey PROGRAM = key("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
    /** 内盘全局配置账户。 */
    private static final PublicKey GLOBAL = key("4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf");
    /** 内盘事件权限账户。 */
    private static final PublicKey EVENT_AUTHORITY = key("Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1");
    /** 内盘全局交易量账户，买入时只读传入。 */
    private static final PublicKey GLOBAL_VOLUME = key("Hq2wp8uJ9jCPsYgNHex8RtqdvMPfVGoYwjvF1ATiwn2Y");
    /** 内盘费用配置账户。 */
    private static final PublicKey FEE_CONFIG = key("8Wf5TiAheLUqBrKXeYg2JtAFFMWtKdG2BSFgqUcPVwTt");
    /** 费用及共享配置所属程序。 */
    private static final PublicKey FEE_PROGRAM = key("pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ");
    /** 与 Rust 默认值一致的回购接收方；调用方应优先传入当前配置中的真实值。 */
    private static final PublicKey DEFAULT_BUYBACK = key("5YxQFdt3Tr9zJLvkFccqXVUwhdTWJQc1fFg2YPbxvxeD");
    /** 固定 quote 输入的 V2 买入判别符。 */
    private static final byte[] BUY_EXACT_QUOTE_IN_V2 = {(byte) 194, (byte) 171, 28, 70, 104, 77, 91, 47};
    /** 固定 base 输入的 V2 卖出判别符。 */
    private static final byte[] SELL_V2 = {93, (byte) 246, (byte) 130, 60, (byte) 231, (byte) 233, 64, (byte) 178};
    /** 用户交易量 PDA 的固定种子，避免每次构造重复编码。 */
    private static final byte[] USER_VOLUME_SEED = "user_volume_accumulator".getBytes(StandardCharsets.US_ASCII);
    /** 费用共享配置 PDA 的固定种子。 */
    private static final byte[] SHARING_CONFIG_SEED = "sharing-config".getBytes(StandardCharsets.US_ASCII);

    /** 工具类不允许实例化。 */
    private PumpFunLegs() { }

    /**
     * 内盘 V2 池快照；两个 Token Program 必须使用各自 mint 的真实 owner，不能按买卖方向猜测。
     * @param baseMint 待买卖的内盘代币 mint
     * @param quoteMint 支付或收回的 SPL quote mint，不接受 WSOL
     * @param baseTokenProgram 内盘 base mint 所属的经典 Token 或 Token-2022 程序
     * @param quoteTokenProgram quote mint 所属的经典 Token 或 Token-2022 程序
     * @param bondingCurve 内盘曲线账户，所属程序为内盘程序
     * @param associatedBaseBondingCurve 曲线持有 base 的代币账户，使用链上已解析地址
     * @param creatorVault 实际创建者费用金库权限账户，可能为共享金库，不从 creator 猜测
     * @param feeRecipient 当前配置选定的协议费用接收方，不是其 quote ATA
     * @param buybackFeeRecipient 当前回购费用接收方；空值或全零公钥使用 Rust 默认值
     */
    public record Pool(PublicKey baseMint, PublicKey quoteMint,
                       PublicKey baseTokenProgram, PublicKey quoteTokenProgram,
                       PublicKey bondingCurve, PublicKey associatedBaseBondingCurve,
                       PublicKey creatorVault, PublicKey feeRecipient,
                       PublicKey buybackFeeRecipient) { }

    /**
     * 以固定 quote 数量买入内盘 base，编码 buy_exact_quote_in_v2。
     * 动态后续跳的 quoteIn 填 1，实际支付量由新版链上 Router 替换为上一跳净新增量。
     * @param pool 已解析的非 WSOL quote 内盘快照
     * @param user 本笔交易实际签名用户
     * @param quoteIn quote 最小单位输入量；动态后续跳填 1
     * @param minBaseOut base 最小单位最低净到账量
     * @return 可用于单跳、动态两跳或动态三跳的内盘买入指令
     */
    public static RouterLeg buy(Pool pool, PublicKey user, long quoteIn, long minBaseOut) {
        positive(quoteIn, "quoteIn"); positive(minBaseOut, "minBaseOut");
        return new RouterLeg(PROGRAM, accounts(pool, user, true),
                RaydiumLegs.discriminatorAndAmounts(BUY_EXACT_QUOTE_IN_V2, quoteIn, minBaseOut).toByteArray(),
                pool.quoteMint(), pool.baseMint(), quoteIn, minBaseOut);
    }

    /**
     * 以固定 base 数量卖出内盘币，编码 sell_v2；输出 quote 留在用户 quote ATA。
     * @param pool 已解析的非 WSOL quote 内盘快照
     * @param user 本笔交易实际签名用户
     * @param baseIn base 最小单位输入量；动态后续跳填 1
     * @param minQuoteOut quote 最小单位最低净到账量
     * @return 可用于单跳、动态两跳或动态三跳的内盘卖出指令
     */
    public static RouterLeg sell(Pool pool, PublicKey user, long baseIn, long minQuoteOut) {
        positive(baseIn, "baseIn"); positive(minQuoteOut, "minQuoteOut");
        return new RouterLeg(PROGRAM, accounts(pool, user, false),
                RaydiumLegs.discriminatorAndAmounts(SELL_V2, baseIn, minQuoteOut).toByteArray(),
                pool.baseMint(), pool.quoteMint(), baseIn, minQuoteOut);
    }

    /**
     * 按 Rust V2 顺序构造账户；买入为 27 个，卖出为 26 个，卖出不含全局交易量账户。
     * @param pool 内盘快照，所有未知 quote 必须显式提供真实 Token Program
     * @param user 实际签名用户，用户交易量 PDA 必须从这个公钥推导
     * @param buy 是否构造买入方向
     * @return 按 CPI 顺序排列的账户列表
     */
    private static List<AccountMeta> accounts(Pool pool, PublicKey user, boolean buy) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(user, "user");
        tokenProgram(pool.baseTokenProgram()); tokenProgram(pool.quoteTokenProgram());
        if (WSOL_MINT.equals(pool.quoteMint()))
            throw new IllegalArgumentException("Pump 内盘 V2 原生 SOL 配对不能作为 WSOL ATA 路由");
        PublicKey buyback = pool.buybackFeeRecipient();
        if (buyback == null || buyback.equals(SystemProgram.PROGRAM_ID)) buyback = DEFAULT_BUYBACK;
        PublicKey userVolume = pda(PROGRAM, USER_VOLUME_SEED, user.toByteArray());
        PublicKey sharingConfig = pda(FEE_PROGRAM, SHARING_CONFIG_SEED, pool.baseMint().toByteArray());
        List<AccountMeta> result = new ArrayList<>(buy ? 27 : 26);
        result.addAll(List.of(
                readonly(GLOBAL), readonly(pool.baseMint()), readonly(pool.quoteMint()),
                readonly(pool.baseTokenProgram()), readonly(pool.quoteTokenProgram()),
                readonly(AssociatedTokenProgram.PROGRAM_ID),
                writable(pool.feeRecipient()),
                writable(ata(pool.feeRecipient(), pool.quoteMint(), pool.quoteTokenProgram())),
                writable(buyback), writable(ata(buyback, pool.quoteMint(), pool.quoteTokenProgram())),
                writable(pool.bondingCurve()), writable(pool.associatedBaseBondingCurve()),
                writable(ata(pool.bondingCurve(), pool.quoteMint(), pool.quoteTokenProgram())),
                writableSigner(user),
                writable(ata(user, pool.baseMint(), pool.baseTokenProgram())),
                writable(ata(user, pool.quoteMint(), pool.quoteTokenProgram())),
                writable(pool.creatorVault()),
                writable(ata(pool.creatorVault(), pool.quoteMint(), pool.quoteTokenProgram())),
                readonly(sharingConfig)));
        if (buy) result.add(readonly(GLOBAL_VOLUME));
        result.addAll(List.of(writable(userVolume),
                writable(ata(userVolume, pool.quoteMint(), pool.quoteTokenProgram())),
                readonly(FEE_CONFIG), readonly(FEE_PROGRAM), readonly(SystemProgram.PROGRAM_ID),
                readonly(EVENT_AUTHORITY), readonly(PROGRAM)));
        return result;
    }
}
