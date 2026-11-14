package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

/**
 * Raydium LaunchLab PoolState 链上账户解析器。
 *
 * <p>
 * LaunchLab Program:
 *
 * <pre>
 * LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj
 * </pre>
 *
 * <p>
 * 当前 PoolState 总长度：
 *
 * <pre>
 * 429 bytes
 * </pre>
 *
 * <p>
 * 其中前 8 字节为 Anchor Account Discriminator。
 */
public final class LaunchLabPoolStateParser {

    /**
     * Raydium LaunchLab Program ID。
     * <p>
     * PoolState 的 account owner 必须是这个 Program。
     */
    public static final PublicKey PROGRAM_ID = new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");

    /**
     * 标准 SPL Token Program。
     */
    public static final PublicKey TOKEN_PROGRAM = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    /**
     * Token-2022 Program。
     */
    public static final PublicKey TOKEN_2022_PROGRAM = new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");

    /**
     * PoolState Anchor discriminator。
     *
     * <pre>
     * sha256("account:PoolState")[0..8]
     *
     * HEX:
     * f7 ed e3 f5 d7 c3 de 46
     * </pre>
     */
    private static final byte[] POOL_STATE_DISCRIMINATOR = {(byte) 0xF7, (byte) 0xED, (byte) 0xE3, (byte) 0xF5, (byte) 0xD7, (byte) 0xC3, (byte) 0xDE, (byte) 0x46};

    /**
     * 当前版本 PoolState 完整账户长度。
     *
     * <pre>
     * 8   discriminator
     * 421 PoolState 数据
     * -----------------
     * 429 bytes
     * </pre>
     */
    public static final int ACCOUNT_SIZE = 429;


    // ============================================================
    // 解析入口
    // ============================================================

    /**
     * 解析 RPC 返回的 Base64 PoolState。
     *
     * @param base64 RPC getAccountInfo 返回的 Base64 account.data
     * @return 解析后的 PoolState
     */
    public static PoolState parseBase64(String base64) {

        return parse(Base64.getDecoder().decode(base64));
    }

    /**
     * 在解析 PoolState 之前同时验证 account owner。
     *
     * <p>
     * 注意：
     * Solana account 的 owner 不存在于 account.data 内部，
     * 所以如果你想验证 owner，必须从 RPC / AccountInfo
     * 单独把 owner 传进来。
     *
     * @param owner Solana AccountInfo.owner
     * @param data  Solana AccountInfo.data 原始字节
     * @return 解析完成的 PoolState
     */
    public static PoolState parse(PublicKey owner, byte[] data) {

        /*
         * PoolState 必须归属于 LaunchLab Program。
         */
        if (!PROGRAM_ID.equals(owner)) {

            throw new IllegalArgumentException("该账户不是 LaunchLab PoolState，owner=" + owner);
        }

        return parse(data);
    }

