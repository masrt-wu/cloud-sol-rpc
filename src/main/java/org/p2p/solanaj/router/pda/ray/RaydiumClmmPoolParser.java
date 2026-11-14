package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.programs.parse.DexAccountData;
import org.p2p.solanaj.router.RaydiumLegs;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.p2p.solanaj.router.pda.TrustedPoolFields.i32;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.key;
import static org.p2p.solanaj.router.pda.TrustedPoolFields.u16;

/**
 * Raydium CLMM PoolState 快速转成路由对象；跨 tick 的账户由调用方准备。
 */
public final class RaydiumClmmPoolParser {
    private RaydiumClmmPoolParser() {
    }

    public static final PublicKey oneBaseMint = new PublicKey("So11111111111111111111111111111111111111112");


    public static final PublicKey oneQuoteMint = new PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

    /**
     * 不校验 owner/判别符；Pool 不保存两侧 Token Program，须传 mint 的实际 owner。
     *
     * @param poolAddress     实际 PoolState 地址
     * @param data            PoolState 原始账户字节
     * @param tokenProgram0   token0 mint 的实际 Token 程序
     * @param tokenProgram1   token1 mint 的实际 Token 程序
     * @param bitmapExtension 需要时提供 tick 位图扩展，否则传 null
     * @param tickArrays      本次交换路径所需且已排序的 TickArray 地址
     * @return 可直接传给 RaydiumLegs.clmm/buyClmm/sellClmm 的对象
     */
    public static RaydiumLegs.Clmm parse(PublicKey poolAddress, byte[] data, PublicKey tokenProgram0, PublicKey tokenProgram1, PublicKey bitmapExtension, List<PublicKey> tickArrays) {
        return new RaydiumLegs.Clmm(key(data, 9), poolAddress, key(data, 73), key(data, 105), key(data, 137), key(data, 169), tokenProgram0, tokenProgram1, key(data, 201), bitmapExtension, tickArrays);
    }

    /**
     * 将已解析的 PoolState 转换为 CLMM 路由对象。
     *
     * @param poolAddress     实际 PoolState 地址
     * @param state           已解析的 PoolState 账户
     * @param tokenProgram0   token0 mint 所属 Token 程序
     * @param tokenProgram1   token1 mint 所属 Token 程序
     * @param bitmapExtension 可选 tick 位图扩展地址
     * @param tickArrays      本次交换所需且按执行顺序排列的 TickArray 地址
     * @return CLMM 路由池对象
     */
    public static RaydiumLegs.Clmm fromDexAccountData(PublicKey poolAddress, DexAccountData state, PublicKey tokenProgram0, PublicKey tokenProgram1, PublicKey bitmapExtension, List<PublicKey> tickArrays) {
        return new RaydiumLegs.Clmm(state.publicKey("amm_config"), poolAddress, state.publicKey("token_mint_0"), state.publicKey("token_mint_1"), state.publicKey("token_vault_0"), state.publicKey("token_vault_1"), tokenProgram0, tokenProgram1, state.publicKey("observation_key"), bitmapExtension, tickArrays);
    }

    /**
     * 将已解析的 PoolState 转换为 CLMM 路由对象。
     *
     * @param poolAddress   实际 PoolState 地址
     * @param state         已解析的 PoolState 账户
     * @param tokenProgram0 token0 mint 所属 Token 程序
     * @param tokenProgram1 token1 mint 所属 Token 程序
     * @return CLMM 路由池对象
     */
    public static RaydiumLegs.Clmm fromDexAccountData(PublicKey poolAddress, DexAccountData state,
                                                      PublicKey tokenProgram0,
                                                      PublicKey tokenProgram1) {
        var bitmapExtension = RaydiumClmmPda.deriveBitmapExtension(poolAddress);
        List<PublicKey> tickArrays   = deriveTickArrays(poolAddress,state.integer("tick_current").intValue(),state.integer("tick_spacing").intValue()
        ,state.list("tick_array_bitmap").stream().map(d-> (BigInteger)d).toArray(BigInteger[]::new),state.publicKey("token_mint_0").equals(state.publicKey("token_mint_1")),6);

        //var tokenProgram0V1 = state.publicKey("token_mint_0").equals(oneBaseMint)
        return new RaydiumLegs.Clmm(state.publicKey("amm_config"), poolAddress, state.publicKey("token_mint_0"), state.publicKey("token_mint_1"), state.publicKey("token_vault_0"), state.publicKey("token_vault_1"), tokenProgram0, tokenProgram1, state.publicKey("observation_key"), bitmapExtension, tickArrays);
    }
    /**
     * 将已解析的 PoolState 转换为 CLMM 路由对象。
     *
     * @param poolAddress   实际 PoolState 地址
     * @param state         已解析的 PoolState 账户
     * @param tokenProgram token0 mint 所属 Token 程序
     * @return CLMM 路由池对象
     */
    public static RaydiumLegs.Clmm fromDexAccountData(PublicKey poolAddress, DexAccountData state,
                                                      PublicKey tokenProgram) {
        var bitmapExtension = RaydiumClmmPda.deriveBitmapExtension(poolAddress);
        List<PublicKey> tickArrays   = deriveTickArrays(poolAddress,state.integer("tick_current").intValue(),state.integer("tick_spacing").intValue()
                ,state.list("tick_array_bitmap").stream().map(d-> (BigInteger)d).toArray(BigInteger[]::new),state.publicKey("token_mint_0").equals(state.publicKey("token_mint_1")),6);

        var tokenProgram0 = state.publicKey("token_mint_0").equals(oneBaseMint) || state.publicKey("token_mint_0").equals(oneQuoteMint)? TokenProgram.PROGRAM_ID:tokenProgram;
        var tokenProgram1 = state.publicKey("token_mint_1").equals(oneBaseMint) || state.publicKey("token_mint_0").equals(oneQuoteMint)? TokenProgram.PROGRAM_ID:tokenProgram;

        return new RaydiumLegs.Clmm(state.publicKey("amm_config"), poolAddress, state.publicKey("token_mint_0"), state.publicKey("token_mint_1"), state.publicKey("token_vault_0"), state.publicKey("token_vault_1"), tokenProgram0, tokenProgram1, state.publicKey("observation_key"), bitmapExtension, tickArrays);
    }

