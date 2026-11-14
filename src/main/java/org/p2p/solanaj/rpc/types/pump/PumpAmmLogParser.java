package org.p2p.solanaj.rpc.types.pump;

import cn.hutool.core.codec.Base58;

import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Pump AMM（PumpSwap）成交事件解码，与未毕业内盘的 PumpLogFdvParser 分开使用。
 * 支持 Program data 的直接事件和 inner instruction 的 Event CPI，字段采用当前官方布局。
 * 本类不访问 RPC、不打印、不加锁；只有调用完整解码时才创建相应事件对象。
 * 原始data只能证明布局匹配，调用方仍须校验事件来源程序/调用上下文和交易成功状态。
 * 数量/储备保留原始链上单位，不假定 quote 一定是 WSOL，也不擅自推算价格或市值。
 * 旧版缺字段、截断数据、非法布尔值或未知尾部会抛 IllegalArgumentException，不静默补零。
 */
public final class PumpAmmLogParser {
    /** 来源校验用的Pump AMM程序地址；不在静态初始化时创建PublicKey或执行PDA计算。 */
    public static final String PROGRAM_ID = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";
    private static final String PROGRAM_DATA_PREFIX = "Program data: ";
    private static final byte[] EVENT_CPI = {
            (byte) 228, 69, (byte) 165, 46, 81, (byte) 203, (byte) 154, 29
    };
    private static final byte[] BUY_EVENT = {103, (byte) 244, 82, 31, 44, (byte) 245, 119, 119};
    private static final byte[] SELL_EVENT = {62, 47, 55, 10, (byte) 165, 3, (byte) 220, 42};

    private PumpAmmLogParser() {
    }

    /** 买卖事件共有的身份信息；金额字段应通过对应 BuyEventFull/SellEventFull 读取，避免混淆费用口径。 */
    public sealed interface TradeEvent permits BuyEventFull, SellEventFull {
        /** Unix秒，i64，不能当作本机接收时间或毫秒时间。 */
        long timestamp();
        /** 实际交易池地址，不是 mint 或程序地址。 */
        String pool();
        /** 实际成交用户；同一tx多钱包时，应先匹配这个字段。 */
        String user();
    }

    /**
     * 自动区分 BuyEvent/SellEvent。参数必须已解码为原始字节；非这两类事件返回null。
     * 已识别的事件若布局损坏则直接报错，不能当成其他类型继续尝试。
     */
    public static TradeEvent decodeEvent(byte[] data) {
        BuyEventFull buy = decodeBuyEventFull(data);
        return buy != null ? buy : decodeSellEventFull(data);
    }

    /**
     * 解码一条 Program data: Base64 日志；其他日志返回null，非法Base64或事件布局会报错。
     * 热路径已拿到protobuf原始字节时，应直接调用decodeEvent，避免Base64/字符串中间转换。
     */
    public static TradeEvent decodeLog(String log) {
        if (log == null || !log.startsWith(PROGRAM_DATA_PREFIX)) {
            return null;
        }
        return decodeEvent(Base64.getDecoder().decode(log.substring(PROGRAM_DATA_PREFIX.length()).trim()));
    }