    /**
     * 解析 Raydium LaunchLab PoolState 原始账户数据。
     *
     * @param data 完整 account.data，包含前 8 字节 Anchor discriminator
     * @return 解析后的 PoolState
     */
    public static PoolState parse(byte[] data) {

        if (data == null) {
            throw new IllegalArgumentException("PoolState 数据不能为空");
        }

        if (data.length < ACCOUNT_SIZE) {
            throw new IllegalArgumentException("PoolState 数据长度不足，至少需要 " + ACCOUNT_SIZE + " 字节，实际=" + data.length);
        }

        Reader r = new Reader(data);


        // ========================================================
        // 0 ~ 7
        // Anchor Account Discriminator
        // ========================================================

        byte[] discriminator = r.bytes(8);

        if (!Arrays.equals(discriminator, POOL_STATE_DISCRIMINATOR)) {

            throw new IllegalArgumentException("PoolState discriminator 不匹配");
        }


        // ========================================================
        // 8 ~ 15
        // epoch
        // ========================================================

        /**
         * Account 最近更新对应的 epoch 标记。
         *
         * 链上类型：
         * u64
         */
        BigInteger epoch = r.u64();


        // ========================================================
        // 16
        // auth_bump
        // ========================================================

        /**
         * LaunchLab Vault Authority PDA bump。
         *
         * 链上类型：
         * u8
         */
        int authBump = r.u8();


        // ========================================================
        // 17
        // status
        // ========================================================

        /**
         * Pool 当前生命周期状态。
         *
         * <pre>
         * 0 = Fund
         *     正在 Bonding Curve 募资/交易
         *
         * 1 = Migrate
         *     募资结束，等待迁移
         *
         * 2 = Trade
         *     已迁移到 AMM / CPSWAP
         * </pre>
         */
        int status = r.u8();


        // ========================================================
        // 18
        // base_decimals
        // ========================================================

        /**
         * Base Token 小数位数。
         */
        int baseDecimals = r.u8();


        // ========================================================
        // 19
        // quote_decimals
        // ========================================================

        /**
         * Quote Token 小数位数。
         */
        int quoteDecimals = r.u8();


        // ========================================================
        // 20
        // migrate_type
        // ========================================================

        /**
         * 募资完成后的迁移目标。
         *
         * <pre>
         * 0 = AMM
         * 1 = CPSWAP
         * </pre>
         */
        int migrateType = r.u8();


        // ========================================================
        // 21 ~ 28
        // supply
        // ========================================================

        /**
         * Base Token 总 Supply。
         *
         * raw amount，包含 decimals。
         */
        BigInteger supply = r.u64();


        // ========================================================
        // 29 ~ 36
        // total_base_sell
        // ========================================================

        /**
         * Bonding Curve 总共允许卖出的 Base Token 数量。
         */
        BigInteger totalBaseSell = r.u64();


        // ========================================================
        // 37 ~ 44
        // virtual_base
        // ========================================================

        /**
         * Curve 使用的 Virtual Base 参数。
         *
         * Constant Curve 时属于虚拟流动性参数。
         */
        BigInteger virtualBase = r.u64();


        // ========================================================
        // 45 ~ 52
        // virtual_quote
        // ========================================================

        /**
         * Curve 使用的 Virtual Quote 参数。
         */
        BigInteger virtualQuote = r.u64();


        // ========================================================
        // 53 ~ 60
        // real_base
        // ========================================================

        /**
         * 当前 Bonding Curve 中的实际 Base Token 数量。
         *
         * 属于动态状态。
         */
        BigInteger realBase = r.u64();


        // ========================================================
        // 61 ~ 68
        // real_quote
        // ========================================================

        /**
         * 当前 Bonding Curve 中的实际 Quote Token 数量。
         *
         * 属于动态状态。
         */
        BigInteger realQuote = r.u64();


        // ========================================================
        // 69 ~ 76
        // total_quote_fund_raising
        // ========================================================

        /**
         * 募资目标 Quote Token 总量。
         */
        BigInteger totalQuoteFundRaising = r.u64();


        // ========================================================
        // 77 ~ 84
        // quote_protocol_fee
        // ========================================================

        /**
         * 当前累计的 Protocol Quote Token Fee。
         */
        BigInteger quoteProtocolFee = r.u64();


        // ========================================================
        // 85 ~ 92
        // platform_fee
        // ========================================================

        /**
         * 当前累计的 Platform Fee。
         */
        BigInteger platformFee = r.u64();


        // ========================================================
        // 93 ~ 100
        // migrate_fee
        // ========================================================

        /**
         * Pool 从 LaunchLab 迁移到 AMM / CPSWAP 所需的迁移手续费。
         */
        BigInteger migrateFee = r.u64();


        // ========================================================
        // 101 ~ 140
        // VestingSchedule
        // ========================================================

        VestingSchedule vestingSchedule = parseVestingSchedule(r);


        // ========================================================
        // 141 ~ 172
        // global_config
        // ========================================================

        /**
         * LaunchLab GlobalConfig 地址。
         */
        PublicKey globalConfig = r.pubkey();


        // ========================================================
        // 173 ~ 204
        // platform_config
        // ========================================================

        /**
         * 当前 Pool 使用的平台配置账户。
         */
        PublicKey platformConfig = r.pubkey();


        // ========================================================
        // 205 ~ 236
        // base_mint
        // ========================================================

        /**
         * Base Token Mint。
         *
         * 通常就是新发行的 LaunchLab Token。
         */
        PublicKey baseMint = r.pubkey();


        // ========================================================
        // 237 ~ 268
        // quote_mint
        // ========================================================

        /**
         * Quote Token Mint。
         *
         * 可以是 WSOL、USDC、STONK 等。
         */
        PublicKey quoteMint = r.pubkey();


        // ========================================================
        // 269 ~ 300
        // base_vault
        // ========================================================

        /**
         * Pool Base Token Vault。
         */
        PublicKey baseVault = r.pubkey();


        // ========================================================
        // 301 ~ 332
        // quote_vault
        // ========================================================

        /**
         * Pool Quote Token Vault。
         */
        PublicKey quoteVault = r.pubkey();


        // ========================================================
        // 333 ~ 364
        // creator
        // ========================================================

        /**
         * Token / Pool Creator 地址。
         */
        PublicKey creator = r.pubkey();


        // ========================================================
        // 365
        // token_program_flag
        // ========================================================

        /**
         * Base / Quote Token 使用哪个 Token Program 的位标记。
         *
         * <pre>
         * bit 0:
         *     Base Token Program
         *
         *     0 = SPL Token
         *     1 = Token-2022
         *
         *
         * bit 1:
         *     Quote Token Program
         *
         *     0 = SPL Token
         *     1 = Token-2022
         * </pre>
         *
         * 因此：
         *
         * <pre>
         * 0 = base SPL       + quote SPL
         * 1 = base Token2022 + quote SPL
         * 2 = base SPL       + quote Token2022
         * 3 = base Token2022 + quote Token2022
         * </pre>
         */
        int tokenProgramFlag = r.u8();


        // ========================================================
        // 366
        // amm_creator_fee_on
        // ========================================================

        /**
         * 迁移到 CPMM 后 Creator Fee 收取在哪侧。
         *
         * Anchor 无 payload enum，
         * 因此链上占 1 字节。
         *
         * <pre>
         * 0 = QuoteToken
         * 1 = BothToken
         * </pre>
         */
        int ammCreatorFeeOn = r.u8();


        // ========================================================
        // 367 ~ 374
        // platform_vesting_share
        // ========================================================

        /**
         * Platform 分配到的 Vesting Base Token 数量。
         *
         * 链上类型：
         * u64
         */
        BigInteger platformVestingShare = r.u64();


        // ========================================================
        // 375 ~ 428
        // padding[54]
        // ========================================================

        /**
         * 未来协议升级预留空间。
         */
        byte[] padding = r.bytes(54);


        // ========================================================
        // 最终 offset 验证
        // ========================================================

        if (r.position() != ACCOUNT_SIZE) {

            throw new IllegalStateException("PoolState 解析 offset 错误，" + "当前=" + r.position() + "，预期=" + ACCOUNT_SIZE);
        }


        return new PoolState(epoch,

                authBump, status,

                baseDecimals, quoteDecimals,

                migrateType,

                supply, totalBaseSell,

                virtualBase, virtualQuote,

                realBase, realQuote,

                totalQuoteFundRaising,

                quoteProtocolFee, platformFee, migrateFee,

                vestingSchedule,

                globalConfig, platformConfig,

                baseMint, quoteMint,

                baseVault, quoteVault,

                creator,

                tokenProgramFlag,

                ammCreatorFeeOn,

                platformVestingShare,

                padding);
    }


