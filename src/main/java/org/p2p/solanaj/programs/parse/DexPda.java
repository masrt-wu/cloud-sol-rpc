package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 各协议共用的本地地址推导工具；只计算地址，不查询或创建链上账户。 */
public final class DexPda {
    /** 普通 SPL Token 程序。 */
    public static final PublicKey TOKEN_PROGRAM = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    /** Token-2022 程序。 */
    public static final PublicKey TOKEN_2022_PROGRAM = new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");
    /** 关联代币账户程序。 */
    public static final PublicKey ATA_PROGRAM = new PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");

    private DexPda() { }

    /**
     * 推导钱包或池 PDA 的 ATA；必须传入该 mint 实际所属的代币程序。
     * @param owner 代币账户的持有者，可以是钱包或池 PDA
     * @param mint 代币铸币账户
     * @param tokenProgram 普通 Token 或 Token-2022 程序
     * @return ATA 地址；地址能推导不代表账户已经存在
     */
    public static PublicKey associatedTokenAccount(PublicKey owner, PublicKey mint, PublicKey tokenProgram) {
        requireTokenProgram(tokenProgram);
        return derive(ATA_PROGRAM, key(owner), key(tokenProgram), key(mint));
    }

    /**
     * 将 DLMM、DAMM v2 的代币程序标记转换成实际程序地址。
     * @param flag 协议中的标记，零为普通 Token，一为 Token-2022
     * @return 对应代币程序
     */
    public static PublicKey tokenProgramFromFlag(int flag) {
        return switch (flag) {
            case 0 -> new PublicKey(TOKEN_PROGRAM.toByteArray());
            case 1 -> new PublicKey(TOKEN_2022_PROGRAM.toByteArray());
            default -> throw new IllegalArgumentException("未知代币程序标记：" + flag);
        };
    }

    /** 检查代币程序，防止把 Token-2022 的 ATA 按普通 Token 推导。 */
    static void requireTokenProgram(PublicKey program) {
        if (!TOKEN_PROGRAM.equals(program) && !TOKEN_2022_PROGRAM.equals(program))
            throw new IllegalArgumentException("不是 Token 或 Token-2022 程序：" + program);
    }

    /** 统一调用 SDK 的 PDA 查找逻辑。 */
    static PublicKey derive(PublicKey program, byte[]... seeds) {
        Objects.requireNonNull(program, "程序地址不能为空");
        return PublicKey.findProgramAddress(Arrays.asList(seeds), program).getAddress();
    }

    /** 将协议固定文本种子转换成 UTF-8 字节。 */
    static byte[] seed(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    /** 获取公钥种子的副本，避免外部修改影响计算。 */
    static byte[] key(PublicKey key) {
        byte[] bytes = Objects.requireNonNull(key, "公钥不能为空").toByteArray();
        if (bytes.length != 32) throw new IllegalArgumentException("公钥必须是三十二字节");
        return bytes.clone();
    }

    /** 比较公钥的原始无符号字节，不按 Base58 文本排序。 */
    static List<PublicKey> ordered(PublicKey a, PublicKey b, boolean ascending) {
        int compare = Arrays.compareUnsigned(key(a), key(b));
        if (compare == 0) throw new IllegalArgumentException("池的两个 mint 不能相同");
        return (compare < 0) == ascending ? List.of(a, b) : List.of(b, a);
    }

    /** 编码二字节无符号种子；Raydium 配置索引用大端，Pump/Orca 用小端。 */
    static byte[] u16(int value, ByteOrder order) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException("数值超出 u16 范围");
        return ByteBuffer.allocate(2).order(order).putShort((short) value).array();
    }

    /** 编码有符号四字节种子。 */
    static byte[] i32(int value, ByteOrder order) {
        return ByteBuffer.allocate(4).order(order).putInt(value).array();
    }

    /** 编码 DLMM BinArray 的有符号小端八字节索引。 */
    static byte[] i64(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    /** 编码配置索引等 u64 种子，保留完整无符号范围。 */
    static byte[] u64(BigInteger value) {
        Objects.requireNonNull(value, "数值不能为空");
        if (value.signum() < 0 || value.bitLength() > 64) throw new IllegalArgumentException("数值超出 u64 范围");
        return i64(value.longValue());
    }

    /** 验证单字节种子范围。 */
    static byte[] u8(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("数值超出 u8 范围");
        return new byte[]{(byte) value};
    }
}