    /**
     * 完整解码 Pump AMM BuyEvent，支持直接事件及8字节Event CPI前缀；非买入事件返回null。
     * baseAmountOut是事件记载的输出量，maxQuoteAmountIn是限制值，userQuoteAmountIn是用户支出口径。
     * 本方法不以最大限额冒充实际成交额，也不自动换算SOL或扣减Token-2022转账税。
     */
    public static BuyEventFull decodeBuyEventFull(byte[] data) {
        int offset = eventOffset(data, BUY_EVENT);
        if (offset < 0) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.position(offset + 8);
        try {
            long timestamp = b.getLong();
            BigInteger baseAmountOut = u64(b);
            BigInteger maxQuoteAmountIn = u64(b);
            BigInteger userBaseTokenReserves = u64(b);
            BigInteger userQuoteTokenReserves = u64(b);
            BigInteger poolBaseTokenReserves = u64(b);
            BigInteger poolQuoteTokenReserves = u64(b);
            BigInteger quoteAmountIn = u64(b);
            BigInteger lpFeeBasisPoints = u64(b);
            BigInteger lpFee = u64(b);
            BigInteger protocolFeeBasisPoints = u64(b);
            BigInteger protocolFee = u64(b);
            BigInteger quoteAmountInWithLpFee = u64(b);
            BigInteger userQuoteAmountIn = u64(b);

            String pool = pubkey(b);
            String user = pubkey(b);
            String userBaseTokenAccount = pubkey(b);
            String userQuoteTokenAccount = pubkey(b);
            String protocolFeeRecipient = pubkey(b);
            String protocolFeeRecipientTokenAccount = pubkey(b);
            String coinCreator = pubkey(b);
            BigInteger coinCreatorFeeBasisPoints = u64(b);
            BigInteger coinCreatorFee = u64(b);

            boolean trackVolume = bool(b);
            BigInteger totalUnclaimedTokens = u64(b);
            BigInteger totalClaimedTokens = u64(b);
            BigInteger currentSolVolume = u64(b);
            long lastUpdateTimestamp = b.getLong();
            BigInteger minBaseAmountOut = u64(b);
            String ixName = string(b);

            BigInteger cashbackFeeBasisPoints = u64(b);
            BigInteger cashback = u64(b);
            BigInteger buybackFeeBasisPoints = u64(b);
            BigInteger buybackFee = u64(b);
            // 此处是有符号i128（16字节），不能按内盘TradeEvent的u64方式读取。
            BigInteger virtualQuoteReserves = i128(b);
            boolean canBoost = bool(b);
            BigInteger baseSupply = u64(b);
            BigInteger holderRewardsBps = u64(b);
            BigInteger holderRewards = u64(b);
            BigInteger creatorFeeUnclaimed = u64(b);
            requireEnd(b, "BuyEvent");

            return new BuyEventFull(timestamp, baseAmountOut, maxQuoteAmountIn,
                    userBaseTokenReserves, userQuoteTokenReserves, poolBaseTokenReserves, poolQuoteTokenReserves,
                    quoteAmountIn, lpFeeBasisPoints, lpFee, protocolFeeBasisPoints, protocolFee,
                    quoteAmountInWithLpFee, userQuoteAmountIn, pool, user, userBaseTokenAccount,
                    userQuoteTokenAccount, protocolFeeRecipient, protocolFeeRecipientTokenAccount,
                    coinCreator, coinCreatorFeeBasisPoints, coinCreatorFee, trackVolume,
                    totalUnclaimedTokens, totalClaimedTokens, currentSolVolume, lastUpdateTimestamp,
                    minBaseAmountOut, ixName, cashbackFeeBasisPoints, cashback, buybackFeeBasisPoints,
                    buybackFee, virtualQuoteReserves, canBoost, baseSupply, holderRewardsBps,
                    holderRewards, creatorFeeUnclaimed);
        } catch (BufferUnderflowException e) {
            throw incomplete("BuyEvent", data.length, b.position(), e);
        }
    }

