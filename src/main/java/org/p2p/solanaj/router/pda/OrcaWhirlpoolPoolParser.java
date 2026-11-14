package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.programs.parse.OrcaWhirlpoolParser;
import org.p2p.solanaj.router.OrcaLegs;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.i32;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.u16;

/**
 * Orca Whirlpool 原始数据直接转成 Router Pool；vault 使用池中保存的地址。
 */
public final class OrcaWhirlpoolPoolParser {
    private OrcaWhirlpoolPoolParser() {
    }

    /**
     * 不校验 owner/判别符；Pool 不存两侧 Token Program，须由调用方提供 mint owner。
     *
     * @param whirlpool     实际 Whirlpool 地址
     * @param data          Whirlpool 的原始账户字节
     * @param tokenProgramA A mint 的实际 Token 程序
     * @param tokenProgramB B mint 的实际 Token 程序
     * @param tickArrays    本次 swap 按顺序准备的三个 TickArray 地址
     * @return 可直接传给 OrcaLegs.swap/buy/sell 的对象
     */
    public static OrcaLegs.Pool parse(PublicKey whirlpool, byte[] data, PublicKey tokenProgramA, PublicKey tokenProgramB, List<PublicKey> tickArrays) {
        return new OrcaLegs.Pool(whirlpool, key(data, 101), key(data, 181), key(data, 133), key(data, 213), tokenProgramA, tokenProgramB, tickArrays);
    }

    /**
     * 使用已经解析的 Whirlpool 直接构造 Orca 路由池对象。
     *
     * @param whirlpool     实际池地址
     * @param state         已解析的 Whirlpool 账户
     * @param tokenProgramA A mint 所属 Token 程序
     * @param tokenProgramB B mint 所属 Token 程序
     * @param tickArrays    本次交换所需且按执行顺序排列的 TickArray 地址
     * @return Orca 路由池对象
     */
    public static OrcaLegs.Pool fromDexAccountData(PublicKey whirlpool, DexAccountData state, PublicKey tokenProgramA, PublicKey tokenProgramB, List<PublicKey> tickArrays) {
        return new OrcaLegs.Pool(whirlpool, state.publicKey("token_mint_a"), state.publicKey("token_mint_b"), state.publicKey("token_vault_a"), state.publicKey("token_vault_b"), tokenProgramA, tokenProgramB, tickArrays);
    }
    /**
     * 使用已经解析的 Whirlpool 直接构造 Orca 路由池对象。
     *
     * @param whirlpool     实际池地址
     * @param state         已解析的 Whirlpool 账户
     * @param tokenProgramA A mint 所属 Token 程序
     * @param tokenProgramB B mint 所属 Token 程序
     * @return Orca 路由池对象
     */
    public static OrcaLegs.Pool fromDexAccountData(PublicKey whirlpool, DexAccountData state, PublicKey tokenProgramA, PublicKey tokenProgramB) {
        var tickArrays = deriveOrcaTickArrays(whirlpool,state.intValue("tick_current_index"),state.intValue("tick_spacing")
        ,state.publicKey("token_mint_a").equals(state.publicKey("token_mint_b")));
        return new OrcaLegs.Pool(whirlpool, state.publicKey("token_mint_a"), state.publicKey("token_mint_b"), state.publicKey("token_vault_a"), state.publicKey("token_vault_b"), tokenProgramA, tokenProgramB, tickArrays);
    }



    /**
     * 读取最新的有符号 tick。
     *
     * @param data Whirlpool 原始账户字节
     * @return 当前 tick
     */
    public static int tickCurrent(byte[] data) {
        return i32(data, 81);
    }

    /**
     * 读取池保存的 tickSpacing。
     *
     * @param data Whirlpool 原始账户字节
     * @return tickSpacing
     */
    public static int tickSpacing(byte[] data) {
        return u16(data, 41);
    }

    /**
     * 根据 Orca Whirlpool 当前状态生成 Swap 使用的 3 个 TickArray PDA。
     *
     * @param whirlpool        Whirlpool Pool 地址
     * @param tickCurrentIndex Pool.tick_current_index
     * @param tickSpacing      Pool.tick_spacing
     * @param aToB             true = token A -> token B；false = token B -> token A
     * @return 按 Swap 执行方向排列的 3 个 TickArray PDA
     */
    public static List<PublicKey> deriveOrcaTickArrays(PublicKey whirlpool, int tickCurrentIndex, int tickSpacing, boolean aToB) {

        /*
         * Orca 一个 TickArray 固定有 88 个 Tick。
         */
        int arraySpan = Math.multiplyExact(tickSpacing, 88);

        /*
         * 当前 TickArray 起始 index。
         *
         * 必须 floorDiv。
         *
         * 例如：
         *
         * tickCurrentIndex = -88599
         * tickSpacing      = 8
         *
         * arraySpan = 704
         *
         * floorDiv(-88599, 704)
         * = -126
         *
         * start = -126 * 704
         *       = -88704
         */
        int currentStart = Math.floorDiv(tickCurrentIndex, arraySpan) * arraySpan;

        /*
         * A -> B：
         * sqrtPrice / tick 向下移动。
         *
         * B -> A：
         * sqrtPrice / tick 向上移动。
         */
        int step = aToB ? -arraySpan : arraySpan;

        int start0 = currentStart;
        int start1 = currentStart + step;
        int start2 = currentStart + step * 2;

        return List.of(deriveOrcaTickArray(whirlpool, start0), deriveOrcaTickArray(whirlpool, start1), deriveOrcaTickArray(whirlpool, start2));
    }

    /**
     * 派生 Orca Whirlpool TickArray PDA。
     *
     * 注意：
     *
     * Orca 和 Raydium CLMM 不一样。
     *
     * Orca 的 startTickIndex seed
     * 不是 i32 BigEndian，
     * 而是十进制字符串 UTF-8。
     *
     * 例如：
     *
     * startTickIndex = -88704
     *
     * seed 实际为：
     *
     * "-88704".getBytes(UTF_8)
     *
     * PDA Seeds:
     *
     * [
     *     "tick_array",
     *     whirlpool,
     *     startTickIndex 的十进制字符串
     * ]
     *
     * @param whirlpool      Whirlpool 地址
     * @param startTickIndex TickArray 起始 Tick Index
     * @return TickArray PDA
     */
    public static PublicKey deriveOrcaTickArray(PublicKey whirlpool, int startTickIndex) {

        byte[] startTickIndexSeed = Integer.toString(startTickIndex).getBytes(StandardCharsets.UTF_8);

        return PublicKey.findProgramAddress(List.of("tick_array".getBytes(StandardCharsets.UTF_8),

                whirlpool.toByteArray(),

                startTickIndexSeed), OrcaWhirlpoolParser.PROGRAM_ID).getAddress();
    }


}