    // ============================================================
    // VestingSchedule
    // ============================================================

    /**
     * 解析 PoolState 内嵌的 VestingSchedule。
     *
     * <p>
     * 总长度：
     *
     * <pre>
     * 5 × u64
     * =
     * 40 bytes
     * </pre>
     *
     * @param r 当前 PoolState 二进制 Reader
     * @return VestingSchedule
     */
    private static VestingSchedule parseVestingSchedule(Reader r) {

        /**
         * 总锁仓 Base Token 数量。
         */
        BigInteger totalLockedAmount = r.u64();

        /**
         * 募资结束后，在开始解锁之前等待的时间。
         *
         * 单位：
         * 秒
         */
        BigInteger cliffPeriod = r.u64();

        /**
         * 线性解锁持续时间。
         *
         * 单位：
         * 秒
         */
        BigInteger unlockPeriod = r.u64();

        /**
         * Vesting 开始时间。
         *
         * 通常在募资结束后由程序设置。
         */
        BigInteger startTime = r.u64();

        /**
         * 已经分配给 VestingRecord 的 Base Token 总量。
         */
        BigInteger allocatedShareAmount = r.u64();

        return new VestingSchedule(totalLockedAmount, cliffPeriod, unlockPeriod, startTime, allocatedShareAmount);
    }


    // ============================================================
    // Token Program
    // ============================================================

    /**
     * 根据 token_program_flag 判断 Base Mint 使用哪个 Token Program。
     *
     * @param tokenProgramFlag PoolState.token_program_flag
     * @return SPL Token 或 Token-2022 Program
     */
    public static PublicKey baseTokenProgram(int tokenProgramFlag) {

        /*
         * bit0 控制 Base Token。
         */
        boolean token2022 = (tokenProgramFlag & 0b0000_0001) != 0;

        return token2022 ? TOKEN_2022_PROGRAM : TOKEN_PROGRAM;
    }

