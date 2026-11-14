package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.SystemProgram;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/** 构造 Meteora DAMM v2 与 DLMM 精确输入 CPI；不适用于 DAMM v1 或 DBC。 */
public final class MeteoraLegs {
    /** Meteora DAMM v2 程序地址，不是具体池地址。 */
    public static final PublicKey DAMM_V2 = key("cpamdpZCGKUy5JxQXB4dcpGPiikHawvSWAd6mEn1sGG");
    /** Meteora DLMM 程序地址，不是具体 lbPair 地址。 */
    public static final PublicKey DLMM = key("LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo");
    /** DAMM v2 指令所需的固定权限账户。 */
    private static final PublicKey DAMM_V2_AUTHORITY = key("HLnpSz9h2S4hiLQ43rnSD9XkcUThA7B8hQMKmDaiTLcC");
    /** DLMM 事件权限账户。 */
    private static final PublicKey DLMM_EVENT_AUTHORITY = key("D1ZN9Wj1fRSUQfCjhvnu1hqDMT7hzjzBBpi12nVniYD6");
    /** DLMM 指令所需的 Memo 程序地址。 */
    private static final PublicKey MEMO = key("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
    /** DAMM v2 限流配置可选使用的指令 sysvar 地址。 */
    private static final PublicKey INSTRUCTIONS_SYSVAR = key("Sysvar1nstructions1111111111111111111111111");
    /** 两类 Meteora 精确输入交换共用的指令判别符。 */
    private static final byte[] SWAP2 = {65, 75, 63, 76, (byte)235, 91, 91, (byte)136};

    /** 工具类不允许实例化。 */
    private MeteoraLegs() { }

    /**
     * DAMM v2 池快照；可选转介账户和限流 sysvar 必须符合目标池要求。
     * @param pool 具体 DAMM v2 池账户
     * @param mintA A 侧代币 mint
     * @param mintB B 侧代币 mint
     * @param vaultA A 侧池金库
     * @param vaultB B 侧池金库
     * @param tokenProgramA A 侧代币程序
     * @param tokenProgramB B 侧代币程序
     * @param referralTokenAccount 可选的转介费用代币账户
     * @param includeRateLimiterSysvar 是否附加限流所需的指令 sysvar
     */
    public record DammV2(PublicKey pool, PublicKey mintA, PublicKey mintB,
                         PublicKey vaultA, PublicKey vaultB, PublicKey tokenProgramA,
                         PublicKey tokenProgramB, PublicKey referralTokenAccount,
                         boolean includeRateLimiterSysvar) { }

    /**
     * DLMM 池快照；bin 数组应按当前价格位置和交易方向提供。
     * @param lbPair 具体 DLMM 池账户
     * @param reserveX X 侧池储备账户
     * @param reserveY Y 侧池储备账户
     * @param mintX X 侧代币 mint
     * @param mintY Y 侧代币 mint
     * @param tokenProgramX X 侧代币程序
     * @param tokenProgramY Y 侧代币程序
     * @param oracle 池预言机账户
     * @param bitmapExtension 可选的 bin 位图扩展账户
     * @param binArrays 当前兑换路径所需的有序 bin 数组
     */
    public record Dlmm(PublicKey lbPair, PublicKey reserveX, PublicKey reserveY,
                       PublicKey mintX, PublicKey mintY, PublicKey tokenProgramX,
                       PublicKey tokenProgramY, PublicKey oracle,
                       PublicKey bitmapExtension, List<PublicKey> binArrays) {
        /** 拷贝 bin 数组列表，防止池快照被外部修改。 */
        public Dlmm { binArrays = List.copyOf(binArrays); }
    }

    /**
     * 构造 DAMM v2 精确输入一跳，并根据输入 mint 选择对应 Token Program。
     * @param pool 已解析的 DAMM v2 池账户
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return DAMM v2 的 Router 一跳
     */
    public static RouterLeg dammV2(DammV2 pool, PublicKey user, PublicKey inputMint,
                                    long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        PublicKey outputMint = RaydiumLegs.other(inputMint, pool.mintA(), pool.mintB());
        boolean aIn = inputMint.equals(pool.mintA());
        PublicKey inputProgram = aIn ? pool.tokenProgramA() : pool.tokenProgramB();
        PublicKey outputProgram = aIn ? pool.tokenProgramB() : pool.tokenProgramA();
        tokenProgram(inputProgram); tokenProgram(outputProgram);
        ByteArrayOutputStream data = RaydiumLegs.discriminatorAndAmounts(SWAP2, amountIn, minOut);
        data.write(0); // 精确输入模式；避免部分成交破坏 Router 的输入金额校验。
        PublicKey eventAuthority = pda(DAMM_V2,
                "__event_authority".getBytes(StandardCharsets.US_ASCII));
        List<AccountMeta> accounts = new ArrayList<>(15);
        accounts.addAll(List.of(
                readonly(DAMM_V2_AUTHORITY), writable(pool.pool()),
                writable(ata(user, inputMint, inputProgram)),
                writable(ata(user, outputMint, outputProgram)),
                writable(pool.vaultA()), writable(pool.vaultB()),
                readonly(pool.mintA()), readonly(pool.mintB()), readonlySigner(user),
                readonly(pool.tokenProgramA()), readonly(pool.tokenProgramB())
        ));
        PublicKey referral = pool.referralTokenAccount();
        accounts.add(referral == null || referral.equals(SystemProgram.PROGRAM_ID) || referral.equals(DAMM_V2)
                ? readonly(DAMM_V2) : writable(referral));
        accounts.add(readonly(eventAuthority));
        accounts.add(readonly(DAMM_V2));
        if (pool.includeRateLimiterSysvar()) accounts.add(readonly(INSTRUCTIONS_SYSVAR));
        return new RouterLeg(DAMM_V2, accounts, data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * 构造 DLMM 精确输入一跳，并把可选 bitmap 与 bin 数组按顺序追加。
     * @param pool 已解析的 DLMM 池账户和 bin 数组
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return DLMM 的 Router 一跳
     */
    public static RouterLeg dlmm(Dlmm pool, PublicKey user, PublicKey inputMint,
                                  long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        //if (pool.binArrays().isEmpty()) throw new IllegalArgumentException("DLMM bin arrays missing");
        PublicKey outputMint = RaydiumLegs.other(inputMint, pool.mintX(), pool.mintY());
        boolean xIn = inputMint.equals(pool.mintX());
        PublicKey inputProgram = xIn ? pool.tokenProgramX() : pool.tokenProgramY();
        PublicKey outputProgram = xIn ? pool.tokenProgramY() : pool.tokenProgramX();
        tokenProgram(inputProgram); tokenProgram(outputProgram);
        ByteArrayOutputStream data = RaydiumLegs.discriminatorAndAmounts(SWAP2, amountIn, minOut);
        for (int i = 0; i < 4; i++) data.write(0); // remaining_accounts_info 使用空向量。
        List<AccountMeta> accounts = new ArrayList<>(16 + pool.binArrays().size());
        accounts.addAll(List.of(
                writable(pool.lbPair()),
                pool.bitmapExtension() == null ? readonly(DLMM) : writable(pool.bitmapExtension()),
                writable(pool.reserveX()), writable(pool.reserveY()),
                writable(ata(user, inputMint, inputProgram)),
                writable(ata(user, outputMint, outputProgram)),
                readonly(pool.mintX()), readonly(pool.mintY()), writable(pool.oracle()),
                readonly(DLMM), readonlySigner(user), readonly(pool.tokenProgramX()),
                readonly(pool.tokenProgramY()), readonly(MEMO),
                readonly(DLMM_EVENT_AUTHORITY), readonly(DLMM)
        ));
        for (PublicKey bin : pool.binArrays()) accounts.add(writable(bin));
        return new RouterLeg(DLMM, accounts, data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * DAMM v2 买入：约定 mintB 为支付侧、mintA 为收到侧。
     * @param pool 含 mintA 和 mintB 的 DAMM v2 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 mintB 的精确数量
     * @param minBaseOut 最低收到 mintA 数量
     * @return DAMM v2 买入一跳
     */
    public static RouterLeg buyDammV2(DammV2 pool, PublicKey user, long quoteIn, long minBaseOut) {
        return dammV2(pool, user, pool.mintB(), quoteIn, minBaseOut);
    }

    /**
     * DAMM v2 卖出：约定 mintA 为卖出侧、mintB 为收到侧。
     * @param pool 含 mintA 和 mintB 的 DAMM v2 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 mintA 的精确数量
     * @param minQuoteOut 最低收到 mintB 数量
     * @return DAMM v2 卖出一跳
     */
    public static RouterLeg sellDammV2(DammV2 pool, PublicKey user, long baseIn, long minQuoteOut) {
        return dammV2(pool, user, pool.mintA(), baseIn, minQuoteOut);
    }

    /**
     * DLMM 买入：约定 mintY 为支付侧、mintX 为收到侧。
     * @param pool 含 mintX、mintY 及所需 bin 数组的 DLMM 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 mintY 的精确数量
     * @param minBaseOut 最低收到 mintX 数量
     * @return DLMM 买入一跳
     */
    public static RouterLeg buyDlmm(Dlmm pool, PublicKey user, long quoteIn, long minBaseOut) {
        return dlmm(pool, user, pool.mintY(), quoteIn, minBaseOut);
    }

    /**
     * DLMM 卖出：约定 mintX 为卖出侧、mintY 为收到侧。
     * @param pool 含 mintX、mintY 及所需 bin 数组的 DLMM 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 mintX 的精确数量
     * @param minQuoteOut 最低收到 mintY 数量
     * @return DLMM 卖出一跳
     */
    public static RouterLeg sellDlmm(Dlmm pool, PublicKey user, long baseIn, long minQuoteOut) {
        return dlmm(pool, user, pool.mintX(), baseIn, minQuoteOut);
    }
}
