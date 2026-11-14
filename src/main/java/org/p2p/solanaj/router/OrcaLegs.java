package org.p2p.solanaj.router;

import org.p2p.solanaj.core.PublicKey;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/** 构造 Orca Whirlpool 的精确输入 swap_v2，三个 tick 数组须由调用方解析。 */
public final class OrcaLegs {
    /** Orca Whirlpool 程序地址，不是具体 Whirlpool 池账户。 */
    public static final PublicKey WHIRLPOOL = key("whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc");
    /** swap_v2 指令所需的 Memo 程序地址。 */
    private static final PublicKey MEMO = key("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
    /** Whirlpool swap_v2 指令的 Anchor 判别符。 */
    private static final byte[] SWAP_V2 = {43, 4, (byte)237, 11, 26, (byte)201, 30, 98};
    /** 从 A 到 B 兑换时使用的价格下界。 */
    private static final BigInteger MIN_SQRT = new BigInteger("4295048016");
    /** 从 B 到 A 兑换时使用的价格上界。 */
    private static final BigInteger MAX_SQRT = new BigInteger("79226673515401279992447579055");

    /** 工具类不允许实例化。 */
    private OrcaLegs() { }

    /**
     * Whirlpool 池快照；tick 数组必须与当前价格及交换方向匹配。
     * @param whirlpool 具体 Whirlpool 池账户
     * @param mintA A 侧代币 mint
     * @param mintB B 侧代币 mint
     * @param vaultA A 侧池金库
     * @param vaultB B 侧池金库
     * @param tokenProgramA A 侧代币程序
     * @param tokenProgramB B 侧代币程序
     * @param tickArrays 按 CPI 顺序排列的 tick 数组
     */
    public record Pool(PublicKey whirlpool, PublicKey mintA, PublicKey mintB,
                       PublicKey vaultA, PublicKey vaultB, PublicKey tokenProgramA,
                       PublicKey tokenProgramB, List<PublicKey> tickArrays) {
        /** 拷贝 tick 数组列表，防止池快照被外部修改。 */
        public Pool { tickArrays = List.copyOf(tickArrays); }
    }

    /**
     * 按输入 mint 推导兑换方向，并构造完整的 Whirlpool CPI 账户顺序。
     * @param pool 已解析的 Whirlpool 池及三个 tick 数组
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return Whirlpool 的 Router 一跳
     */
    public static RouterLeg swap(Pool pool, PublicKey user, PublicKey inputMint,
                                 long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        if (pool.tickArrays().size() < 3) throw new IllegalArgumentException("Whirlpool needs 3 tick arrays");
        PublicKey outputMint = RaydiumLegs.other(inputMint, pool.mintA(), pool.mintB());
        tokenProgram(pool.tokenProgramA()); tokenProgram(pool.tokenProgramB());
        boolean aToB = inputMint.equals(pool.mintA());
        ByteArrayOutputStream data = RaydiumLegs.discriminatorAndAmounts(SWAP_V2, amountIn, minOut);
        RaydiumLegs.putU128(data, aToB ? MIN_SQRT : MAX_SQRT);
        data.write(1); // amount_specified_is_input=true，金额表示输入量。
        data.write(aToB ? 1 : 0);
        data.write(0); // 不附加额外账户；当前构造不支持 transfer-hook 扩展账户。
        PublicKey oracle = pda(WHIRLPOOL, "oracle".getBytes(StandardCharsets.US_ASCII),
                pool.whirlpool().toByteArray());
        return new RouterLeg(WHIRLPOOL, List.of(
                readonly(pool.tokenProgramA()), readonly(pool.tokenProgramB()), readonly(MEMO),
                readonlySigner(user), writable(pool.whirlpool()), readonly(pool.mintA()),
                readonly(pool.mintB()), writable(ata(user, pool.mintA(), pool.tokenProgramA())),
                writable(pool.vaultA()), writable(ata(user, pool.mintB(), pool.tokenProgramB())),
                writable(pool.vaultB()), writable(pool.tickArrays().get(0)),
                writable(pool.tickArrays().get(1)), writable(pool.tickArrays().get(2)),
                writable(oracle)
        ), data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * Whirlpool 买入：约定 mintB 为支付侧、mintA 为收到侧。
     * @param pool 含 mintA、mintB 及当前 tick 数组的 Whirlpool 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 mintB 的精确数量
     * @param minBaseOut 最低收到 mintA 数量
     * @return Whirlpool 买入一跳
     */
    public static RouterLeg buy(Pool pool, PublicKey user, long quoteIn, long minBaseOut) {
        return swap(pool, user, pool.mintB(), quoteIn, minBaseOut);
    }

    /**
     * Whirlpool 卖出：约定 mintA 为卖出侧、mintB 为收到侧。
     * @param pool 含 mintA、mintB 及当前 tick 数组的 Whirlpool 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 mintA 的精确数量
     * @param minQuoteOut 最低收到 mintB 数量
     * @return Whirlpool 卖出一跳
     */
    public static RouterLeg sell(Pool pool, PublicKey user, long baseIn, long minQuoteOut) {
        return swap(pool, user, pool.mintA(), baseIn, minQuoteOut);
    }
}