    /**
     * 根据 token_program_flag 判断 Quote Mint 使用哪个 Token Program。
     *
     * @param tokenProgramFlag PoolState.token_program_flag
     * @return SPL Token 或 Token-2022 Program
     */
    public static PublicKey quoteTokenProgram(int tokenProgramFlag) {

        /*
         * bit1 控制 Quote Token。
         */
        boolean token2022 = (tokenProgramFlag & 0b0000_0010) != 0;

        return token2022 ? TOKEN_2022_PROGRAM : TOKEN_PROGRAM;
    }


    // ============================================================
    // Pool Status
    // ============================================================

    /**
     * 将 status 转成方便调试的中文名称。
     *
     * @param status PoolState.status
     * @return 状态说明
     */
    public static String statusName(int status) {

        return switch (status) {

            case 0 -> "Fund / 募资交易阶段";

            case 1 -> "Migrate / 等待迁移";

            case 2 -> "Trade / 已迁移";

            default -> "未知状态(" + status + ")";
        };
    }

    /**
     * 将 migrate_type 转为名称。
     *
     * @param migrateType PoolState.migrate_type
     * @return AMM 或 CPSWAP
     */
    public static String migrateTypeName(int migrateType) {

        return switch (migrateType) {

            case 0 -> "AMM";

            case 1 -> "CPSWAP";

            default -> "未知迁移类型(" + migrateType + ")";
        };
    }

    /**
     * 将 amm_creator_fee_on 转换为可读名称。
     *
     * @param value PoolState.amm_creator_fee_on
     * @return Creator Fee 收取模式
     */
    public static String ammCreatorFeeOnName(int value) {

        return switch (value) {

            case 0 -> "QuoteToken";

            case 1 -> "BothToken";

            default -> "未知模式(" + value + ")";
        };
    }


    // ============================================================
    // 数据结构
    // ============================================================

    /**
     * LaunchLab Base Token Vesting 配置。
     *
     * @param totalLockedAmount    总共锁仓的 Base Token raw amount
     * @param cliffPeriod          募资完成后等待多久开始解锁，单位秒
     * @param unlockPeriod         线性解锁持续时间，单位秒
     * @param startTime            Vesting 正式开始的时间
     * @param allocatedShareAmount 已经分配给 VestingRecord 的 Token 数量
     */
    public record VestingSchedule(

            BigInteger totalLockedAmount,

            BigInteger cliffPeriod,

            BigInteger unlockPeriod,

            BigInteger startTime,

            BigInteger allocatedShareAmount) {
    }