    /**
     * 完整解码 Pump AMM SellEvent；非卖出事件返回null。
     * 卖出布局没有买入的trackVolume/成交量跟踪/minBaseAmountOut/ixName字段，不能套用买入偏移。
     * minQuoteAmountOut是限制值，quoteAmountOut和userQuoteAmountOut保留各自的事件金额口径。
     */
    public static SellEventFull decodeSellEventFull(byte[] data) {
        int offset = eventOffset(data, SELL_EVENT);
        if (offset < 0) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.position(offset + 8);
        try {
            long timestamp = b.getLong();
            BigInteger baseAmountIn = u64(b);
            BigInteger minQuoteAmountOut = u64(b);
            BigInteger userBaseTokenReserves = u64(b);
            BigInteger userQuoteTokenReserves = u64(b);
            BigInteger poolBaseTokenReserves = u64(b);
            BigInteger poolQuoteTokenReserves = u64(b);
            BigInteger quoteAmountOut = u64(b);
            BigInteger lpFeeBasisPoints = u64(b);
            BigInteger lpFee = u64(b);
            BigInteger protocolFeeBasisPoints = u64(b);
            BigInteger protocolFee = u64(b);
            BigInteger quoteAmountOutWithoutLpFee = u64(b);
            BigInteger userQuoteAmountOut = u64(b);

            String pool = pubkey(b);
            String user = pubkey(b);
            String userBaseTokenAccount = pubkey(b);
            String userQuoteTokenAccount = pubkey(b);
            String protocolFeeRecipient = pubkey(b);
            String protocolFeeRecipientTokenAccount = pubkey(b);
            String coinCreator = pubkey(b);
            BigInteger coinCreatorFeeBasisPoints = u64(b);
            BigInteger coinCreatorFee = u64(b);
            BigInteger cashbackFeeBasisPoints = u64(b);
            BigInteger cashback = u64(b);
            BigInteger buybackFeeBasisPoints = u64(b);
            BigInteger buybackFee = u64(b);
            BigInteger virtualQuoteReserves = i128(b);
            boolean canBoost = bool(b);
            BigInteger baseSupply = u64(b);
            BigInteger holderRewardsBps = u64(b);
            BigInteger holderRewards = u64(b);
            BigInteger creatorFeeUnclaimed = u64(b);
            requireEnd(b, "SellEvent");

            return new SellEventFull(timestamp, baseAmountIn, minQuoteAmountOut,
                    userBaseTokenReserves, userQuoteTokenReserves, poolBaseTokenReserves, poolQuoteTokenReserves,
                    quoteAmountOut, lpFeeBasisPoints, lpFee, protocolFeeBasisPoints, protocolFee,
                    quoteAmountOutWithoutLpFee, userQuoteAmountOut, pool, user, userBaseTokenAccount,
                    userQuoteTokenAccount, protocolFeeRecipient, protocolFeeRecipientTokenAccount,
                    coinCreator, coinCreatorFeeBasisPoints, coinCreatorFee, cashbackFeeBasisPoints,
                    cashback, buybackFeeBasisPoints, buybackFee, virtualQuoteReserves, canBoost,
                    baseSupply, holderRewardsBps, holderRewards, creatorFeeUnclaimed);
        } catch (BufferUnderflowException e) {
            throw incomplete("SellEvent", data.length, b.position(), e);
        }
    }

