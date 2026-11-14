package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.RouterTradeBuilder.*;

/** 按 Rust SDK 的账户顺序构造 Raydium 精确输入 CPI；池参数由调用方提供。 */
public final class RaydiumLegs {
    /** Raydium AMM V4 程序地址，不是具体 AMM 池地址。 */
    public static final PublicKey AMM_V4 = key("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
    /** Raydium CPMM 程序地址，不是具体 CPMM 池地址。 */
    public static final PublicKey CPMM = key("CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C");
    /** Raydium CLMM 程序地址，不是具体 CLMM 池地址。 */
    public static final PublicKey CLMM = key("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");
    /** AMM V4 交换指令要求的固定权限账户。 */
    private static final PublicKey V4_AUTHORITY = key("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1");
    /** CPMM 交换指令要求的固定权限账户。 */
    private static final PublicKey CPMM_AUTHORITY = key("GpMZbSM2GgvTKHJirzeGfMFoaZ8UR2X7F4v8vHTvxFbL");
    /** CLMM swap_v2 指令所需的 Memo 程序地址。 */
    private static final PublicKey MEMO = key("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
    /** CPMM 精确输入交换指令的 Anchor 判别符。 */
    private static final byte[] CPMM_SWAP_BASE_IN = {(byte)143, (byte)190, 90, (byte)218,
            (byte)196, 30, 51, (byte)222};
    /** CLMM swap_v2 指令的 Anchor 判别符。 */
    private static final byte[] CLMM_SWAP_V2 = {43, 4, (byte)237, 11, 26, (byte)201, 30, 98};
    /** CLMM 从 token0 换 token1 时使用的价格下界。 */
    private static final BigInteger CLMM_MIN_SQRT = new BigInteger("4295048016");
    /** CLMM 从 token1 换 token0 时使用的价格上界。 */
    private static final BigInteger CLMM_MAX_SQRT = new BigInteger("79226673521066979257578248091");

    /** 工具类不允许实例化。 */
    private RaydiumLegs() { }

    /**
     * AMM V4 池快照。
     * @param amm 具体 AMM 池账户
     * @param coinMint coin 侧代币 mint
     * @param pcMint pc 侧代币 mint
     * @param coinVault coin 侧池金库
     * @param pcVault pc 侧池金库
     * @param tokenProgram 池使用的 SPL Token 程序
     *                     | 参数 | Raydium 字段 | 含义 |
     * |---|---|---|
     * | `amm` | Pool ID / AMM ID | 池主账户 |
     * | `coinMint` | `baseMint` / `coinMint` | Base 侧 Mint |
     * | `pcMint` | `quoteMint` / `pcMint` | Quote 侧 Mint |
     * | `coinVault` | `baseVault` / `coinVault` | Base 侧池金库 |
     * | `pcVault` | `quoteVault` / `pcVault` | Quote 侧池金库 |
     * | `tokenProgram` | SPL Token Program | Token 转账程序 |
     *
     */
    public record AmmV4(PublicKey amm, PublicKey coinMint, PublicKey pcMint,
                        PublicKey coinVault, PublicKey pcVault, PublicKey tokenProgram) { }

    /**
     * CPMM 池快照。
     * @param config 池配置账户
     * @param pool 具体 CPMM 池账户
     * @param baseMint base 侧代币 mint
     * @param quoteMint quote 侧代币 mint
     * @param baseVault base 侧池金库
     * @param quoteVault quote 侧池金库
     * @param baseTokenProgram base 侧代币程序
     * @param quoteTokenProgram quote 侧代币程序
     * @param observation 池观察状态账户
     */
    public record Cpmm(PublicKey config, PublicKey pool, PublicKey baseMint, PublicKey quoteMint,
                       PublicKey baseVault, PublicKey quoteVault, PublicKey baseTokenProgram,
                       PublicKey quoteTokenProgram, PublicKey observation) { }

    /**
     * CLMM 池快照；tick 数组须按本次价格方向提供当前可用的链上账户。
     * @param config 池配置账户
     * @param pool 具体 CLMM 池账户
     * @param token0Mint 第 0 侧代币 mint
     * @param token1Mint 第 1 侧代币 mint
     * @param token0Vault 第 0 侧池金库
     * @param token1Vault 第 1 侧池金库
     * @param token0Program 第 0 侧代币程序
     * @param token1Program 第 1 侧代币程序
     * @param observation 池观察状态账户
     * @param bitmapExtension 可选的 tick 位图扩展 PDA
     * @param tickArrays 当前交换所需的有序 tick 数组
     */
    public record Clmm(PublicKey config, PublicKey pool, PublicKey token0Mint, PublicKey token1Mint,
                       PublicKey token0Vault, PublicKey token1Vault, PublicKey token0Program,
                       PublicKey token1Program, PublicKey observation, PublicKey bitmapExtension,
                       List<PublicKey> tickArrays) {
        /** 拷贝 tick 数组列表，避免调用方后续修改池快照。 */
        public Clmm { tickArrays = List.copyOf(tickArrays); }
    }

    /**
     * 构造 AMM V4 的 SwapBaseInV2 一跳，不包含 OpenBook 账户。
     * @param pool 已解析的 AMM V4 池账户
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return AMM V4 的 Router 一跳
     */
    public static RouterLeg ammV4(AmmV4 pool, PublicKey user, PublicKey inputMint,
                                  long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        PublicKey outputMint = other(inputMint, pool.coinMint(), pool.pcMint());
        PublicKey tokenProgram = Objects.requireNonNull(pool.tokenProgram(), "tokenProgram");
        tokenProgram(tokenProgram);
        ByteArrayOutputStream data = new ByteArrayOutputStream(17);
        data.write(16); // SwapBaseInV2 指令标签；该版本不传 OpenBook 账户。
        putU64(data, amountIn);
        putU64(data, minOut);
        return new RouterLeg(AMM_V4, List.of(
                readonly(tokenProgram),
                writable(pool.amm()),
                readonly(V4_AUTHORITY),
                writable(pool.coinVault()),
                writable(pool.pcVault()),
                writable(ata(user, inputMint, tokenProgram)),
                writable(ata(user, outputMint, tokenProgram)), readonlySigner(user)
        ), data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * 根据输入 mint 自动选择 CPMM 的输入/输出 vault 与 Token Program。
     * @param pool 已解析的 CPMM 池账户
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return CPMM 的 Router 一跳
     */
    public static RouterLeg cpmm(Cpmm pool, PublicKey user, PublicKey inputMint,
                                 long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        PublicKey outputMint = other(inputMint, pool.baseMint(), pool.quoteMint());
        boolean baseIn = inputMint.equals(pool.baseMint());
        PublicKey inputVault = baseIn ? pool.baseVault() : pool.quoteVault();
        PublicKey outputVault = baseIn ? pool.quoteVault() : pool.baseVault();
        PublicKey inputProgram = baseIn ? pool.baseTokenProgram() : pool.quoteTokenProgram();
        PublicKey outputProgram = baseIn ? pool.quoteTokenProgram() : pool.baseTokenProgram();
        tokenProgram(inputProgram); tokenProgram(outputProgram);
        ByteArrayOutputStream data = discriminatorAndAmounts(CPMM_SWAP_BASE_IN, amountIn, minOut);
        return new RouterLeg(CPMM, List.of(
                readonlySigner(user), readonly(CPMM_AUTHORITY), readonly(pool.config()),
                writable(pool.pool()), writable(ata(user, inputMint, inputProgram)),
                writable(ata(user, outputMint, outputProgram)), writable(inputVault),
                writable(outputVault), readonly(inputProgram), readonly(outputProgram),
                readonly(inputMint), readonly(outputMint), writable(pool.observation())
        ), data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * 构造 CLMM swap_v2；校验可选 bitmap PDA 并追加调用方提供的 tick 数组。
     * @param pool 已解析的 CLMM 池账户和 tick 数组
     * @param user 交易用户公钥
     * @param inputMint 本跳输入代币 mint
     * @param amountIn 精确输入量，代币最小单位
     * @param minOut 最低输出量，代币最小单位
     * @return CLMM 的 Router 一跳
     */
    public static RouterLeg clmm(Clmm pool, PublicKey user, PublicKey inputMint,
                                 long amountIn, long minOut) {
        Objects.requireNonNull(pool, "pool");
        positive(amountIn, "amountIn"); positive(minOut, "minOut");
        if (pool.tickArrays().isEmpty()) throw new IllegalArgumentException("CLMM tick arrays missing");
        PublicKey outputMint = other(inputMint, pool.token0Mint(), pool.token1Mint());
        boolean zeroForOne = inputMint.equals(pool.token0Mint());
        PublicKey inputVault = zeroForOne ? pool.token0Vault() : pool.token1Vault();
        PublicKey outputVault = zeroForOne ? pool.token1Vault() : pool.token0Vault();
        PublicKey inputProgram = zeroForOne ? pool.token0Program() : pool.token1Program();
        PublicKey outputProgram = zeroForOne ? pool.token1Program() : pool.token0Program();
        tokenProgram(inputProgram); tokenProgram(outputProgram);
        ByteArrayOutputStream data = discriminatorAndAmounts(CLMM_SWAP_V2, amountIn, minOut);
        putU128(data, zeroForOne ? CLMM_MIN_SQRT.add(BigInteger.ONE)
                : CLMM_MAX_SQRT.subtract(BigInteger.ONE));
        data.write(1); // is_base_input=true，按精确输入模式执行。
        List<AccountMeta> accounts = new ArrayList<>(14 + pool.tickArrays().size());
        accounts.addAll(List.of(
                readonlySigner(user), readonly(pool.config()), writable(pool.pool()),
                writable(ata(user, inputMint, inputProgram)),
                writable(ata(user, outputMint, outputProgram)), writable(inputVault),
                writable(outputVault), writable(pool.observation()),
                readonly(TokenProgram.PROGRAM_ID), readonly(TokenProgram.TOKEN_2022),
                readonly(MEMO), readonly(inputMint), readonly(outputMint)
        ));
        if (pool.bitmapExtension() != null) {
            PublicKey expected = pda(CLMM, "pool_tick_array_bitmap_extension".getBytes(StandardCharsets.US_ASCII),
                    pool.pool().toByteArray());
            if (!expected.equals(pool.bitmapExtension()))
                throw new IllegalArgumentException("invalid CLMM bitmap extension PDA");
            accounts.add(writable(pool.bitmapExtension()));
        }
        for (PublicKey tick : pool.tickArrays()) accounts.add(writable(tick));
        return new RouterLeg(CLMM, accounts, data.toByteArray(), inputMint, outputMint, amountIn, minOut);
    }

    /**
     * AMM V4 买入：约定 pc 为支付侧、coin 为收到侧。
     * @param pool 含 pc 和 coin 的 AMM V4 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 pc 的精确数量
     * @param minBaseOut 最低收到 coin 数量
     * @return AMM V4 买入一跳
     */
    public static RouterLeg buyAmmV4(AmmV4 pool, PublicKey user, long quoteIn, long minBaseOut) {
        return ammV4(pool, user, pool.pcMint(), quoteIn, minBaseOut);
    }

    /**
     * AMM V4 卖出：约定 coin 为卖出侧、pc 为收到侧。
     * @param pool 含 pc 和 coin 的 AMM V4 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 coin 的精确数量
     * @param minQuoteOut 最低收到 pc 数量
     * @return AMM V4 卖出一跳
     */
    public static RouterLeg sellAmmV4(AmmV4 pool, PublicKey user, long baseIn, long minQuoteOut) {
        return ammV4(pool, user, pool.coinMint(), baseIn, minQuoteOut);
    }

    /**
     * CPMM 买入：约定 quote 为支付侧、base 为收到侧。
     * @param pool 含 base 和 quote 的 CPMM 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 quote 的精确数量
     * @param minBaseOut 最低收到 base 数量
     * @return CPMM 买入一跳
     */
    public static RouterLeg buyCpmm(Cpmm pool, PublicKey user, long quoteIn, long minBaseOut) {
        return cpmm(pool, user, pool.quoteMint(), quoteIn, minBaseOut);
    }

    /**
     * CPMM 卖出：约定 base 为卖出侧、quote 为收到侧。
     * @param pool 含 base 和 quote 的 CPMM 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 base 的精确数量
     * @param minQuoteOut 最低收到 quote 数量
     * @return CPMM 卖出一跳
     */
    public static RouterLeg sellCpmm(Cpmm pool, PublicKey user, long baseIn, long minQuoteOut) {
        return cpmm(pool, user, pool.baseMint(), baseIn, minQuoteOut);
    }

    /**
     * CLMM 买入：约定 token1 为支付侧、token0 为收到侧。
     * @param pool 含 token0、token1 及所需 tick 数组的 CLMM 池
     * @param user 交易用户公钥
     * @param quoteIn 支付 token1 的精确数量
     * @param minBaseOut 最低收到 token0 数量
     * @return CLMM 买入一跳
     */
    public static RouterLeg buyClmm(Clmm pool, PublicKey user, long quoteIn, long minBaseOut) {
        return clmm(pool, user, pool.token1Mint(), quoteIn, minBaseOut);
    }

    /**
     * CLMM 卖出：约定 token0 为卖出侧、token1 为收到侧。
     * @param pool 含 token0、token1 及所需 tick 数组的 CLMM 池
     * @param user 交易用户公钥
     * @param baseIn 卖出 token0 的精确数量
     * @param minQuoteOut 最低收到 token1 数量
     * @return CLMM 卖出一跳
     */
    public static RouterLeg sellClmm(Clmm pool, PublicKey user, long baseIn, long minQuoteOut) {
        return clmm(pool, user, pool.token0Mint(), baseIn, minQuoteOut);
    }

    /**
     * 确认输入 mint 属于池子，并返回另一侧 mint。
     * @param input 本跳输入 mint
     * @param a 池的第一侧 mint
     * @param b 池的第二侧 mint
     * @return 本跳输出 mint
     */
    static PublicKey other(PublicKey input, PublicKey a, PublicKey b) {
        if (input.equals(a)) return b;
        if (input.equals(b)) return a;
        throw new IllegalArgumentException("input mint is not in this pool");
    }

    /**
     * 写入 Anchor 判别符、输入量和最低输出量。
     * @param discriminator DEX 指令判别符
     * @param amountIn 精确输入数量
     * @param minOut 最低输出数量
     * @return 已写入指令前缀的字节流
     */
    static ByteArrayOutputStream discriminatorAndAmounts(byte[] discriminator, long amountIn, long minOut) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(discriminator.length + 16);
        out.writeBytes(discriminator);
        putU64(out, amountIn); putU64(out, minOut);
        return out;
    }

    /**
     * 将无符号 128 位整数按小端序写入 DEX 指令。
     * @param out 目标指令字节流
     * @param value 待写入的无符号整数
     */
    static void putU128(ByteArrayOutputStream out, BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 128) throw new IllegalArgumentException("u128 out of range");
        byte[] bigEndian = value.toByteArray();
        for (int i = 0; i < 16; i++) {
            int source = bigEndian.length - 1 - i;
            out.write(source >= 0 ? bigEndian[source] & 0xff : 0);
        }
    }
}