    /**
     * 读取最新的有符号 tick。
     *
     * @param data PoolState 原始账户字节
     * @return 当前 tick
     */
    public static int tickCurrent(byte[] data) {
        return i32(data, 269);
    }

    /**
     * 读取池保存的 tickSpacing。
     *
     * @param data PoolState 原始账户字节
     * @return tickSpacing
     */
    public static int tickSpacing(byte[] data) {
        return u16(data, 235);
    }


    /**
     * 根据 CLMM PoolState 的当前 Tick、TickSpacing 和 bitmap，
     * 生成本次 Swap 需要携带的 TickArray PDA 列表。
     *
     * @param poolAddress CLMM PoolState 地址
     * @param tickCurrent PoolState.tick_current
     * @param tickSpacing PoolState.tick_spacing
     * @param bitmap      PoolState.tick_array_bitmap，长度必须为 16
     * @param zeroForOne  true = token0 -> token1；false = token1 -> token0
     * @param maxCount    最多返回多少个 TickArray
     * @return 按 Swap 执行方向排列的 TickArray PDA
     */
    public static List<PublicKey> deriveTickArrays(PublicKey poolAddress, int tickCurrent, int tickSpacing, BigInteger[] bitmap, boolean zeroForOne, int maxCount) {

        if (bitmap == null || bitmap.length != 16) {
            throw new IllegalArgumentException("tick_array_bitmap 必须是 u64[16]");
        }

        if (tickSpacing <= 0) {
            throw new IllegalArgumentException("tickSpacing 必须 > 0");
        }

        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount 必须 > 0");
        }

        /*
         * Raydium CLMM 一个 TickArray 固定有 60 个 TickState。
         *
         * 例如：
         *
         * tickSpacing = 120
         *
         * arraySpan =
         * 120 * 60
         * =
         * 7200
         */
        int arraySpan = Math.multiplyExact(tickSpacing, 60);

        /*
         * 当前 tick 所在的 TickArray index。
         *
         * 必须使用 floorDiv，
         * 否则负数 Tick 会计算错误。
         */
        int currentArrayIndex = Math.floorDiv(tickCurrent, arraySpan);

        List<PublicKey> result = new ArrayList<>(maxCount);

        /*
         * 当前 TickArray 如果已经初始化，
         * 先加入。
         */
        if (isTickArrayInitialized(bitmap, currentArrayIndex)) {

            int startTickIndex = Math.multiplyExact(currentArrayIndex, arraySpan);

            result.add(deriveTickArray(poolAddress, startTickIndex));
        }

        /*
         * Swap 方向：
         *
         * token0 -> token1
         * Tick 下降
         * arrayIndex - 1
         *
         * token1 -> token0
         * Tick 上升
         * arrayIndex + 1
         */
        int step = zeroForOne ? -1 : 1;

        int arrayIndex = currentArrayIndex + step;

        /*
         * 默认 bitmap 表达 arrayIndex：
         *
         * -512 ~ 511
         */
        while (result.size() < maxCount && arrayIndex >= -512 && arrayIndex <= 511) {

            if (isTickArrayInitialized(bitmap, arrayIndex)) {

                int startTickIndex = Math.multiplyExact(arrayIndex, arraySpan);

                result.add(deriveTickArray(poolAddress, startTickIndex));
            }

            arrayIndex += step;
        }

        return List.copyOf(result);
    }


    /**
     * 判断指定 TickArray index 是否已经初始化。
     * <p>
     * 默认 Raydium CLMM bitmap：
     * <p>
     * arrayIndex = -512 -> bit 0
     * arrayIndex = 0    -> bit 512
     * arrayIndex = 511  -> bit 1023
     *
     * @param bitmap     PoolState.tick_array_bitmap
     * @param arrayIndex TickArray index
     * @return true = 已初始化
     */
    public static boolean isTickArrayInitialized(BigInteger[] bitmap, int arrayIndex) {

        if (arrayIndex < -512 || arrayIndex > 511) {
            return false;
        }

        int bitPosition = arrayIndex + 512;

        int wordIndex = bitPosition / 64;

        int bitOffset = bitPosition % 64;

        return bitmap[wordIndex].testBit(bitOffset);
    }


    /**
     * 派生 Raydium CLMM TickArray PDA。
     * <p>
     * Seeds:
     * <p>
     * [
     * "tick_array",
     * poolAddress,
     * startTickIndex(i32 Big Endian)
     * ]
     *
     * @param poolAddress    CLMM PoolState 地址
     * @param startTickIndex TickArray 起始 Tick
     * @return TickArray PDA
     */
    public static PublicKey deriveTickArray(PublicKey poolAddress, int startTickIndex) {

        byte[] startTickIndexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(startTickIndex).array();

        return PublicKey.findProgramAddress(List.of("tick_array".getBytes(StandardCharsets.UTF_8),

                poolAddress.toByteArray(),

                startTickIndexBytes), new PublicKey("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK")).getAddress();
    }


}
