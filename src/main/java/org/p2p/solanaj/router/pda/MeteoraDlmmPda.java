package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 根据已知 DLMM 池地址和种子推导所需账户，不访问 RPC。
 */
public class MeteoraDlmmPda {

    // Meteora DLMM Program
    public static final PublicKey PROGRAM_ID = new PublicKey("LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo");

    private static final byte[] ORACLE_SEED = "oracle".getBytes(StandardCharsets.UTF_8);

    private static final byte[] BIN_ARRAY_SEED = "bin_array".getBytes(StandardCharsets.UTF_8);

    private static final byte[] BITMAP_SEED = "bitmap".getBytes(StandardCharsets.UTF_8);

    private static final byte[] EVENT_AUTHORITY_SEED = "__event_authority".getBytes(StandardCharsets.UTF_8);


    // =========================================================
    // 1. Reserve PDA
    //
    // seeds:
    // [
    //     lbPair,
    //     tokenMint
    // ]
    // =========================================================

    /**
     * 推导池中指定代币的储备账户地址。
     *
     * @param lbPair    具体 DLMM 池地址
     * @param tokenMint 储备账户对应的代币 mint
     * @return 储备账户的 PDA
     */
    public static PublicKey reserve(PublicKey lbPair, PublicKey tokenMint) {
        return PublicKey.findProgramAddress(List.of(lbPair.toByteArray(), tokenMint.toByteArray()), PROGRAM_ID).getAddress();
    }


    /**
     * 推导池的 X 侧储备账户地址。
     *
     * @param lbPair     具体 DLMM 池地址
     * @param tokenXMint X 侧代币 mint
     * @return X 侧储备账户的 PDA
     */
    public static PublicKey reserveX(PublicKey lbPair, PublicKey tokenXMint) {
        return reserve(lbPair, tokenXMint);
    }


    /**
     * 推导池的 Y 侧储备账户地址。
     *
     * @param lbPair     具体 DLMM 池地址
     * @param tokenYMint Y 侧代币 mint
     * @return Y 侧储备账户的 PDA
     */
    public static PublicKey reserveY(PublicKey lbPair, PublicKey tokenYMint) {
        return reserve(lbPair, tokenYMint);
    }


    // =========================================================
    // 2. Oracle PDA
    //
    // seeds:
    // [
    //     "oracle",
    //     lbPair
    // ]
    // =========================================================

    /**
     * 推导池的预言机账户地址。
     *
     * @param lbPair 具体 DLMM 池地址
     * @return 预言机账户的 PDA
     */
    public static PublicKey oracle(PublicKey lbPair) {
        return PublicKey.findProgramAddress(List.of(ORACLE_SEED, lbPair.toByteArray()), PROGRAM_ID).getAddress();
    }


    // =========================================================
    // 3. BinArray PDA
    //
    // seeds:
    // [
    //     "bin_array",
    //     lbPair,
    //     i64LE(binArrayIndex)
    // ]
    //
    // 注意：
    // 这里是 binArrayIndex，不是 binId
    // =========================================================

    /**
     * 根据 bin 数组索引推导数组账户地址，索引不是 bin ID。
     *
     * @param lbPair        具体 DLMM 池地址
     * @param binArrayIndex bin 数组索引，可为负数
     * @return bin 数组账户的 PDA
     */
    public static PublicKey binArray(PublicKey lbPair, long binArrayIndex) {
        return PublicKey.findProgramAddress(List.of(BIN_ARRAY_SEED, lbPair.toByteArray(), i64LE(binArrayIndex)), PROGRAM_ID).getAddress();
    }


    // =========================================================
    // 4. 如果你手里拿到的是 binId
    //
    // 一个 BinArray = 70 个 bin
    //
    // binArrayIndex = floor(binId / 70)
    //
    // 必须 floorDiv，因为 binId 可以是负数
    // =========================================================

    /**
     * 根据 bin ID 推导其所在的数组账户地址。
     *
     * @param lbPair 具体 DLMM 池地址
     * @param binId  目标 bin ID，可为负数
     * @return 对应 bin 数组账户的 PDA
     */
    public static PublicKey binArrayByBinId(PublicKey lbPair, int binId) {
        long index = Math.floorDiv((long) binId, 70L);

        return binArray(lbPair, index);
    }


    /**
     * 根据 bin ID 计算所在数组的索引。
     *
     * @param binId 目标 bin ID，可为负数
     * @return 使用向下取整规则得到的数组索引
     */
    public static long binArrayIndex(int binId) {
        return Math.floorDiv((long) binId, 70L);
    }


    // =========================================================
    // 5. BinArray Bitmap Extension PDA
    //
    // seeds:
    // [
    //     "bitmap",
    //     lbPair
    // ]
    // =========================================================

    /**
     * 推导池的 bin 位图扩展账户地址。
     *
     * @param lbPair 具体 DLMM 池地址
     * @return 位图扩展账户的 PDA
     */
    public static PublicKey bitmapExtension(PublicKey lbPair) {
        return PublicKey.findProgramAddress(List.of(BITMAP_SEED, lbPair.toByteArray()), PROGRAM_ID).getAddress();
    }


    // =========================================================
    // 6. Event Authority
    //
    // seeds:
    // [
    //     "__event_authority"
    // ]
    //
    // 注意：
    // 这里不需要 lbPair
    // 所有 Meteora DLMM 池共用这个 PDA
    // =========================================================

    /**
     * 返回所有 DLMM 池共用的事件权限 PDA。
     */
    public static PublicKey eventAuthority() {
        return PublicKey.findProgramAddress(List.of(EVENT_AUTHORITY_SEED), PROGRAM_ID).getAddress();
    }


    // =========================================================
    // 7. bin_step_seed
    //
    // binStep u16 Little Endian
    //
    // 例如:
    // 50 = 0x0032
    //
    // LE:
    // 32 00
    //
    // decimal:
    // [50, 0]
    // =========================================================

    /**
     * 把 bin 步长编码为小端 u16 种子。
     *
     * @param binStep 池使用的 bin 步长
     * @return 两字节小端种子
     */
    public static byte[] binStepSeed(int binStep) {
        return u16LE(binStep);
    }


    // =========================================================
    // 8. base_factor_seed
    //
    // baseFactor u16 Little Endian
    //
    // 例如:
    // 10000 = 0x2710
    //
    // LE:
    // 10 27
    //
    // decimal:
    // [16, 39]
    // =========================================================

    /**
     * 把基础因子编码为小端 u16 种子。
     *
     * @param baseFactor 池使用的基础因子
     * @return 两字节小端种子
     */
    public static byte[] baseFactorSeed(int baseFactor) {
        return u16LE(baseFactor);
    }


    // =========================================================
    // u16 -> Little Endian
    // =========================================================

    /**
     * 把非负整数编码为小端 u16。
     *
     * @param value 范围为 0 到 65535 的整数
     * @return 两字节小端数组
     */
    public static byte[] u16LE(int value) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException("value 超出 u16: " + value);
        }

        return new byte[]{(byte) (value & 0xff), (byte) ((value >>> 8) & 0xff)};
    }


    // =========================================================
    // i64 -> Little Endian
    //
    // BinArray index 会是负数，
    // 所以这里必须支持 signed long
    // =========================================================

    /**
     * 把有符号长整数编码为小端 i64。
     *
     * @param value 可为负数的 bin 数组索引
     * @return 八字节小端数组
     */
    public static byte[] i64LE(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }
}
