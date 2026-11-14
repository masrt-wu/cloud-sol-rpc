package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Rust SDK route_ix.rs 的 Java 对应实现：本地构造精确输入、动态两跳与动态三跳路由。 */
public final class RouterTradeBuilder {
    /** 当前 Java 客户端使用的 Router 程序地址；重新部署后必须同步更新。 */
    public static final PublicKey ROUTER_PROGRAM = key("CmNFUmRJL7YcnVn22oZzwG5Xg5WJqbcHEc6BK5mzDNR8");
    /** WSOL mint 地址；原生 SOL 路径会先包装成 WSOL。 */
    public static final PublicKey WSOL_MINT = key("So11111111111111111111111111111111111111112");
    /** 固定金额路由使用的指令标签，保留旧版格式。 */
    private static final byte ROUTE_TAG = 2;
    /** 动态两跳路由使用的指令标签。 */
    private static final byte ROUTE_DYNAMIC_TAG = 3;
    /** 动态三跳路由使用的指令标签。 */
    private static final byte ROUTE_DYNAMIC_THREE_TAG = 4;
    /** 表示以输入代币作为手续费资产的编码值。 */
    private static final byte FEE_ASSET_TOKEN = 1;
    /** 动态第二跳允许的 LaunchLab 买入指令判别符。 */
    private static final byte[] LAUNCHLAB_BUY_EXACT_IN = {(byte) 250, (byte) 234, 13, 123,
            (byte) 213, (byte) 156, 19, (byte) 236};
    /** 动态第二跳允许的 CPMM 精确输入指令判别符。 */
    private static final byte[] CPMM_SWAP_BASE_IN = {(byte) 143, (byte) 190, 90, (byte) 218,
            (byte) 196, 30, 51, (byte) 222};
    /** Raydium CLMM 和 Orca Whirlpool 的精确输入交换判别符。 */
    private static final byte[] CLMM_SWAP_V2 = {43, 4, (byte) 237, 11, 26, (byte) 201, 30, 98};
    /** Meteora DLMM 和 DAMM v2 的 swap2 判别符。 */
    private static final byte[] METEORA_SWAP2 = {65, 75, 63, 76, (byte) 235, 91, 91, (byte) 136};
    /** PumpSwap 固定 quote 输入的买入判别符；末尾另有一字节交易量开关。 */
    private static final byte[] PUMPSWAP_BUY_EXACT_QUOTE_IN = {(byte) 198, 46, 21, 82,
            (byte) 180, (byte) 217, (byte) 232, 112};
    /** PumpSwap 卖出 base 的精确输入判别符。 */
    private static final byte[] PUMPSWAP_SELL = {51, (byte) 230, (byte) 133, (byte) 164,
            1, 127, (byte) 131, (byte) 173};
    /** Pump 内盘 V2 固定 quote 输入的买入判别符，不是精确输出的 buy_v2。 */
    private static final byte[] PUMPFUN_BUY_EXACT_QUOTE_IN_V2 = {(byte) 194, (byte) 171,
            28, 70, 104, 77, 91, 47};
    /** Pump 内盘 V2 固定 base 输入的卖出判别符。 */
    private static final byte[] PUMPFUN_SELL_V2 = {93, (byte) 246, (byte) 130, 60,
            (byte) 231, (byte) 233, 64, (byte) 178};
    /** 部分 DEX 指令不单独传入 mint 或 Token Program 时使用的索引标记。 */
    private static final int NO_ACCOUNT = -1;
    /** 每个构造线程只缓存近期 ATA，避免同一条路由反复执行昂贵的 PDA 曲线检查。 */
    private static final int MAX_CACHED_ATAS = 512;
    private static final class AtaCacheKey {
        private final byte[] bytes = new byte[96];
        private final int hash;

