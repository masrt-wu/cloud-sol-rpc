package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.MeteoraLegs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.i32;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.tokenProgram;

/**
 * Meteora DLMM 只读取路由所需的 LbPair 地址与代币程序，不解码完整奖励状态。
 */
public final class MeteoraDlmmPoolParser {
    private static final int BITMAP_OFFSET = 584;

    private MeteoraDlmmPoolParser() {
    }

    /**
     * 直接从 LbPair 原始数据建立路由池快照；BinArray 必须与本次方向和数量匹配。
     *
     * @param lbPair          实际池账户地址
     * @param data            LbPair 原始账户字节
     * @param bitmapExtension 超出默认位图时需要的扩展账户，否则可传 null
     * @param binArrays       本次 swap 按执行顺序准备的 BinArray 地址
     * @return 可直接传给 MeteoraLegs.dlmm/buyDlmm/sellDlmm 的对象
     */
    public static MeteoraLegs.Dlmm parse(PublicKey lbPair, byte[] data, PublicKey bitmapExtension, List<PublicKey> binArrays) {
        return new MeteoraLegs.Dlmm(lbPair, key(data, 152), key(data, 184), key(data, 88), key(data, 120), tokenProgram(data, 880), tokenProgram(data, 881), key(data, 552), bitmapExtension, binArrays);
    }

    /**
     * 使用已经解析的 LbPair 直接构造 DLMM 路由池对象。
     *
     * @param lbPair          实际池地址
     * @param state           已解析的 LbPair 账户
     * @param bitmapExtension 可选扩展位图地址
     * @param binArrays       本次交换所需且按执行顺序排列的 BinArray 地址
     * @return DLMM 路由池对象
     */
    public static MeteoraLegs.Dlmm fromDexAccountData(PublicKey lbPair, DexAccountData state, PublicKey bitmapExtension, List<PublicKey> binArrays) {
        return new MeteoraLegs.Dlmm(lbPair, state.publicKey("reserve_x"), state.publicKey("reserve_y"), state.publicKey("token_x_mint"), state.publicKey("token_y_mint"), tokenProgram(state.intValue("token_mint_x_program_flag")), tokenProgram(state.intValue("token_mint_y_program_flag")), state.publicKey("oracle"), bitmapExtension, binArrays);
    }

    /**
     * 读取最新的有符号 activeId。
     *
     * @param data LbPair 原始账户字节
     * @return 当前 activeId
     */
    public static int activeId(byte[] data) {
        return i32(data, 76);
    }

    /**
     * 仅在默认位图范围内按 X→Y 或 Y→X 方向寻找已初始化 BinArray。
     * 跨出 -512..511 时不能猜测扩展位图；此时调用方另行提供数组。
     *
     * @param lbPair   实际池账户地址
     * @param data     LbPair 最新原始字节
     * @param swapForY true 表示 X→Y，向较小 BinArray 索引搜索
     * @param maxCount 最多返回的数组数量
     * @return 当前默认位图中按交换方向排列的地址
     */
    public static List<PublicKey> defaultBinArrays(PublicKey lbPair, byte[] data, boolean swapForY, int maxCount) {
        List<PublicKey> result = new ArrayList<>(maxCount);
        long step = swapForY ? -1L : 1L;
        for (long index = Math.floorDiv((long) activeId(data), 70L); index >= -512 && index <= 511 && result.size() < maxCount; index += step) {
            int bit = (int) index + 512;
            if (((data[BITMAP_OFFSET + (bit >>> 3)] >>> (bit & 7)) & 1) != 0) {
                result.add(MeteoraDlmmPda.binArray(lbPair, index));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 从已解析的 LbPair 默认位图按方向寻找已初始化的 BinArray，不重新读取原始字节。
     * 超出默认位图覆盖范围时仍需由调用方另行提供扩展账户数据。
     *
     * @param lbPair   实际池地址
     * @param state    已解析的 LbPair 账户
     * @param swapForY true 表示 X→Y，向较小 BinArray 索引搜索
     * @param maxCount 最多返回的数组数量
     * @return 默认位图中按交换方向排列的 BinArray 地址
     */
    public static List<PublicKey> defaultBinArrays(PublicKey lbPair, DexAccountData state, boolean swapForY, int maxCount) {
        List<Object> bitmap = state.list("bin_array_bitmap");
        List<PublicKey> result = new ArrayList<>(maxCount);
        long step = swapForY ? -1L : 1L;
        for (long index = Math.floorDiv((long) state.intValue("active_id"), 70L); index >= -512 && index <= 511 && result.size() < maxCount; index += step) {
            int bit = (int) index + 512;
            if (((BigInteger) bitmap.get(bit >>> 6)).testBit(bit & 63)) {
                result.add(MeteoraDlmmPda.binArray(lbPair, index));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 从已解析的 LbPair 对象推导当前兑换方向的 BinArray，不再解析 JSON 字符串。
     * 仅覆盖内置位图的数组索引 -512..511；超出范围时需另行读取位图扩展账户。
     * 返回最多 maxCount 个已初始化数组的候选地址，实际所需数量取决于金额和流动性。
     *
     * @param lbPair state 对应的 LbPair 池账户地址，不是 base_key
     * @param state 由 DexParsers 解析出的 LbPair 账户对象
     * @param inputMint 本跳实际支付的 mint；自动判断 X→Y 或 Y→X 方向
     * @param maxCount 最多返回的 BinArray 数量，必须大于零
     * @return 按兑换方向排列的 BinArray PDA 列表
     */
    public static List<PublicKey> binArraysForSwap(PublicKey lbPair, DexAccountData state,
                                                    PublicKey inputMint, int maxCount) {
        Objects.requireNonNull(lbPair, "lbPair");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(inputMint, "inputMint");
        if (maxCount <= 0) throw new IllegalArgumentException("maxCount 必须大于零");

        PublicKey mintX = state.publicKey("token_x_mint");
        PublicKey mintY = state.publicKey("token_y_mint");
        boolean swapForY;
        if (inputMint.equals(mintX)) swapForY = true;
        else if (inputMint.equals(mintY)) swapForY = false;
        else throw new IllegalArgumentException("输入 mint 不属于该 DLMM 池");

        List<PublicKey> addresses = defaultBinArrays(lbPair, state, swapForY, maxCount);
        if (addresses.isEmpty())
            throw new IllegalArgumentException("当前方向在内置位图中找不到 BinArray；请核对池状态或检查位图扩展账户");
        return addresses;
    }
}
