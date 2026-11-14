package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.SystemProgram;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/**
 * 构造 Raydium LaunchLab 精确输入 CPI，账户顺序与 Rust legs.rs 保持一致。
 */
public final class LaunchLabLegs {
    /** Raydium LaunchLab 程序地址，不是具体池状态账户。 */
    public static final PublicKey PROGRAM = key("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
    /** LaunchLab 交换指令使用的固定权限账户。 */
    private static final PublicKey AUTHORITY = key("WLHv2UAZm6z4KyaaELi5pjdbJh6RESMva1Rnn8pJVVh");
    /** LaunchLab 事件权限账户。 */
    private static final PublicKey EVENT_AUTHORITY = key("2DPAtwB8L12vrMRExbLuyGnC7n2J5LNoZQSejeQGpwkr");
    /** 精确输入买入指令的 Anchor 判别符。 */
    private static final byte[] BUY_EXACT_IN = {(byte) 250, (byte) 234, 13, 123, (byte) 213, (byte) 156, 19, (byte) 236};
    /** 精确输入卖出指令的 Anchor 判别符。 */
    private static final byte[] SELL_EXACT_IN = {(byte) 149, 39, (byte) 222, (byte) 155, (byte) 211, 124, (byte) 152, 26};

    /** 工具类不允许实例化。 */
    private LaunchLabLegs() {
    }

    /**
     * 这些地址须来自已核验且及时更新的 LaunchLab 池快照。
     * @param baseMint 目标代币 mint
     * @param quoteMint 用于支付或收取的 quote mint
     * @param baseTokenProgram base 侧代币程序
     * @param quoteTokenProgram quote 侧代币程序
     * @param poolState 具体 LaunchLab 池状态账户
     * @param baseVault base 侧池金库
     * @param quoteVault quote 侧池金库
     * @param platformConfig 平台配置账户
     * @param platformFeeVault 平台关联代币账户
     * @param creatorFeeVault 创建者关联代币账户
     * @param globalConfig 全局配置账户
     */
    public record Pool(PublicKey baseMint,
                       PublicKey quoteMint,
                       PublicKey baseTokenProgram,
                       PublicKey quoteTokenProgram,
                       PublicKey poolState,
                       PublicKey baseVault,
                       PublicKey quoteVault,
                       PublicKey platformConfig,
                       PublicKey platformFeeVault,
                       PublicKey creatorFeeVault,
                       PublicKey globalConfig) {
        /** 校验两个 mint、代币程序和必需池账户。 */
        public Pool {
            Objects.requireNonNull(baseMint, "baseMint");
            Objects.requireNonNull(quoteMint, "quoteMint");
            if (baseMint.equals(quoteMint)) throw new IllegalArgumentException("LaunchLab mints are equal");
            tokenProgram(baseTokenProgram);
            tokenProgram(quoteTokenProgram);
            Objects.requireNonNull(poolState, "poolState");
            Objects.requireNonNull(baseVault, "baseVault");
            Objects.requireNonNull(quoteVault, "quoteVault");
            Objects.requireNonNull(platformConfig, "platformConfig");
            Objects.requireNonNull(platformFeeVault, "platformAssociatedAccount");
            Objects.requireNonNull(creatorFeeVault, "creatorAssociatedAccount");
            Objects.requireNonNull(globalConfig, "globalConfig");
        }
    }

    /**
     * 用 quote 买入 base，输入量和最低输出量均为代币最小单位。
     * @param pool 已解析的 LaunchLab 池
     * @param user 交易用户公钥
     * @param amountIn 精确支付的 quote 数量
     * @param minOut 最低收到的 base 数量
     * @return LaunchLab 买入一跳
     */
    public static RouterLeg buyExactIn(Pool pool, PublicKey user, long amountIn, long minOut) {
        return exactIn(pool, user, amountIn, minOut, true);
    }

    /**
     * 卖出 base 换取 quote，输入量和最低输出量均为代币最小单位。
     * @param pool 已解析的 LaunchLab 池
     * @param user 交易用户公钥
     * @param amountIn 精确卖出的 base 数量
     * @param minOut 最低收到的 quote 数量
     * @return LaunchLab 卖出一跳
     */
    public static RouterLeg sellExactIn(Pool pool, PublicKey user, long amountIn, long minOut) {
        return exactIn(pool, user, amountIn, minOut, false);
    }

    /**
     * 用 quote 买入 base 的易读入口。
     * @param pool 已解析的 LaunchLab 池
     * @param user 交易用户公钥
     * @param quoteIn 精确支付的 quote 数量
     * @param minBaseOut 最低收到的 base 数量
     * @return LaunchLab 买入一跳
     */
    public static RouterLeg buy(Pool pool, PublicKey user, long quoteIn, long minBaseOut) {
        return buyExactIn(pool, user, quoteIn, minBaseOut);
    }

    /**
     * 卖出 base 换 quote 的易读入口。
     * @param pool 已解析的 LaunchLab 池
     * @param user 交易用户公钥
     * @param baseIn 精确卖出的 base 数量
     * @param minQuoteOut 最低收到的 quote 数量
     * @return LaunchLab 卖出一跳
     */
    public static RouterLeg sell(Pool pool, PublicKey user, long baseIn, long minQuoteOut) {
        return sellExactIn(pool, user, baseIn, minQuoteOut);
    }

    /**
     * 按买卖方向写入判别符与账户；分享费率固定为零。
     * @param pool 已解析的 LaunchLab 池
     * @param user 交易用户公钥
     * @param amountIn 精确输入数量
     * @param minOut 最低输出数量
     * @param buy 为真时买入 base，为假时卖出 base
     * @return LaunchLab 的 Router 一跳
     */
    private static RouterLeg exactIn(Pool pool, PublicKey user, long amountIn, long minOut, boolean buy) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(user, "user");
        positive(amountIn, "amountIn");
        positive(minOut, "minOut");
        ByteArrayOutputStream data = new ByteArrayOutputStream(32);
        data.writeBytes(buy ? BUY_EXACT_IN : SELL_EXACT_IN);
        putU64(data, amountIn);
        putU64(data, minOut);
        putU64(data, 0); // 本构造不启用 LaunchLab 的分享费率。

        List<AccountMeta> accounts = List.of(
                writableSigner(user),
                readonly(AUTHORITY),
                readonly(pool.globalConfig()),
                readonly(pool.platformConfig()),
                writable(pool.poolState()),
                writable(ata(user, pool.baseMint(),
                        pool.baseTokenProgram())),
                writable(ata(user, pool.quoteMint(),
                        pool.quoteTokenProgram())),
                writable(pool.baseVault()),
                writable(pool.quoteVault()),
                readonly(pool.baseMint()),
                readonly(pool.quoteMint()),
                readonly(pool.baseTokenProgram()),
                readonly(pool.quoteTokenProgram()),
                readonly(EVENT_AUTHORITY),
                readonly(PROGRAM),
                readonly(SystemProgram.PROGRAM_ID),
                writable(pool.platformFeeVault()),
                writable(pool.creatorFeeVault()));
        return new RouterLeg(PROGRAM, accounts, data.toByteArray(), buy ? pool.quoteMint() : pool.baseMint(), buy ? pool.baseMint() : pool.quoteMint(), amountIn, minOut);
    }
}
