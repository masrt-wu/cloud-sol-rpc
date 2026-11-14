package org.p2p.solanaj.router.pda;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;

/**
 * 已知池型的热路径字段读取器。只读取路由建单需要的固定偏移，
 * 不加载 JSON、不查 RPC、不校验 owner 或 Anchor 判别符。
 * 调用方必须先确认原始数据属于目标池；Java 数组边界检查仍会拒绝截断数据。
 */
public final class TrustedPoolFields {
    private static final PublicKey[] TOKEN_PROGRAMS = {
            TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_2022
    };

    private TrustedPoolFields() { }

    /**
     * 从固定偏移拷贝公钥；不要让后续复用的 RPC 缓冲区影响结果。
     * @param data 可信账户原始字节
     * @param offset 公钥的起始偏移
     * @return 独立的公钥对象
     */
    public static PublicKey key(byte[] data, int offset) {
        byte[] bytes = new byte[32];
        System.arraycopy(data, offset, bytes, 0, 32);
        return new PublicKey(bytes);
    }

    /**
     * 读取无符号 u8。
     * @param data 可信账户原始字节
     * @param offset 字段偏移
     * @return 零到 255 的值
     */
    public static int u8(byte[] data, int offset) {
        return data[offset] & 0xff;
    }

    /**
     * 读取小端 u16。
     * @param data 可信账户原始字节
     * @param offset 字段偏移
     * @return 零到 65535 的值
     */
    public static int u16(byte[] data, int offset) {
        return u8(data, offset) | (u8(data, offset + 1) << 8);
    }

    /**
     * 读取小端有符号 i32；负 tick 和 bin ID 保留符号。
     * @param data 可信账户原始字节
     * @param offset 字段偏移
     * @return 有符号整数
     */
    public static int i32(byte[] data, int offset) {
        return u8(data, offset) | (u8(data, offset + 1) << 8)
                | (u8(data, offset + 2) << 16) | (data[offset + 3] << 24);
    }

    /**
     * 将已知池内的 0/1 Token Program 标记映射为实际程序地址。
     * 不执行额外标记检查；未知标记由 Java 数组边界保护拒绝。
     * @param data 可信账户原始字节
     * @param offset 标记字段偏移
     * @return 标记对应的 Token Program
     */
    public static PublicKey tokenProgram(byte[] data, int offset) {
        return TOKEN_PROGRAMS[u8(data, offset)];
    }

    /**
     * 将已解析的 0/1 标记映射为 Token 程序，不重新读取池账户。
     * @param flag 通用解析结果中的程序标记
     * @return 对应的 Token 程序地址
     */
    public static PublicKey tokenProgram(int flag) {
        return TOKEN_PROGRAMS[flag];
    }
}