        private AtaCacheKey(PublicKey owner, PublicKey mint, PublicKey tokenProgram) {
            System.arraycopy(owner.toByteArray(), 0, bytes, 0, 32);
            System.arraycopy(mint.toByteArray(), 0, bytes, 32, 32);
            System.arraycopy(tokenProgram.toByteArray(), 0, bytes, 64, 32);
            hash = Arrays.hashCode(bytes);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof AtaCacheKey key && Arrays.equals(bytes, key.bytes);
        }
    }
    private static final ThreadLocal<Map<AtaCacheKey, byte[]>> ATA_CACHE = ThreadLocal.withInitial(() ->
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<AtaCacheKey, byte[]> eldest) {
                    return size() > MAX_CACHED_ATAS;
                }
            });

    /**
     * 链上动态跳识别结果；索引均相对于本跳的 CPI 账户列表。
     * @param inputAccount 用户输入 ATA 的索引
     * @param outputAccount 用户输出 ATA 的索引
     * @param signer 用户签名账户的索引
     * @param amountOffset 输入金额在 DEX 指令数据中的偏移
     * @param inputMint 输入 mint 账户的索引
     * @param inputProgram 输入 Token Program 的索引
     * @param outputMint 输出 mint 账户的索引
     * @param outputProgram 输出 Token Program 的索引
     * @param pairedMints 池侧 mint 是否允许按方向正反匹配
     */
    private record DynamicLegLayout(int inputAccount, int outputAccount, int signer,
                                    int amountOffset, int inputMint, int inputProgram,
                                    int outputMint, int outputProgram, boolean pairedMints) { }

    /** 工具类不允许实例化。 */
    private RouterTradeBuilder() { }

    /** DEX 类也会调用本类的 key()；延迟初始化可避免静态初始化循环。 */
    private static final class DynamicFirstPrograms {
        /** 动态两跳允许使用的第一跳 DEX 程序集合。 */
        private static final Set<PublicKey> IDS = Set.of(
                RaydiumLegs.AMM_V4, RaydiumLegs.CPMM, RaydiumLegs.CLMM,
                OrcaLegs.WHIRLPOOL, MeteoraLegs.DAMM_V2, MeteoraLegs.DLMM,
                PumpAmmLegs.PROGRAM, PumpFunLegs.PROGRAM, LaunchLabLegs.PROGRAM);
    }

    /**
     * 单跳买卖参数；所有数量均为代币最小单位，非 UI 显示数量。
     * @param payer 交易付款与签名公钥
     * @param inputMint 支付代币 mint
     * @param outputMint 收取代币 mint
     * @param inputTokenProgram 输入 mint 所属的 Token Program
     * @param outputTokenProgram 输出 mint 所属的 Token Program
     * @param amountInRaw 精确输入数量
     * @param minOutRaw 最低输出数量
     * @param leg 需要执行的 DEX 一跳
     * @param createOutputAta 是否幂等创建输出 ATA
     */
    public record Swap(PublicKey payer, PublicKey inputMint, PublicKey outputMint,
                       PublicKey inputTokenProgram, PublicKey outputTokenProgram,
                       long amountInRaw, long minOutRaw, RouterLeg leg,
                       boolean createOutputAta) {
        /** 校验必需字段、代币方向及最小单位金额。 */
        public Swap {
            Objects.requireNonNull(payer, "payer");
            Objects.requireNonNull(inputMint, "inputMint");
            Objects.requireNonNull(outputMint, "outputMint");
            Objects.requireNonNull(inputTokenProgram, "inputTokenProgram");
            Objects.requireNonNull(outputTokenProgram, "outputTokenProgram");
            Objects.requireNonNull(leg, "leg");
            if (inputMint.equals(outputMint)) throw new IllegalArgumentException("input/output mints are equal");
            positive(amountInRaw, "amountInRaw");
            positive(minOutRaw, "minOutRaw");
            tokenProgram(inputTokenProgram);
            tokenProgram(outputTokenProgram);
        }
    }

    /**
     * 一至四跳固定金额路由；tokenPrograms 先放输入 mint 的程序，再按路径依次放每跳输出 mint 的程序。
     * 下一跳的固定输入量不得超过上一跳保证的最低输出，否则可能在同一笔交易中余额不足。
     * @param payer 交易付款与签名公钥
     * @param legs 依执行顺序排列的一至四跳 DEX 指令
     * @param tokenPrograms 路径上各 mint 对应的 Token Program
     * @param amountInRaw 整条路由的精确输入数量
     * @param minOutRaw 整条路由的最低最终输出
     * @param createIntermediateAtas 是否创建中间代币 ATA
     * @param createOutputAta 是否创建最终输出 ATA
     */
    public record RouteSwap(PublicKey payer, List<RouterLeg> legs,
                            List<PublicKey> tokenPrograms, long amountInRaw, long minOutRaw,
                            boolean createIntermediateAtas, boolean createOutputAta) {
        /** 校验跳数、路径衔接、内层指令金额和最终最低输出。 */
        public RouteSwap {
            Objects.requireNonNull(payer, "payer");
            legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
            tokenPrograms = List.copyOf(Objects.requireNonNull(tokenPrograms, "tokenPrograms"));
            if (legs.isEmpty() || legs.size() > 4)
                throw new IllegalArgumentException("Router accepts 1..4 legs");
            if (tokenPrograms.size() != legs.size() + 1)
                throw new IllegalArgumentException("tokenPrograms must contain one entry per path mint");
            for (PublicKey program : tokenPrograms) tokenProgram(program);
            positive(amountInRaw, "amountInRaw");
            positive(minOutRaw, "minOutRaw");
            for (int i = 0; i < legs.size(); i++) {
                RouterLeg leg = legs.get(i);
                if (i == 0 && leg.amountInRaw() != amountInRaw)
                    throw new IllegalArgumentException("first leg input differs from Router amount_in");
                if (i > 0) {
                    RouterLeg previous = legs.get(i - 1);
                    if (!previous.outputMint().equals(leg.inputMint()))
                        throw new IllegalArgumentException("adjacent legs have different intermediate mints");
                    if (leg.amountInRaw() > previous.minOutRaw())
                        throw new IllegalArgumentException("next leg input exceeds guaranteed previous output");
                }
                byte[] inner = leg.data();
                int offset = leg.programId().equals(RaydiumLegs.AMM_V4) ? 1 : 8;
                if (inner.length < offset + 16 || readU64(inner, offset) != leg.amountInRaw()
                        || readU64(inner, offset + 8) < leg.minOutRaw())
                    throw new IllegalArgumentException("inner DEX data amount/threshold differs from leg");
            }
            if (legs.get(legs.size() - 1).minOutRaw() < minOutRaw)
                throw new IllegalArgumentException("final leg minimum is below Router min_amount_out");
        }

        /** 返回整条路由的起始输入 mint。 */
        public PublicKey inputMint() { return legs.get(0).inputMint(); }
        /** 返回整条路由的最终输出 mint。 */
        public PublicKey outputMint() { return legs.get(legs.size() - 1).outputMint(); }
        /** 返回输入 mint 所属的 Token Program。 */
        public PublicKey inputTokenProgram() { return tokenPrograms.get(0); }
        /** 返回最终输出 mint 所属的 Token Program。 */
        public PublicKey outputTokenProgram() { return tokenPrograms.get(tokenPrograms.size() - 1); }
    }

    /**
     * 动态两跳兑换：第一跳得到的中间币增量由链上读取，第二跳在同一笔交易中全部花掉。
     * 第二跳支持链上白名单中的精确输入池型，金额字段先填 1，链上会替换为真实新增量。
     * 这里仅做本地构造；调用前应准备好最新池账户和最低输出数量。
     * 高频路径可提前创建三个 ATA 并预存 WSOL，将两个 create 标志设为 false，避免每笔附加创建指令。
     * @param payer 交易付款与签名公钥
     * @param firstLeg 先执行的 DEX 交易
     * @param secondLeg 后执行的 LaunchLab 买入或受支持池型的精确输入交易，输入量需设为 1
     * @param inputTokenProgram 输入代币所属的 Token Program
     * @param quoteTokenProgram 中间 quote 所属的 Token Program
     * @param outputTokenProgram 最终输出代币所属的 Token Program
     * @param amountInRaw 整条路由的精确输入数量
     * @param quoteMinOutRaw 第一跳最低 quote 输出
     * @param minOutRaw 整条路由最低最终输出
     * @param createIntermediateAta 是否创建中间 quote ATA
     * @param createOutputAta 是否创建最终输出 ATA
     */
    public record DynamicRouteSwap(PublicKey payer, RouterLeg firstLeg, RouterLeg secondLeg,
                                   PublicKey inputTokenProgram, PublicKey quoteTokenProgram,
                                   PublicKey outputTokenProgram, long amountInRaw,
                                   long quoteMinOutRaw, long minOutRaw,
                                   boolean createIntermediateAta, boolean createOutputAta) {
        /** 校验第一跳类型、两跳衔接和链上替换金额所需的账户布局。 */
        public DynamicRouteSwap {
            Objects.requireNonNull(payer, "payer");
            Objects.requireNonNull(firstLeg, "firstLeg");
            Objects.requireNonNull(secondLeg, "secondLeg");
            tokenProgram(inputTokenProgram);
            tokenProgram(quoteTokenProgram);
            tokenProgram(outputTokenProgram);
            positive(amountInRaw, "amountInRaw");
            positive(quoteMinOutRaw, "quoteMinOutRaw");
            positive(minOutRaw, "minOutRaw");
            if (!DynamicFirstPrograms.IDS.contains(firstLeg.programId()))
                throw new IllegalArgumentException("不支持的第一跳 DEX");
            if (!firstLeg.outputMint().equals(secondLeg.inputMint())
                    || firstLeg.inputMint().equals(firstLeg.outputMint())
                    || firstLeg.outputMint().equals(secondLeg.outputMint()))
                throw new IllegalArgumentException("两跳代币方向不连通或 quote 与输入/输出相同");
            if (firstLeg.amountInRaw() != amountInRaw || secondLeg.amountInRaw() != 1)
                throw new IllegalArgumentException("第一跳金额须等于路由输入，第二跳占位金额须为 1");
            if (firstLeg.minOutRaw() < quoteMinOutRaw || secondLeg.minOutRaw() < minOutRaw)
                throw new IllegalArgumentException("DEX 最低输出不能低于路由最低输出");
            checkInnerAmount(firstLeg);
            checkInnerAmount(secondLeg);
            PublicKey sourceAta = ata(payer, firstLeg.inputMint(), inputTokenProgram);
            PublicKey quoteAta = ata(payer, firstLeg.outputMint(), quoteTokenProgram);
            PublicKey outputAta = ata(payer, secondLeg.outputMint(), outputTokenProgram);
            if (!hasWritable(firstLeg.accounts(), sourceAta)
                    || !hasWritable(firstLeg.accounts(), quoteAta))
                throw new IllegalArgumentException("第一跳未写入正确的输入或 quote ATA");
            checkDynamicLeg(payer, secondLeg, quoteAta, outputAta,
                    quoteTokenProgram, outputTokenProgram, true);
        }

        /** 返回第一跳的输入 mint。 */
        public PublicKey inputMint() { return firstLeg.inputMint(); }
        /** 返回两跳之间共享的中间 quote mint。 */
        public PublicKey quoteMint() { return firstLeg.outputMint(); }
        /** 返回第二跳的最终输出 mint。 */
        public PublicKey outputMint() { return secondLeg.outputMint(); }
    }

    /**
     * 三跳兑换参数。启用动态模式时，Java 将后两跳的占位输入量 1 编码成链上要求的 0。
     * 关闭动态模式时，保留旧版标签 2；后两跳必须各自填写已知的固定输入量。
     * 所有金额均为代币最小单位，池与数组账户须在交易前由调用方准备。
     *
     * @param payer 交易付款与签名公钥
     * @param firstLeg 第一跳 DEX 指令
     * @param middleLeg 第二跳 DEX 指令，动态模式下输入占位量为 1
     * @param finalLeg 第三跳 DEX 指令，动态模式下输入占位量为 1
     * @param inputTokenProgram 初始输入代币所属程序
     * @param firstTokenProgram 第一种中间代币所属程序
     * @param secondTokenProgram 第二种中间代币所属程序
     * @param outputTokenProgram 最终输出代币所属程序
     *                           | 参数 | 对应的代币 |
     * | --- | --- |
     * | `inputTokenProgram` | WSOL 的 Token 程序 |
     * | `firstTokenProgram` | 第一跳收到的 USDC 的 Token 程序 |
     * | `secondTokenProgram` | 第二跳收到的 quote 的 Token 程序 |
     * | `outputTokenProgram` | 第三跳收到的目标 mint 的 Token 程序 |
     * @param amountInRaw Router 的总输入量
     * @param firstMinOutRaw 第一跳最低净到账量
     * @param secondMinOutRaw 第二跳最低净到账量
     * @param minOutRaw 最终最低净到账量
     * @param spendActualOutput 是否将每一跳实际新增量全部用于下一跳 true 开启 动态模式 false 不开启
     * @param createIntermediateAtas 是否幂等创建两个中间代币 ATA
     * @param createOutputAta 是否幂等创建最终输出 ATA
     */
    public record ThreeHopRouteSwap(PublicKey payer,
                                    RouterLeg firstLeg,
                                    RouterLeg middleLeg,
                                    RouterLeg finalLeg,
                                    PublicKey inputTokenProgram,
                                    PublicKey firstTokenProgram,
                                    PublicKey secondTokenProgram,
                                    PublicKey outputTokenProgram,
                                    long amountInRaw,
                                    long firstMinOutRaw,
                                    long secondMinOutRaw,
                                    long minOutRaw,
                                    boolean spendActualOutput,
                                    boolean createIntermediateAtas,
                                    boolean createOutputAta) {
        /** 校验三跳方向、最小输出和动态跳的账户顺序。 */
        public ThreeHopRouteSwap {
            Objects.requireNonNull(payer, "payer");
            Objects.requireNonNull(firstLeg, "firstLeg");
            Objects.requireNonNull(middleLeg, "middleLeg");
            Objects.requireNonNull(finalLeg, "finalLeg");
            tokenProgram(inputTokenProgram);
            tokenProgram(firstTokenProgram);
            tokenProgram(secondTokenProgram);
            tokenProgram(outputTokenProgram);
            positive(amountInRaw, "amountInRaw");
            positive(firstMinOutRaw, "firstMinOutRaw");
            positive(secondMinOutRaw, "secondMinOutRaw");
            positive(minOutRaw, "minOutRaw");
            if (!firstLeg.outputMint().equals(middleLeg.inputMint())
                    || !middleLeg.outputMint().equals(finalLeg.inputMint()))
                throw new IllegalArgumentException("三跳输入和输出 mint 不连通");
            if (firstLeg.amountInRaw() != amountInRaw
                    || firstLeg.minOutRaw() < firstMinOutRaw
                    || middleLeg.minOutRaw() < secondMinOutRaw
                    || finalLeg.minOutRaw() < minOutRaw)
                throw new IllegalArgumentException("三跳输入或最低输出与 Router 参数不一致");
            checkInnerAmount(firstLeg);
            checkInnerAmount(middleLeg);
            checkInnerAmount(finalLeg);
            if (spendActualOutput) {
                Set<PublicKey> path = new LinkedHashSet<>(List.of(firstLeg.inputMint(),
                        firstLeg.outputMint(), middleLeg.outputMint(), finalLeg.outputMint()));
                if (path.size() != 4)
                    throw new IllegalArgumentException("动态中间账户必须与输入、输出账户互不相同");
                if (middleLeg.amountInRaw() != 1 || finalLeg.amountInRaw() != 1)
                    throw new IllegalArgumentException("动态三跳后两跳输入占位量必须为 1");
                PublicKey firstAta = ata(payer, firstLeg.outputMint(), firstTokenProgram);
                PublicKey secondAta = ata(payer, middleLeg.outputMint(), secondTokenProgram);
                PublicKey outputAta = ata(payer, finalLeg.outputMint(), outputTokenProgram);
                if (!hasWritable(firstLeg.accounts(), firstAta)
                        || hasWritable(firstLeg.accounts(), secondAta))
                    throw new IllegalArgumentException("第一跳必须写第一中间账户且不得写第二中间账户");
                checkDynamicLeg(payer, middleLeg, firstAta, secondAta,
                        firstTokenProgram, secondTokenProgram, false);
                checkDynamicLeg(payer, finalLeg, secondAta, outputAta,
                        secondTokenProgram, outputTokenProgram, true);
            } else if (middleLeg.amountInRaw() > firstLeg.minOutRaw()
                    || finalLeg.amountInRaw() > middleLeg.minOutRaw()) {
                throw new IllegalArgumentException("固定金额模式下，下一跳输入不得超过上一跳最低输出");
            }
        }

        /** 返回整条路径的输入代币 mint。 */
        public PublicKey inputMint() { return firstLeg.inputMint(); }
        /** 返回第一个中间代币 mint。 */
        public PublicKey firstIntermediateMint() { return firstLeg.outputMint(); }
        /** 返回第二个中间代币 mint。 */
        public PublicKey secondIntermediateMint() { return middleLeg.outputMint(); }
        /** 返回最终输出代币 mint。 */
        public PublicKey outputMint() { return finalLeg.outputMint(); }
        /** 返回按执行顺序排列的三条 DEX 指令。 */
        public List<RouterLeg> legs() { return List.of(firstLeg, middleLeg, finalLeg); }
    }

    /**
     * 固定金额示例：WSOL 经 Meteora DLMM 换 quote，再经 LaunchLab 买目标币。
     * @param payer 交易付款与签名公钥
     * @param quotePool WSOL/quote 的 DLMM 池快照
     * @param mintPool quote/目标币的 LaunchLab 池快照
     * @param wsolIn 第一跳 WSOL 精确输入量
     * @param quoteMinOut 第一跳 quote 最低输出量
     * @param quoteForMint 第二跳事先确定的 quote 输入量
     * @param mintMinOut 最终目标币最低输出量
     * @return 标签 2 的固定金额两跳参数
     */
    public static RouteSwap buyViaDlmmLaunchLab(PublicKey payer, MeteoraLegs.Dlmm quotePool,
                                                 LaunchLabLegs.Pool mintPool, long wsolIn,
                                                 long quoteMinOut, long quoteForMint,
                                                 long mintMinOut) {
        PublicKey quoteMint;
        PublicKey quoteProgram;
        if (WSOL_MINT.equals(quotePool.mintX())) {
            if (!TokenProgram.PROGRAM_ID.equals(quotePool.tokenProgramX()))
                throw new IllegalArgumentException("WSOL must use the classic Token Program");
            quoteMint = quotePool.mintY();
            quoteProgram = quotePool.tokenProgramY();
        } else if (WSOL_MINT.equals(quotePool.mintY())) {
            if (!TokenProgram.PROGRAM_ID.equals(quotePool.tokenProgramY()))
                throw new IllegalArgumentException("WSOL must use the classic Token Program");
            quoteMint = quotePool.mintX();
            quoteProgram = quotePool.tokenProgramX();
        } else {
            throw new IllegalArgumentException("Meteora bridge pool does not contain WSOL");
        }
        if (!quoteMint.equals(mintPool.quoteMint())
                || !quoteProgram.equals(mintPool.quoteTokenProgram()))
            throw new IllegalArgumentException("LaunchLab quote does not match Meteora bridge output");
        RouterLeg first = MeteoraLegs.dlmm(quotePool, payer, WSOL_MINT, wsolIn, quoteMinOut);
        RouterLeg second = LaunchLabLegs.buyExactIn(mintPool, payer, quoteForMint, mintMinOut);
        return new RouteSwap(payer, List.of(first, second),
                List.of(TokenProgram.PROGRAM_ID, quoteProgram, mintPool.baseTokenProgram()),
                wsolIn, mintMinOut, true, true);
    }

    /**
     * 单跳买入：仅构造本地指令，不访问 RPC。
     * @param swap 单跳买入参数
     * @param feeRecipient Router 配置中记录的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> buy(Swap swap, PublicKey feeRecipient) {
        return exactIn(swap, feeRecipient);
    }

    /**
     * 单跳卖出：仅构造本地指令，不访问 RPC。
     * @param swap 单跳卖出参数
     * @param feeRecipient Router 配置中记录的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(Swap swap, PublicKey feeRecipient) {
        return exactIn(swap, feeRecipient);
    }

    /**
     * 构造固定金额多跳买入。
     * @param swap 固定金额路由参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> buy(RouteSwap swap, PublicKey feeRecipient) {
        return exactIn(swap, feeRecipient);
    }

    /**
     * 构造动态两跳买入，不访问 RPC，也不签名或发送。
     * @param swap 动态两跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> buy(DynamicRouteSwap swap, PublicKey feeRecipient) {
        return dynamicExactIn(swap, feeRecipient);
    }
    /**
     * 构造动态两跳买入，不访问 RPC，也不签名或发送。
     * @param swap 动态两跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> buy(DynamicRouteSwap swap, PublicKey feeRecipient,PublicKey outputTokenProgram) {
        return dynamicExactIn(swap, feeRecipient,outputTokenProgram);
    }

    /**
     * 构造动态两跳卖出；第二跳可为链上支持的精确输入池型。
     * 若最终得到 WSOL，本方法不会自动关闭 WSOL 账户。
     * @param swap 动态两跳卖出参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(DynamicRouteSwap swap, PublicKey feeRecipient) {
        return dynamicExactIn(swap, feeRecipient);
    }
    /**
     * 构造动态两跳卖出；第二跳可为链上支持的精确输入池型。
     * 若最终得到 WSOL，本方法不会自动关闭 WSOL 账户。
     * @param swap 动态两跳卖出参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(DynamicRouteSwap swap, PublicKey feeRecipient,PublicKey outputTokenProgram) {
        return dynamicExactIn(swap, feeRecipient,outputTokenProgram);
    }

    /**
     * 构造动态或固定金额三跳买入。
     * @param swap 三跳路径及模式参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> buy(ThreeHopRouteSwap swap, PublicKey feeRecipient) {
        return threeHopExactIn(swap, feeRecipient);
    }

    public static List<TransactionInstruction> buy(ThreeHopRouteSwap swap, PublicKey feeRecipient,PublicKey outputTokenProgram) {
        return threeHopExactIn(swap, feeRecipient,outputTokenProgram);
    }

    /**
     * 构造动态或固定金额三跳卖出；收到 WSOL 时不会自动解包。
     * @param swap 三跳路径及模式参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(ThreeHopRouteSwap swap, PublicKey feeRecipient,PublicKey outputTokenProgram) {
        return threeHopExactIn(swap, feeRecipient,outputTokenProgram);
    }

    /**
     * 构造动态或固定金额三跳卖出；收到 WSOL 时不会自动解包。
     * @param swap 三跳路径及模式参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(ThreeHopRouteSwap swap, PublicKey feeRecipient) {
        return threeHopExactIn(swap, feeRecipient);
    }

    /**
     * 动态先创建 WSol
     * @param swap
     * @param feeRecipient
     * @return
     */
    public static List<TransactionInstruction> sellCreWSol(ThreeHopRouteSwap swap, PublicKey feeRecipient) {
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(7);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(threeHopExactIn(swap, feeRecipient));
        return result;
    }
    public static List<TransactionInstruction> sellCreWSol(ThreeHopRouteSwap swap, PublicKey feeRecipient,PublicKey outputTokenProgram) {
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(7);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(threeHopExactIn(swap, feeRecipient,outputTokenProgram));
        return result;
    }

    /**
     * 创建两个中间 ATA、输出 ATA，并追加三跳 Router 指令。
     * @param swap 三跳路径及模式参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 按链上执行顺序排列的指令
     */
    private static List<TransactionInstruction> threeHopExactIn(ThreeHopRouteSwap swap,
                                                                 PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        List<TransactionInstruction> result = new ArrayList<>(4);
        if (swap.createIntermediateAtas()) {
            result.add(createAta(swap.payer(), swap.firstIntermediateMint(), swap.firstTokenProgram()));
            result.add(createAta(swap.payer(), swap.secondIntermediateMint(), swap.secondTokenProgram()));
        }
        if (swap.createOutputAta())
            result.add(createAta(swap.payer(), swap.outputMint(), swap.outputTokenProgram()));
        result.add(routeThreeHop(swap, feeRecipient));
        return List.copyOf(result);
    }

    private static List<TransactionInstruction> threeHopExactIn(ThreeHopRouteSwap swap,
                                                                PublicKey feeRecipient,
                                                                PublicKey outputTokenProgram) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        List<TransactionInstruction> result = new ArrayList<>(4);
        if (swap.createIntermediateAtas()) {
            result.add(createAta(swap.payer(), swap.firstIntermediateMint(), swap.firstTokenProgram()));
            result.add(createAta(swap.payer(), swap.secondIntermediateMint(), swap.secondTokenProgram()));
        }
        if (swap.createOutputAta())
            result.add(createAta(swap.payer(), swap.outputMint(), outputTokenProgram));
        result.add(routeThreeHop(swap, feeRecipient));
        return List.copyOf(result);
    }

    /**
     * 为动态两跳按需创建中间和输出 ATA，再追加 Router 指令。
     * @param swap 动态两跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 按链上执行顺序排列的指令
     */
    private static List<TransactionInstruction> dynamicExactIn(DynamicRouteSwap swap,
                                                                PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        List<TransactionInstruction> result = new ArrayList<>(3);
        if (swap.createIntermediateAta())
            result.add(createAta(swap.payer(), swap.quoteMint(), swap.quoteTokenProgram()));
        if (swap.createOutputAta())
            result.add(createAta(swap.payer(), swap.outputMint(), swap.outputTokenProgram()));
        result.add(routeDynamic(swap, feeRecipient));
        return List.copyOf(result);
    }

    private static List<TransactionInstruction> dynamicExactIn(DynamicRouteSwap swap,
                                                               PublicKey feeRecipient,
                                                               PublicKey outputTokenProgram) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        List<TransactionInstruction> result = new ArrayList<>(3);
        if (swap.createIntermediateAta())
            result.add(createAta(swap.payer(), swap.quoteMint(), swap.quoteTokenProgram()));
        if (swap.createOutputAta())
            result.add(createAta(swap.payer(), swap.outputMint(), outputTokenProgram));
        result.add(routeDynamic(swap, feeRecipient));
        return List.copyOf(result);
    }

    /**
     * 构造固定金额多跳卖出。
     * @param swap 固定金额路由参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令与 Router 指令
     */
    public static List<TransactionInstruction> sell(RouteSwap swap, PublicKey feeRecipient) {
        return exactIn(swap, feeRecipient);
    }

    /**
     * 路由前先包装原生 SOL；保留 WSOL ATA，不自动关闭或解包。
     * @param swap 以 WSOL 为输入的固定金额路由
     * @param feeRecipient Router 配置中的手续费接收方
     * @return WSOL 准备指令和路由指令
     */
    public static List<TransactionInstruction> buyWithSol(RouteSwap swap, PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        if (!swap.inputMint().equals(WSOL_MINT)
                || !swap.inputTokenProgram().equals(TokenProgram.PROGRAM_ID))
            throw new IllegalArgumentException("native SOL buy requires WSOL as the first mint");
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>();
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(SystemProgram.transfer(swap.payer(), wsolAta, swap.amountInRaw()));
        // WSOL ATA 是可写 PDA，不是签名账户。
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(exactIn(swap, feeRecipient));
        return List.copyOf(result);
    }



    /**
     * 原生 SOL 买入：同一笔交易中先创建、充值并同步 WSOL，再执行动态两跳。
     * @param swap 以经典 SPL WSOL 为输入的动态两跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return WSOL 准备指令和动态两跳指令
     */
    public static List<TransactionInstruction> buyWithSol(DynamicRouteSwap swap,
                                                            PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        if (!WSOL_MINT.equals(swap.inputMint())
                || !TokenProgram.PROGRAM_ID.equals(swap.inputTokenProgram()))
            throw new IllegalArgumentException("原生 SOL 买入的第一跳输入必须是经典 SPL WSOL");
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(6);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(SystemProgram.transfer(swap.payer(), wsolAta, swap.amountInRaw()));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(buy(swap, feeRecipient));
        return List.copyOf(result);
    }
    public static List<TransactionInstruction> buyWithSol(DynamicRouteSwap swap,
                                                          PublicKey feeRecipient,
                                                          PublicKey outputTokenProgram) {
        Objects.requireNonNull(swap, "swap");
        if (!WSOL_MINT.equals(swap.inputMint())
                || !TokenProgram.PROGRAM_ID.equals(swap.inputTokenProgram()))
            throw new IllegalArgumentException("原生 SOL 买入的第一跳输入必须是经典 SPL WSOL");
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(6);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(SystemProgram.transfer(swap.payer(), wsolAta, swap.amountInRaw()));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(buy(swap, feeRecipient,outputTokenProgram));
        return List.copyOf(result);
    }

    /**
     * 使用原生 SOL 买入：在同一笔交易内创建、充值、同步 WSOL，再执行三跳。
     * @param swap 以经典 SPL WSOL 为输入的三跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return WSOL 准备指令和三跳路由指令
     */
    public static List<TransactionInstruction> buyWithSol(ThreeHopRouteSwap swap,
                                                            PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        if (!WSOL_MINT.equals(swap.inputMint())
                || !TokenProgram.PROGRAM_ID.equals(swap.inputTokenProgram()))
            throw new IllegalArgumentException("原生 SOL 买入的第一跳输入必须是经典 SPL WSOL");
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(7);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(SystemProgram.transfer(swap.payer(), wsolAta, swap.amountInRaw()));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(buy(swap, feeRecipient));
        return List.copyOf(result);
    }
    public static List<TransactionInstruction> buyWithSol(ThreeHopRouteSwap swap,
                                                          PublicKey feeRecipient,
                                                          PublicKey outputTokenProgram) {
        Objects.requireNonNull(swap, "swap");
        if (!WSOL_MINT.equals(swap.inputMint())
                || !TokenProgram.PROGRAM_ID.equals(swap.inputTokenProgram()))
            throw new IllegalArgumentException("原生 SOL 买入的第一跳输入必须是经典 SPL WSOL");
        PublicKey wsolAta = ata(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID);
        List<TransactionInstruction> result = new ArrayList<>(7);
        result.add(createAta(swap.payer(), WSOL_MINT, TokenProgram.PROGRAM_ID));
        result.add(SystemProgram.transfer(swap.payer(), wsolAta, swap.amountInRaw()));
        result.add(new TransactionInstruction(TokenProgram.PROGRAM_ID,
                List.of(writable(wsolAta)), new byte[]{17}));
        result.addAll(buy(swap, feeRecipient,outputTokenProgram));
        return List.copyOf(result);
    }

    /**
     * 把单跳参数转换为旧版 RouteSwap，复用统一的指令构造路径。
     * @param swap 单跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 可选 ATA 创建指令和单跳路由指令
     */
    private static List<TransactionInstruction> exactIn(Swap swap, PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        if (!swap.leg().inputMint().equals(swap.inputMint())
                || !swap.leg().outputMint().equals(swap.outputMint()))
            throw new IllegalArgumentException("leg mint direction differs from Router swap");
        return exactIn(new RouteSwap(swap.payer(), List.of(swap.leg()),
                List.of(swap.inputTokenProgram(), swap.outputTokenProgram()),
                swap.amountInRaw(), swap.minOutRaw(), false, swap.createOutputAta()), feeRecipient);
    }

    /**
     * 构造固定金额多跳的可选 ATA 指令和 Router 主指令。
     * @param swap 固定金额路由参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 按链上执行顺序排列的指令
     */
    private static List<TransactionInstruction> exactIn(RouteSwap swap, PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        List<TransactionInstruction> result = new ArrayList<>();
        for (int i = 0; i < swap.legs().size(); i++) {
            boolean create = i == swap.legs().size() - 1
                    ? swap.createOutputAta() : swap.createIntermediateAtas();
            if (create) result.add(createAta(swap.payer(), swap.legs().get(i).outputMint(),
                    swap.tokenPrograms().get(i + 1)));
        }
        result.add(route(swap, feeRecipient));
        return List.copyOf(result);
    }

    /**
     * 仅构造单跳 Router 指令，不附带 ATA 创建、签名或发送。
     * @param swap 单跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 单跳 Router 主指令
     */
    public static TransactionInstruction route(Swap swap, PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        if (!swap.leg().inputMint().equals(swap.inputMint())
                || !swap.leg().outputMint().equals(swap.outputMint())) {
            throw new IllegalArgumentException("leg mint direction or amount differs from Router swap");
        }
        return route(new RouteSwap(swap.payer(), List.of(swap.leg()),
                List.of(swap.inputTokenProgram(), swap.outputTokenProgram()),
                swap.amountInRaw(), swap.minOutRaw(), false, swap.createOutputAta()), feeRecipient);
    }

    /**
     * 按标签 2 的链上格式序列化一至四跳固定金额路由。
     * @param swap 固定金额路由参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 标签 2 的 Router 主指令
     */
    public static TransactionInstruction route(RouteSwap swap, PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        PublicKey inputAta = ata(swap.payer(), swap.inputMint(), swap.inputTokenProgram());
        PublicKey outputAta = ata(swap.payer(), swap.outputMint(), swap.outputTokenProgram());
        PublicKey feeDestination = ata(feeRecipient, swap.inputMint(), swap.inputTokenProgram());
        PublicKey config = pda(ROUTER_PROGRAM, "config".getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream data = new ByteArrayOutputStream(18 + swap.legs().size() * 64);
        data.write(ROUTE_TAG);
        putU64(data, swap.amountInRaw());
        putU64(data, swap.minOutRaw());
        data.write(FEE_ASSET_TOKEN);
        data.write(swap.legs().size());

        // 前六个账户顺序必须与 Rust Route 指令一致，后面紧接各跳 DEX 账户。
        List<AccountMeta> accounts = new ArrayList<>();
        accounts.add(writableSigner(swap.payer()));
        accounts.add(readonly(config));
        accounts.add(writable(feeDestination));
        accounts.add(writable(inputAta));
        accounts.add(writable(outputAta));
        accounts.add(readonly(swap.inputTokenProgram()));
        LinkedHashSet<PublicKey> dexPrograms = new LinkedHashSet<>();
        for (RouterLeg leg : swap.legs()) {
            byte[] legData = leg.data();
            putKey(data, leg.programId());
            data.write(leg.accounts().size());
            data.write(legData.length & 0xff);
            data.write((legData.length >>> 8) & 0xff);
            data.writeBytes(legData);
            accounts.addAll(leg.accounts());
            dexPrograms.add(leg.programId());
        }
        // 各跳账户之后，按首次出现顺序补入去重后的 CPI 程序账户。
        for (PublicKey program : dexPrograms) accounts.add(readonly(program));
        return new TransactionInstruction(ROUTER_PROGRAM, accounts, data.toByteArray());
    }

    /**
     * 与 Rust build_dynamic_route_instruction 完全一致的 tag=3 数据和账户顺序。
     * 只有目标链上 Router 已部署支持 tag=3 的版本时，才能模拟或提交这类交易。
     * @param swap 已校验的动态两跳参数
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 不含 ATA 创建指令的 Router 主指令
     */
    public static TransactionInstruction routeDynamic(DynamicRouteSwap swap,
                                                       PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        PublicKey inputAta = ata(swap.payer(), swap.inputMint(), swap.inputTokenProgram());
        PublicKey quoteAta = ata(swap.payer(), swap.quoteMint(), swap.quoteTokenProgram());
        PublicKey outputAta = ata(swap.payer(), swap.outputMint(), swap.outputTokenProgram());
        PublicKey feeDestination = ata(feeRecipient, swap.inputMint(), swap.inputTokenProgram());
        PublicKey config = pda(ROUTER_PROGRAM, "config".getBytes(StandardCharsets.US_ASCII));
        RouterLeg first = swap.firstLeg();
        RouterLeg second = swap.secondLeg();
        ByteArrayOutputStream data = new ByteArrayOutputStream(
                97 + first.data().length + second.data().length);
        data.write(ROUTE_DYNAMIC_TAG);
        putU64(data, swap.amountInRaw());
        putU64(data, swap.minOutRaw());
        data.write(FEE_ASSET_TOKEN);
        data.write(2);
        putU64(data, swap.quoteMinOutRaw());

        // 固定账户包含额外的中间 quote ATA，链上会据此读取第一跳真实到账增量。
        List<AccountMeta> accounts = new ArrayList<>(
                9 + first.accounts().size() + second.accounts().size());
        accounts.add(writableSigner(swap.payer()));
        accounts.add(readonly(config));
        accounts.add(writable(feeDestination));
        accounts.add(writable(inputAta));
        accounts.add(writable(outputAta));
        accounts.add(readonly(swap.inputTokenProgram()));
        accounts.add(writable(quoteAta));
        for (RouterLeg leg : List.of(first, second)) {
            byte[] inner = leg.data();
            putKey(data, leg.programId());
            data.write(leg.accounts().size());
            data.write(inner.length & 0xff);
            data.write((inner.length >>> 8) & 0xff);
            data.writeBytes(inner);
            accounts.addAll(leg.accounts());
        }
        accounts.add(readonly(first.programId()));
        if (!first.programId().equals(second.programId()))
            accounts.add(readonly(second.programId()));
        return new TransactionInstruction(ROUTER_PROGRAM, accounts, data.toByteArray());
    }

    /**
     * 依照 Rust 的三跳格式编码；动态模式为标签 4，固定金额模式沿用标签 2。
     * Java API 使用占位量 1 创建后两跳，动态编码时写为 0，链上再替换为实际净到账量。
     * @param swap 三跳路径、最低输出与动态模式开关
     * @param feeRecipient Router 配置中的手续费接收方
     * @return 不含 ATA 创建指令的 Router 主指令
     */
    public static TransactionInstruction routeThreeHop(ThreeHopRouteSwap swap,
                                                         PublicKey feeRecipient) {
        Objects.requireNonNull(swap, "swap");
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        if (!swap.spendActualOutput()) {
            RouteSwap fixed = new RouteSwap(swap.payer(), swap.legs(),
                    List.of(swap.inputTokenProgram(), swap.firstTokenProgram(),
                            swap.secondTokenProgram(), swap.outputTokenProgram()),
                    swap.amountInRaw(), swap.minOutRaw(), swap.createIntermediateAtas(),
                    swap.createOutputAta());
            return route(fixed, feeRecipient);
        }
        PublicKey inputAta = ata(swap.payer(), swap.inputMint(), swap.inputTokenProgram());
        PublicKey outputAta = ata(swap.payer(), swap.outputMint(), swap.outputTokenProgram());
        PublicKey feeDestination = ata(feeRecipient, swap.inputMint(), swap.inputTokenProgram());
        PublicKey config = pda(ROUTER_PROGRAM, "config".getBytes(StandardCharsets.US_ASCII));
        List<RouterLeg> legs = swap.legs();
        ByteArrayOutputStream data = new ByteArrayOutputStream(35 + legs.size() * 75);
        data.write(ROUTE_DYNAMIC_THREE_TAG);
        putU64(data, swap.amountInRaw());
        putU64(data, swap.minOutRaw());
        data.write(FEE_ASSET_TOKEN);
        data.write(3);
        putU64(data, swap.firstMinOutRaw());
        putU64(data, swap.secondMinOutRaw());
        List<AccountMeta> accounts = new ArrayList<>();
        accounts.add(writableSigner(swap.payer()));
        accounts.add(readonly(config));
        accounts.add(writable(feeDestination));
        accounts.add(writable(inputAta));
        accounts.add(writable(outputAta));
        accounts.add(readonly(swap.inputTokenProgram()));
        accounts.add(writable(ata(swap.payer(), swap.firstIntermediateMint(), swap.firstTokenProgram())));
        accounts.add(writable(ata(swap.payer(), swap.secondIntermediateMint(), swap.secondTokenProgram())));
        LinkedHashSet<PublicKey> dexPrograms = new LinkedHashSet<>();
        for (int i = 0; i < legs.size(); i++) {
            RouterLeg leg = legs.get(i);
            byte[] inner = leg.data();
            if (i > 0) {
                int offset = dynamicLegLayout(leg).amountOffset();
                Arrays.fill(inner, offset, offset + 8, (byte) 0);
            }
            putKey(data, leg.programId());
            data.write(leg.accounts().size());
            data.write(inner.length & 0xff);
            data.write((inner.length >>> 8) & 0xff);
            data.writeBytes(inner);
            accounts.addAll(leg.accounts());
            dexPrograms.add(leg.programId());
        }
        for (PublicKey program : dexPrograms) accounts.add(readonly(program));
        return new TransactionInstruction(ROUTER_PROGRAM, accounts, data.toByteArray());
    }

    /**
     * 核对内层 DEX 指令的输入量与最低输出，防止记录值和实际编码不一致。
     * @param leg 待校验的 DEX 一跳
     */
    private static void checkInnerAmount(RouterLeg leg) {
        byte[] inner = leg.data();
        int offset = leg.programId().equals(RaydiumLegs.AMM_V4) ? 1 : 8;
        if (inner.length < offset + 16 || readU64(inner, offset) != leg.amountInRaw()
                || readU64(inner, offset + 8) < leg.minOutRaw())
            throw new IllegalArgumentException("DEX 指令金额或最低输出与 RouterLeg 不一致");
    }

    /**
     * 确认指定 ATA 已作为可写账户传入 CPI。
     * @param accounts 本跳的 CPI 账户列表
     * @param key 应为可写账户的 ATA 公钥
     * @return 找到可写 ATA 时为真
     */
    private static boolean hasWritable(List<AccountMeta> accounts, PublicKey key) {
        for (AccountMeta account : accounts)
            if (account.getPublicKey().equals(key) && account.isWritable()) return true;
        return false;
    }

    /**
     * 识别 Rust 链上 Router 支持的动态精确输入池型和账户索引。
     * @param leg 待识别的 DEX 一跳
     * @return 金额偏移以及输入、输出、mint、程序账户位置
     */
    private static DynamicLegLayout dynamicLegLayout(RouterLeg leg) {
        byte[] data = leg.data();
        if (leg.programId().equals(LaunchLabLegs.PROGRAM) && data.length == 32
                && matchesPrefix(data, LAUNCHLAB_BUY_EXACT_IN)
                && readU64(data, 24) == 0) {
            return new DynamicLegLayout(6, 5, 0, 8, 10, 12, 9, 11, false);
        } else if (leg.programId().equals(RaydiumLegs.CPMM) && data.length == 24
                && matchesPrefix(data, CPMM_SWAP_BASE_IN)) {
            return new DynamicLegLayout(4, 5, 0, 8, 10, 8, 11, 9, false);
        } else if (leg.programId().equals(PumpFunLegs.PROGRAM) && data.length == 24
                && (matchesPrefix(data, PUMPFUN_BUY_EXACT_QUOTE_IN_V2)
                || matchesPrefix(data, PUMPFUN_SELL_V2))) {
            boolean buy = matchesPrefix(data, PUMPFUN_BUY_EXACT_QUOTE_IN_V2);
            if (leg.accounts().size() < (buy ? 27 : 26))
                throw new IllegalArgumentException("Pump 内盘 V2 缺少完整的买卖账户");
            // 原生 SOL 配对内盘走 lamports，不会从 WSOL ATA 扣款，不能套用动态 ATA 模式。
            if (leg.accounts().get(2).getPublicKey().equals(WSOL_MINT))
                throw new IllegalArgumentException("Pump 内盘动态 V2 仅支持非 WSOL 的 SPL quote");
            return buy
                    ? new DynamicLegLayout(15, 14, 13, 8, 2, 4, 1, 3, false)
                    : new DynamicLegLayout(14, 15, 13, 8, 1, 3, 2, 4, false);
        } else if (leg.programId().equals(PumpAmmLegs.PROGRAM) && data.length == 25
                && matchesPrefix(data, PUMPSWAP_BUY_EXACT_QUOTE_IN)
                && Byte.toUnsignedInt(data[24]) <= 1) {
            return new DynamicLegLayout(6, 5, 1, 8, 4, 12, 3, 11, false);
        } else if (leg.programId().equals(PumpAmmLegs.PROGRAM) && data.length == 24
                && matchesPrefix(data, PUMPSWAP_SELL)) {
            // 与链上动态跳一致：用户 base ATA → 用户 quote ATA，金额位于第 8 字节。
            return new DynamicLegLayout(5, 6, 1, 8, 3, 11, 4, 12, false);
        }
        if (leg.programId().equals(RaydiumLegs.AMM_V4) && data.length == 17 && data[0] == 16)
            return new DynamicLegLayout(5, 6, 7, 1, NO_ACCOUNT, 0,
                    NO_ACCOUNT, 0, false);
        if (leg.programId().equals(RaydiumLegs.CLMM) && data.length == 41
                && matchesPrefix(data, CLMM_SWAP_V2) && data[40] == 1)
            return new DynamicLegLayout(3, 4, 0, 8, 11, NO_ACCOUNT,
                    12, NO_ACCOUNT, false);
        if (leg.programId().equals(MeteoraLegs.DLMM) && data.length == 28
                && matchesPrefix(data, METEORA_SWAP2)
                && data[24] == 0 && data[25] == 0 && data[26] == 0 && data[27] == 0)
            return new DynamicLegLayout(4, 5, 10, 8, 6, 11, 7, 12, true);
        if (leg.programId().equals(MeteoraLegs.DAMM_V2) && data.length == 25
                && matchesPrefix(data, METEORA_SWAP2) && data[24] == 0)
            return new DynamicLegLayout(2, 3, 8, 8, 6, 9, 7, 10, true);
        if (leg.programId().equals(OrcaLegs.WHIRLPOOL) && data.length == 43
                && matchesPrefix(data, CLMM_SWAP_V2) && data[40] == 1
                && (data[41] == 0 || data[41] == 1) && data[42] == 0) {
            boolean aToB = data[41] == 1;
            return new DynamicLegLayout(aToB ? 7 : 9, aToB ? 9 : 7, 3, 8,
                    aToB ? 5 : 6, aToB ? 0 : 1,
                    aToB ? 6 : 5, aToB ? 1 : 0, false);
        }
        throw new IllegalArgumentException("动态后续跳必须使用链上支持的精确输入池型");
    }

    /**
     * 按 Rust 链上逻辑校验动态后续跳的 DEX 类型、签名者、代币方向及账户顺序。
     * @param payer Router 交易付款和签名公钥
     * @param leg 待校验的 DEX 一跳
     * @param inputAta 本跳应使用的用户输入 ATA
     * @param outputAta 本跳应使用的用户输出 ATA
     * @param inputProgram 输入代币所属 Token Program
     * @param outputProgram 输出代币所属 Token Program
     * @param allowLaunchLab 是否允许 LaunchLab 买入作为本跳
     */
    private static void checkDynamicLeg(PublicKey payer, RouterLeg leg,
                                        PublicKey inputAta, PublicKey outputAta,
                                        PublicKey inputProgram, PublicKey outputProgram,
                                        boolean allowLaunchLab) {
        if (!allowLaunchLab && leg.programId().equals(LaunchLabLegs.PROGRAM))
            throw new IllegalArgumentException("LaunchLab 只能作为动态路由的最后一跳");
        DynamicLegLayout layout = dynamicLegLayout(leg);
        List<AccountMeta> accounts = leg.accounts();
        int[] required = {layout.inputAccount(), layout.outputAccount(), layout.signer(),
                layout.inputMint(), layout.inputProgram(), layout.outputMint(), layout.outputProgram()};
        for (int index : required)
            if (index != NO_ACCOUNT && index >= accounts.size())
                throw new IllegalArgumentException("动态跳缺少必需的 DEX 账户");
//        if (!accounts.get(layout.signer()).getPublicKey().equals(payer)
//                || !accounts.get(layout.signer()).isSigner()
//                || !accounts.get(layout.inputAccount()).getPublicKey().equals(inputAta)
//                || !accounts.get(layout.inputAccount()).isWritable()
//                || !accounts.get(layout.outputAccount()).getPublicKey().equals(outputAta)
//                || !accounts.get(layout.outputAccount()).isWritable())
//            throw new IllegalArgumentException("动态跳的签名者或输入、输出 ATA 不匹配");
//        if (layout.pairedMints()) {
//            boolean forward = matchesAccount(accounts, layout.inputMint(), leg.inputMint())
//                    && matchesAccount(accounts, layout.outputMint(), leg.outputMint())
//                    && matchesAccount(accounts, layout.inputProgram(), inputProgram)
//                    && matchesAccount(accounts, layout.outputProgram(), outputProgram);
//            boolean reverse = matchesAccount(accounts, layout.outputMint(), leg.inputMint())
//                    && matchesAccount(accounts, layout.inputMint(), leg.outputMint())
//                    && matchesAccount(accounts, layout.outputProgram(), inputProgram)
//                    && matchesAccount(accounts, layout.inputProgram(), outputProgram);
//            if (!forward && !reverse)
//                throw new IllegalArgumentException("动态跳的池侧 mint 或 Token Program 不匹配");
//        }
//        else if (!matchesAccount(accounts, layout.inputMint(), leg.inputMint())
//                || !matchesAccount(accounts, layout.outputMint(), leg.outputMint())
//                || !matchesAccount(accounts, layout.inputProgram(), inputProgram)
//                || !matchesAccount(accounts, layout.outputProgram(), outputProgram)) {
//            throw new IllegalArgumentException("动态跳的 mint 或 Token Program 不匹配");
//        }
    }

    /**
     * 对可选的 DEX 账户索引执行公钥比较。
     * @param accounts 本跳账户列表
     * @param index 本跳账户索引，负一表示该 DEX 不单独提供此账户
     * @param expected 期望的 mint 或 Token Program 公钥
     * @return 索引不存在或对应公钥相同时为真
     */
    private static boolean matchesAccount(List<AccountMeta> accounts, int index, PublicKey expected) {
        return index == NO_ACCOUNT || accounts.get(index).getPublicKey().equals(expected);
    }

    /**
     * 比较内层 DEX 指令的判别符前缀。
     * @param data 内层 DEX 指令数据
     * @param prefix 期望的判别符
     * @return 前缀一致时为真
     */
    private static boolean matchesPrefix(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++)
            if (data[i] != prefix[i]) return false;
        return true;
    }

    /**
     * 构造幂等 ATA 创建指令；只负责构造，不负责检查或提交。
     * @param payer 支付 ATA 租金的交易用户
     * @param mint ATA 对应的代币 mint
     * @param tokenProgram mint 所属的 Token Program
     * @return 幂等创建 ATA 的指令
     */
    public static TransactionInstruction createAta(PublicKey payer, PublicKey mint,
                                                     PublicKey tokenProgram) {
        return new TransactionInstruction(AssociatedTokenProgram.PROGRAM_ID,
                List.of(writableSigner(payer),
                        writable(ata(payer, mint, tokenProgram)),
                        readonly(payer),
                        readonly(mint),
                        readonly(SystemProgram.PROGRAM_ID),
                        readonly(tokenProgram)
        ), new byte[]{1});
    }

    /**
     * 根据 owner、Token Program 和 mint 推导对应的关联代币账户地址。
     * @param owner 关联代币账户所有者
     * @param mint 代币 mint
     * @param tokenProgram mint 所属的 Token Program
     * @return 对应 ATA 的公钥
     */
    public static PublicKey ata(PublicKey owner, PublicKey mint, PublicKey tokenProgram) {
        //tokenProgram(tokenProgram);
        AtaCacheKey key = new AtaCacheKey(owner, mint, tokenProgram);
        Map<AtaCacheKey, byte[]> cache = ATA_CACHE.get();
        byte[] cached = cache.get(key);
        if (cached != null) return new PublicKey(cached);
        PublicKey address = PublicKey.findProgramAddress(List.of(owner.toByteArray(), tokenProgram.toByteArray(),
                mint.toByteArray()), AssociatedTokenProgram.PROGRAM_ID).getAddress();
        cache.put(key, address.toByteArray().clone());
        return address;
    }

    /**
     * 根据程序地址和种子推导程序派生地址。
     * @param program 派生地址所属程序
     * @param seeds 依序参与派生的种子
     * @return 派生出的程序地址
     */
    static PublicKey pda(PublicKey program, byte[]... seeds) {
        return PublicKey.findProgramAddress(Arrays.asList(seeds), program).getAddress();
    }

    /**
     * 拒绝零或负数的原始交易数量。
     * @param value 待检查的最小单位数量
     * @param name 用于错误信息的参数名称
     */
    static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be 1..Long.MAX_VALUE raw units");
    }

    /**
     * 仅允许经典 SPL Token 或 Token-2022 程序。
     * @param program 待检查的 Token Program 公钥
     */
    static void tokenProgram(PublicKey program) {
        if (!program.equals(TokenProgram.PROGRAM_ID) && !program.equals(TokenProgram.TOKEN_2022)) {
            throw new IllegalArgumentException("not an SPL Token / Token-2022 program: " + program);
        }
    }

    /**
     * 构造非签名的可写账户元数据。
     * @param key 账户公钥
     * @return 可写非签名账户元数据
     */
    static AccountMeta writable(PublicKey key) { return new AccountMeta(key, false, true); }
    /**
     * 构造非签名的只读账户元数据。
     * @param key 账户公钥
     * @return 只读非签名账户元数据
     */
    static AccountMeta readonly(PublicKey key) { return new AccountMeta(key, false, false); }
    /**
     * 构造只读签名账户元数据。
     * @param key 账户公钥
     * @return 只读签名账户元数据
     */
    static AccountMeta readonlySigner(PublicKey key) { return new AccountMeta(key, true, false); }
    /**
     * 构造可写签名账户元数据。
     * @param key 账户公钥
     * @return 可写签名账户元数据
     */
    static AccountMeta writableSigner(PublicKey key) { return new AccountMeta(key, true, true); }
    /**
     * 把 Base58 文本解析为公钥。
     * @param base58 公钥的 Base58 文本
     * @return 解析后的公钥
     */
    static PublicKey key(String base58) { return new PublicKey(base58); }

    /**
     * 按原始 32 字节写入公钥。
     * @param out 目标字节流
     * @param key 待写入的公钥
     */
    static void putKey(ByteArrayOutputStream out, PublicKey key) { out.writeBytes(key.toByteArray()); }

    /**
     * 将非负 long 按无符号 u64 小端序写入。
     * @param out 目标字节流
     * @param value 待写入的非负整数
     */
    static void putU64(ByteArrayOutputStream out, long value) {
        if (value < 0) throw new IllegalArgumentException("u64 value must fit a non-negative Java long");
        for (int i = 0; i < 8; i++) out.write((int) (value >>> (8 * i)) & 0xff);
    }

    /**
     * 从内层 DEX 指令中读取小端 u64，用于本地一致性校验。
     * @param data 内层 DEX 指令数据
     * @param offset 金额字段在指令数据中的字节偏移
     * @return 读出的原始金额
     */
    private static long readU64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value |= (data[offset + i] & 0xffL) << (8 * i);
        return value;
    }

    public static void main(String[] args) {
        PublicKey sourceAta = ata(new PublicKey("EaN1vRcBAWuFTGebN5v4zih2xfaTvAEJ5V2u1kWh98ss"), new PublicKey("So11111111111111111111111111111111111111112"), new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"));
        var v1 = ata(new PublicKey("EaN1vRcBAWuFTGebN5v4zih2xfaTvAEJ5V2u1kWh98ss"), new PublicKey("So11111111111111111111111111111111111111112"), new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"));
        System.out.println(sourceAta);
        System.out.println(v1);
    }
}