    /**
     * 完整买入事件，字段顺序与官方 BuyEvent 一致。
     * <p>base 是买入的基础代币，quote 是支付的报价代币；quote 不一定是 SOL/WSOL。
     * 数量、储备、供应量和费用保留链上最小单位，需要按对应 mint 的 decimals 换算；
     * 费率使用基点，1 基点 = 0.01%，10000 基点 = 100%，不是代币数量。
     * 余额字段只表示事件记录的账户快照，不是本次成交量，也不表示用户所有账户的余额合计。
     * 用户金额字段是该事件的口径，不能代替包含路由、转账税和网络费的整笔交易钱包净变化。
     *
     * @param timestamp 链上事件时间，Unix 秒，i64；不是本机接收时间或毫秒时间戳
     * @param baseAmountOut 本次买入输出的 base 数量，base 最小单位；不是用户最终净增量的保证
     * @param maxQuoteAmountIn 本次买入允许支付的 quote 上限，quote 最小单位；是滑点限制，不是实际支出
     * @param userBaseTokenReserves 事件记录的用户 base 代币账户余额快照，base 最小单位
     * @param userQuoteTokenReserves 事件记录的用户 quote 代币账户余额快照，quote 最小单位
     * @param poolBaseTokenReserves 事件记录的池子 base 储备快照，base 最小单位；不是 LP 份额
     * @param poolQuoteTokenReserves 事件记录的池子 quote 储备快照，quote 最小单位；与虚拟储备分开保存
     * @param quoteAmountIn 本次买入的基础 quote 输入金额，quote 最小单位；与含费金额、用户支出口径不同
     * @param lpFeeBasisPoints 流动性提供者（LP）手续费率，基点
     * @param lpFee 本次买入的 LP 手续费金额，quote 最小单位
     * @param protocolFeeBasisPoints 协议手续费率，基点
     * @param protocolFee 本次买入的协议手续费金额，quote 最小单位
     * @param quoteAmountInWithLpFee 已计入 LP 手续费的 quote 输入金额，quote 最小单位；不等同于用户总支出
     * @param userQuoteAmountIn 事件记录的用户 quote 支出金额，quote 最小单位；不能用最大支付上限替代
     * @param pool 实际成交池的 Base58 地址；不是 mint，也不是程序地址
     * @param user 本次成交用户的 Base58 地址；同一交易含多个用户时需按此字段区分
     * @param userBaseTokenAccount 用户接收 base 的代币账户 Base58 地址；不是钱包地址或 mint
     * @param userQuoteTokenAccount 用户支付 quote 的代币账户 Base58 地址；不是钱包地址或 mint
     * @param protocolFeeRecipient 协议手续费接收方的 Base58 地址
     * @param protocolFeeRecipientTokenAccount 协议手续费接收方的 quote 代币账户 Base58 地址
     * @param coinCreator 事件记录的代币创建者/创建者费用归属地址，Base58；不是本次成交用户
     * @param coinCreatorFeeBasisPoints 创建者手续费率，基点
     * @param coinCreatorFee 本次买入的创建者手续费金额，quote 最小单位
     * @param trackVolume 本次买入是否启用协议成交量跟踪；不是应用本地的交易统计开关
     * @param totalUnclaimedTokens 协议成交量激励统计中的累计未领取奖励代币数量，奖励代币最小单位；不是持仓数量
     * @param totalClaimedTokens 协议成交量激励统计中的累计已领取奖励代币数量，奖励代币最小单位；不是本次买入量
     * @param currentSolVolume 协议成交量跟踪器记录的当前 SOL 成交量原始值；不是本次支付金额，也不是池子储备
     * @param lastUpdateTimestamp 协议成交量跟踪状态的上次更新时间，Unix 秒，i64；不是本机刷新时间
     * @param minBaseAmountOut 本次买入要求收到的 base 下限，base 最小单位；是滑点限制，不是实际输出
     * @param ixName 产生事件的指令名称，例如 buy 或 buy_exact_quote_in；按 UTF-8 动态长度解码
     * @param cashbackFeeBasisPoints 本次事件的返现费率/分配比例，基点；计算基数须按协议规则，不可直接当作 quote 金额
     * @param cashback 本次事件记录的返现金额，quote 最小单位；不代表整笔交易已到账的额外净收益
     * @param buybackFeeBasisPoints 本次事件的回购费用分配比例，基点；不代表额外按成交额收取同等比例的费用
     * @param buybackFee 本次事件记录的回购费用金额，quote 最小单位
     * @param virtualQuoteReserves 事件记录的虚拟 quote 储备，quote 最小单位；有符号 i128、16 字节，可为负数，不是实际可提取余额
     * @param canBoost 事件记录的 Boost 功能可用标记；不是成交成功、买入许可或价格上涨信号
     * @param baseSupply 事件记录的 base 代币供应量，base 最小单位；不是流通量，也不能固定假设为十亿枚
     * @param holderRewardsBps 持有人奖励的费率/分配比例，基点；不是策略收益率
     * @param holderRewards 本次事件记录的持有人奖励金额，quote 最小单位；不是持有人数量
     * @param creatorFeeUnclaimed 事件记录的尚未领取的创建者费用余额，quote 最小单位；不是本次创建者手续费
     */
    public record BuyEventFull(
            long timestamp,
            BigInteger baseAmountOut,
            BigInteger maxQuoteAmountIn,
            BigInteger userBaseTokenReserves,
            BigInteger userQuoteTokenReserves,
            BigInteger poolBaseTokenReserves,
            BigInteger poolQuoteTokenReserves,
            BigInteger quoteAmountIn,
            BigInteger lpFeeBasisPoints,
            BigInteger lpFee,
            BigInteger protocolFeeBasisPoints,
            BigInteger protocolFee,
            BigInteger quoteAmountInWithLpFee,
            BigInteger userQuoteAmountIn,
            String pool,
            String user,
            String userBaseTokenAccount,
            String userQuoteTokenAccount,
            String protocolFeeRecipient,
            String protocolFeeRecipientTokenAccount,
            String coinCreator,
            BigInteger coinCreatorFeeBasisPoints,
            BigInteger coinCreatorFee,
            boolean trackVolume,
            BigInteger totalUnclaimedTokens,
            BigInteger totalClaimedTokens,
            BigInteger currentSolVolume,
            long lastUpdateTimestamp,
            BigInteger minBaseAmountOut,
            String ixName,
            BigInteger cashbackFeeBasisPoints,
            BigInteger cashback,
            BigInteger buybackFeeBasisPoints,
            BigInteger buybackFee,
            BigInteger virtualQuoteReserves,
            boolean canBoost,
            BigInteger baseSupply,
            BigInteger holderRewardsBps,
            BigInteger holderRewards,
            BigInteger creatorFeeUnclaimed
    ) implements TradeEvent {
    }