    /**
     * Raydium LaunchLab PoolState。
     *
     * @param epoch                 Account 更新 epoch
     * @param authBump              Vault Authority PDA bump
     * @param status                Pool 状态：
     *                              0=Fund，
     *                              1=Migrate，
     *                              2=Trade
     * @param baseDecimals          Base Token decimals
     * @param quoteDecimals         Quote Token decimals
     * @param migrateType           迁移类型：
     *                              0=AMM，
     *                              1=CPSWAP
     * @param supply                Base Token 总 Supply
     * @param totalBaseSell         Bonding Curve 总共允许出售的 Base Token 数量
     * @param virtualBase           Curve Virtual Base 参数
     * @param virtualQuote          Curve Virtual Quote 参数
     * @param realBase              当前 Curve 实际 Base Token 数量
     * @param realQuote             当前 Curve 实际 Quote Token 数量
     * @param totalQuoteFundRaising Quote Token 募资目标
     * @param quoteProtocolFee      当前累计 Protocol Fee
     * @param platformFee           当前累计 Platform Fee
     * @param migrateFee            迁移 AMM / CPSWAP 时的费用
     * @param vestingSchedule       Base Token Vesting 配置
     * @param globalConfig          LaunchLab GlobalConfig
     * @param platformConfig        LaunchLab PlatformConfig
     * @param baseMint              Base Token Mint
     * @param quoteMint             Quote Token Mint
     * @param baseVault             Base Token Pool Vault
     * @param quoteVault            Quote Token Pool Vault
     * @param creator               Token / Pool Creator
     * @param tokenProgramFlag      Token Program bitmask：
     *                              bit0=Base，
     *                              bit1=Quote
     * @param ammCreatorFeeOn       迁移到 CPMM 后 Creator Fee 模式：
     *                              0=QuoteToken，
     *                              1=BothToken
     * @param platformVestingShare  Platform 获得的 Vesting Token 数量
     * @param padding               未来协议升级预留的 54 字节
     */
    public record PoolState(

            BigInteger epoch,

            int authBump,

            int status,

            int baseDecimals,

            int quoteDecimals,

            int migrateType,

            BigInteger supply,

            BigInteger totalBaseSell,

            BigInteger virtualBase,

            BigInteger virtualQuote,

            BigInteger realBase,

            BigInteger realQuote,

            BigInteger totalQuoteFundRaising,

            BigInteger quoteProtocolFee,

            BigInteger platformFee,

            BigInteger migrateFee,

            VestingSchedule vestingSchedule,

            PublicKey globalConfig,

            PublicKey platformConfig,

            PublicKey baseMint,

            PublicKey quoteMint,

            PublicKey baseVault,

            PublicKey quoteVault,

            PublicKey creator,

            int tokenProgramFlag,

            int ammCreatorFeeOn,

            BigInteger platformVestingShare,

            byte[] padding) {

        /**
         * 获取 Base Mint 对应的 Token Program。
         *
         * @return SPL Token 或 Token-2022
         */
        public PublicKey baseTokenProgram() {

            return LaunchLabPoolStateParser.baseTokenProgram(tokenProgramFlag);
        }

        /**
         * 获取 Quote Mint 对应的 Token Program。
         *
         * @return SPL Token 或 Token-2022
         */
        public PublicKey quoteTokenProgram() {

            return LaunchLabPoolStateParser.quoteTokenProgram(tokenProgramFlag);
        }

        /**
         * 当前是否仍然处于 Bonding Curve Fund 阶段。
         *
         * @return true 表示 status=0
         */
        public boolean isFund() {
            return status == 0;
        }

        /**
         * 当前是否等待迁移。
         *
         * @return true 表示 status=1
         */
        public boolean isMigrate() {
            return status == 1;
        }

        /**
         * 当前是否已经迁移完成。
         *
         * @return true 表示 status=2
         */
        public boolean isTrade() {
            return status == 2;
        }
    }


    // ============================================================
    // Little Endian Reader
    // ============================================================

    /**
     * Solana Little Endian 原始二进制读取器。
     */
    private static final class Reader {

        /**
         * 底层 Little Endian ByteBuffer。
         */
        private final ByteBuffer buffer;

        /**
         * 创建 Reader。
         *
         * @param data Solana account.data 原始数据
         */
        private Reader(byte[] data) {

            this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }

        /**
         * 获取当前读取 offset。
         *
         * @return 当前字节位置
         */
        int position() {

            return buffer.position();
        }

        /**
         * 读取指定数量字节。
         *
         * @param length 需要读取的字节数量
         * @return 新 byte[]
         */
        byte[] bytes(int length) {

            byte[] result = new byte[length];

            buffer.get(result);

            return result;
        }

        /**
         * 读取 unsigned u8。
         *
         * @return 0 ~ 255
         */
        int u8() {

            return buffer.get() & 0xFF;
        }

        /**
         * 读取 Solana PublicKey。
         *
         * @return 32 字节 PublicKey
         */
        PublicKey pubkey() {

            return new PublicKey(bytes(32));
        }

        /**
         * 读取 unsigned u64 Little Endian。
         *
         * <p>
         * Java long 是 signed，因此这里使用 BigInteger，
         * 避免 u64 高位为 1 时溢出。
         *
         * @return 正数 BigInteger
         */
        BigInteger u64() {

            return unsignedLittleEndian(8);
        }

        /**
         * 将 Little Endian 无符号整数转换成 BigInteger。
         *
         * @param length 字节长度
         * @return 正数 BigInteger
         */
        private BigInteger unsignedLittleEndian(int length) {

            byte[] littleEndian = bytes(length);

            byte[] bigEndian = new byte[length];

            /*
             * BigInteger 使用 Big Endian，
             * 所以需要翻转 Solana Little Endian 数据。
             */
            for (int i = 0; i < length; i++) {

                bigEndian[length - 1 - i] = littleEndian[i];
            }

            /*
             * signum=1，
             * 强制按照 unsigned 正整数解析。
             */
            return new BigInteger(1, bigEndian);
        }
    }
}