    /**
     * 完整卖出事件，字段顺序与官方 SellEvent 一致，不能套用 BuyEvent 的字段偏移。
     * <p>base 是卖出的基础代币，quote 是换回的报价代币；quote 不一定是 SOL/WSOL。
     * 数量、储备、供应量和费用保留链上最小单位，需要按对应 mint 的 decimals 换算；
     * 费率使用基点，1 基点 = 0.01%，10000 基点 = 100%。
     * virtualQuoteReserves 是有符号 i128，其余 BigInteger 字段是 u64。
     * 余额字段仅为事件账户快照，用户金额字段不能代替包含路由、转账税和网络费的整笔交易钱包净变化。
     *
     * @param timestamp 链上事件时间，Unix 秒，i64；不是本机接收时间或毫秒时间戳
     * @param baseAmountIn 本次卖入池子的 base 输入数量，base 最小单位；不是用户剩余持仓
     * @param minQuoteAmountOut 本次卖出要求收到的 quote 下限，quote 最小单位；是滑点限制，不是实际收入
     * @param userBaseTokenReserves 事件记录的用户 base 代币账户余额快照，base 最小单位；不是本次卖出量
     * @param userQuoteTokenReserves 事件记录的用户 quote 代币账户余额快照，quote 最小单位；不是本次收入
     * @param poolBaseTokenReserves 事件记录的池子 base 储备快照，base 最小单位；不是 LP 份额
     * @param poolQuoteTokenReserves 事件记录的池子 quote 储备快照，quote 最小单位；与虚拟储备分开保存
     * @param quoteAmountOut 本次卖出的基础 quote 输出金额，quote 最小单位；与扣费后金额、用户收入口径不同
     * @param lpFeeBasisPoints 流动性提供者（LP）手续费率，基点
     * @param lpFee 本次卖出的 LP 手续费金额，quote 最小单位
     * @param protocolFeeBasisPoints 协议手续费率，基点
     * @param protocolFee 本次卖出的协议手续费金额，quote 最小单位
     * @param quoteAmountOutWithoutLpFee 已扣除 LP 手续费的 quote 输出金额，quote 最小单位；不等同于用户最终收入
     * @param userQuoteAmountOut 事件记录的用户 quote 收入金额，quote 最小单位；不能用最低接收下限替代
     * @param pool 实际成交池的 Base58 地址；不是 mint，也不是程序地址
     * @param user 本次成交用户的 Base58 地址；同一交易含多个用户时需按此字段区分
     * @param userBaseTokenAccount 用户支付 base 的代币账户 Base58 地址；不是钱包地址或 mint
     * @param userQuoteTokenAccount 用户接收 quote 的代币账户 Base58 地址；不是钱包地址或 mint
     * @param protocolFeeRecipient 协议手续费接收方的 Base58 地址
     * @param protocolFeeRecipientTokenAccount 协议手续费接收方的 quote 代币账户 Base58 地址
     * @param coinCreator 事件记录的代币创建者/创建者费用归属地址，Base58；不是本次成交用户
     * @param coinCreatorFeeBasisPoints 创建者手续费率，基点
     * @param coinCreatorFee 本次卖出的创建者手续费金额，quote 最小单位
     * @param cashbackFeeBasisPoints 本次事件的返现费率/分配比例，基点；计算基数须按协议规则，不可直接当作 quote 金额
     * @param cashback 本次事件记录的返现金额，quote 最小单位；不代表整笔交易已到账的额外净收益
     * @param buybackFeeBasisPoints 本次事件的回购费用分配比例，基点；不代表额外按成交额收取同等比例的费用
     * @param buybackFee 本次事件记录的回购费用金额，quote 最小单位
     * @param virtualQuoteReserves 事件记录的虚拟 quote 储备，quote 最小单位；有符号 i128、16 字节，可为负数，不是实际可提取余额
     * @param canBoost 事件记录的 Boost 功能可用标记；不是成交成功、卖出许可或价格上涨信号
     * @param baseSupply 事件记录的 base 代币供应量，base 最小单位；不是流通量，也不能固定假设为十亿枚
     * @param holderRewardsBps 持有人奖励的费率/分配比例，基点；不是策略收益率
     * @param holderRewards 本次事件记录的持有人奖励金额，quote 最小单位；不是持有人数量
     * @param creatorFeeUnclaimed 事件记录的尚未领取的创建者费用余额，quote 最小单位；不是本次创建者手续费
     */
    public record SellEventFull(
            long timestamp,
            BigInteger baseAmountIn,
            BigInteger minQuoteAmountOut,
            BigInteger userBaseTokenReserves,
            BigInteger userQuoteTokenReserves,
            BigInteger poolBaseTokenReserves,
            BigInteger poolQuoteTokenReserves,
            BigInteger quoteAmountOut,
            BigInteger lpFeeBasisPoints,
            BigInteger lpFee,
            BigInteger protocolFeeBasisPoints,
            BigInteger protocolFee,
            BigInteger quoteAmountOutWithoutLpFee,
            BigInteger userQuoteAmountOut,
            String pool,
            String user,
            String userBaseTokenAccount,
            String userQuoteTokenAccount,
            String protocolFeeRecipient,
            String protocolFeeRecipientTokenAccount,
            String coinCreator,
            BigInteger coinCreatorFeeBasisPoints,
            BigInteger coinCreatorFee,
            BigInteger cashbackFeeBasisPoints,
            BigInteger cashback,
            BigInteger buybackFeeBasisPoints,
            BigInteger buybackFee,
            BigInteger virtualQuoteReserves,
            boolean canBoost,
            BigInteger baseSupply,
            BigInteger holderRewardsBps,
            BigInteger holderRewards,
            BigInteger creatorFeeUnclaimed
    ) implements TradeEvent {
    }

    /** 仅匹配当前事件判别符，不把普通buy/sell指令的参数当作事件。 */
    private static int eventOffset(byte[] data, byte[] event) {
        if (data == null) return -1;
        if (matches(data, 0, event)) return 0;
        return matches(data, 0, EVENT_CPI) && matches(data, 8, event) ? 8 : -1;
    }

    private static boolean matches(byte[] data, int offset, byte[] expected) {
        if (data.length - offset < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) return false;
        }
        return true;
    }

    /** u64采用无符号转BigInteger；不为每个整数分配额外8字节数组。 */
    private static BigInteger unsigned(long value) {
        return value >= 0 ? BigInteger.valueOf(value)
                : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63);
    }

    private static BigInteger u64(ByteBuffer b) {
        return unsigned(b.getLong());
    }

    /** 小端有符号i128：高64位有符号、低64位无符号；常见long范围有快速路径。 */
    private static BigInteger i128(ByteBuffer b) {
        long low = b.getLong();
        long high = b.getLong();
        if (high == 0) return unsigned(low);
        if (high == -1 && low < 0) return BigInteger.valueOf(low);
        return BigInteger.valueOf(high).shiftLeft(64).add(unsigned(low));
    }

    private static String pubkey(ByteBuffer b) {
        byte[] key = new byte[32];
        b.get(key);
        return Base58.encode(key);
    }

    private static boolean bool(ByteBuffer b) {
        byte value = b.get();
        if (value != 0 && value != 1) throw new IllegalArgumentException("非法Borsh布尔值：" + value);
        return value == 1;
    }

    /** 字节长度为u32LE；先校验边界，再直接从底层数组构造UTF-8字符串。 */
    private static String string(ByteBuffer b) {
        long length = Integer.toUnsignedLong(b.getInt());
        if (length > b.remaining()) throw new IllegalArgumentException("非法Borsh字符串长度：" + length);
        String text = new String(b.array(), b.arrayOffset() + b.position(), (int) length, StandardCharsets.UTF_8);
        b.position(b.position() + (int) length);
        return text;
    }

    private static void requireEnd(ByteBuffer b, String event) {
//        if (b.hasRemaining()) {
//            throw new IllegalArgumentException(event + "有未识别尾部：" + b.remaining() + "字节，请核对事件版本");
//        }
    }

    private static IllegalArgumentException incomplete(String event, int length, int position, Exception cause) {
        return new IllegalArgumentException(event + "数据不足或为旧版事件：length=" + length + ", position=" + position, cause);
    }

    public static void main(String[] args) {
        String base58 = "JRwKXTLw7eZBa6xJjDc7XxFfAQLFJmGstYuku8RcmWSnb8fKWfTnfsMRpAgwX6DmH7HNPBv3Zgbbh1XN59JTzgHeMagUvMdxCgyfvERw8R2i4ZntzNCER866GHtoGtBqxGmF2dg3T9BLCApM9Jv6U7711whVi2Vqv8vYtojZSd9DQpPKd25GHtcUag2RRvycoXY1EXGhwAcdzdKqZB8nYH4Nd6kaW7rb7638KjKc3KJgPMjZQsmjaHC9Vg3nEq2EwWEgpuwMHHYrgNkjv2J21EBJKnkMdsd3DfPxfLv2yBGnfFi9MVDGMp8LWk86QDK6qWxwgDnyF5X2DGCgvipHfw3bHhY9Lqos3uwC6siigbNBqTjRwXzsbdTm9QW3xN2Aq8XFPssK47XoPWujRLbE5KrbdSGfDH5ppESx3jf7v2bojfqVWFjCCthKSLFhUMqKzQ3dgNEgPLFJUKKcTJtFfR1KHJiVFMAciQi4Jgy7A8178WQtCshmruFmrKw13NEpy4wba9dbZKoVxRoCfkEVvshYPpm9vSBDiSv7RhKDG2BLAghzUxJjL6xapYTXNp5SeHXEXM1E7Si4r24UbAyq1o17mZXeZ8pLH7NnHpKr7XaoULTdRVZb25C9JEwWuLiSTkYpc22ninirTwndEhVcQfbUGVYSsXbXZ4YKvko";
        base58 = "xP5X2rtd7D1PF6YVZfjxs4vBhj2A47nLApYuwVQF48BoaM4DesX2kaGqgz9g6JXJEXYVsGaPm3GJNpwZjUCr26SgaR4KEftU7YLEZbNf39bbg9L3BfbJJyDFGq3xuUP3XL857r3MRZhJE7EdG9jc6mBfeXyM25MgyMNKZ7ANHpaucNDdp3rtTvcQR175QuriqGYNpX2ecNaJQBtifV3e72ViLJCexRSunh3yfB7hSeG7SVh23dQSHSqCPEXQFPqVcV7xeKa957G9DfCK56DffXf6qNLDtr2zgfFf7ibbL6xmHPSgZtHzUexiFw67kRi7rD4CgWJciF8rpqPNEy9RngrXX93h336WePQPk4m8MZcL2dMLPxtkaJ8Fqzg9mN9kcZwE7LxxzWxtpTcWr7XcsAi9wYTZUwMSz95QT5nu1c2JPuKNpKfKwxnfsWwVN5GXtzDgETKjDftEVD3fDTsinriDyqPK9dm11xFiSRupBUBTGgE7quKqf67heTGmNsWyBge8YLvuK6SU6yFQemsogqPfJnbT3dkd7k8ooYLQHzsWAZ4mpbfd";

        var item = decodeEvent(Base58.decode(base58));
        System.out.println(item);
    }
}
