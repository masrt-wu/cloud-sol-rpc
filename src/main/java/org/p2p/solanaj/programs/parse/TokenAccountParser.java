package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 解析普通 Token 与 Token-2022 的账户余额、mint 供应量和原始扩展，不访问 RPC。 */
public final class TokenAccountParser {
    private TokenAccountParser() { }

    /**
     * 代币账户当前状态；金额均为代币最小单位。
     * @param mint 账户对应的 mint
     * @param owner 持有代币的用户或池权限地址，不是账户所属程序
     * @param amount 实际可用代币余额，不包含 Token-2022 已扣留的转账费
     * @param state 零为未初始化、一为正常、二为冻结
     * @param nativeRentReserve WSOL 租金保留量；非原生代币账户为 null
     * @param delegate 可选授权地址
     * @param delegatedAmount 已授权的额度
     * @param closeAuthority 可选关闭权限
     * @param extensions 以扩展类型编号为键的原始 TLV 数据
     */
    public record TokenAccount(PublicKey mint, PublicKey owner, BigInteger amount, int state,
                               BigInteger nativeRentReserve, PublicKey delegate, BigInteger delegatedAmount,
                               PublicKey closeAuthority, Map<Integer, byte[]> extensions) {
        public TokenAccount {
            mint = copyKey(mint); owner = copyKey(owner); delegate = copyKey(delegate);
            closeAuthority = copyKey(closeAuthority); extensions = copyExtensions(extensions);
        }
        /** @return mint 公钥副本 */
        @Override public PublicKey mint() { return copyKey(mint); }
        /** @return 用户或池权限公钥副本 */
        @Override public PublicKey owner() { return copyKey(owner); }
        /** @return 可选授权公钥副本 */
        @Override public PublicKey delegate() { return copyKey(delegate); }
        /** @return 可选关闭权限公钥副本 */
        @Override public PublicKey closeAuthority() { return copyKey(closeAuthority); }
        /** @return 扩展数据副本 */
        @Override public Map<Integer, byte[]> extensions() { return copyExtensions(extensions); }
        /** @return 扣留在此账户中的转账费；不存在该扩展时返回零 */
        public BigInteger withheldTransferFee() {
            byte[] fee = extensions.get(2);
            if (fee == null) return BigInteger.ZERO;
            if (fee.length != 8) throw new IllegalArgumentException("转账费账户扩展长度不正确");
            return unsigned(fee, 0, 8);
        }
    }

    /**
     * mint 的当前供应和权限。
     * @param supply 铸币供应量，保持 u64 完整精度
     * @param decimals 小数位数
     * @param initialized 是否已初始化
     * @param mintAuthority 可选铸币权限
     * @param freezeAuthority 可选冻结权限
     * @param extensions 以扩展类型编号为键的原始 TLV 数据
     */
    public record Mint(BigInteger supply, int decimals, boolean initialized, PublicKey mintAuthority,
                       PublicKey freezeAuthority, Map<Integer, byte[]> extensions) {
        public Mint {
            mintAuthority = copyKey(mintAuthority); freezeAuthority = copyKey(freezeAuthority);
            extensions = copyExtensions(extensions);
        }
        /** @return 可选铸币权限公钥副本 */
        @Override public PublicKey mintAuthority() { return copyKey(mintAuthority); }
        /** @return 可选冻结权限公钥副本 */
        @Override public PublicKey freezeAuthority() { return copyKey(freezeAuthority); }
        /** @return 扩展数据副本 */
        @Override public Map<Integer, byte[]> extensions() { return copyExtensions(extensions); }
    }

    /**
     * 解析 vault、用户 ATA 或池持有的 vault LP 账户。
     * @param tokenProgram 该账户链上 owner，必须为 Token 或 Token-2022
     * @param data 账户原始字节，不含 RPC 的 Base64 文本包装
     * @return 余额、权限和扩展
     */
    public static TokenAccount parseTokenAccount(PublicKey tokenProgram, byte[] data) {
        DexPda.requireTokenProgram(tokenProgram);
        require(data, 165);
        int state = Byte.toUnsignedInt(data[108]);
        if (state > 2) throw new IllegalArgumentException("代币账户状态编码无效");
        int nativeOption = option(data, 109);
        return new TokenAccount(key(data, 0), key(data, 32), unsigned(data, 64, 8), state,
                nativeOption == 0 ? null : unsigned(data, 113, 8), optionalKey(data, 72),
                unsigned(data, 121, 8), optionalKey(data, 129), extensions(tokenProgram, data, 2, 165));
    }

    /**
     * 解析 mint；DAMM v1 计算 vault LP 底层份额时需要最新 supply。
     * @param tokenProgram 该 mint 的链上 owner
     * @param data mint 原始字节
     * @return 供应量、精度和权限
     */
    public static Mint parseMint(PublicKey tokenProgram, byte[] data) {
        DexPda.requireTokenProgram(tokenProgram);
        require(data, 82);
        int initialized = Byte.toUnsignedInt(data[45]);
        if (initialized > 1) throw new IllegalArgumentException("mint 初始化标记无效");
        return new Mint(unsigned(data, 36, 8), Byte.toUnsignedInt(data[44]), initialized == 1,
                optionalKey(data, 0), optionalKey(data, 46), extensions(tokenProgram, data, 1, 82));
    }

    /** 读取可选公钥；Token 基础布局使用四字节 COption 标记。 */
    private static PublicKey optionalKey(byte[] data, int offset) {
        return option(data, offset) == 0 ? null : key(data, offset + 4);
    }

    /** 验证 COption 标记，不能把任意非零值当成已存在。 */
    private static int option(byte[] data, int offset) {
        BigInteger value = unsigned(data, offset, 4);
        if (value.compareTo(BigInteger.ONE) > 0) throw new IllegalArgumentException("代币权限标记无效");
        return value.intValue();
    }

    /** 读取已验证范围内的公钥。 */
    private static PublicKey key(byte[] data, int offset) {
        return new PublicKey(Arrays.copyOfRange(data, offset, offset + 32));
    }

    /** 复制可选公钥；项目 PublicKey 的字节数组访问器可变，不能直接共享。 */
    private static PublicKey copyKey(PublicKey key) {
        return key == null ? null : new PublicKey(key.toByteArray());
    }

    /** 读取无符号小端整数。 */
    static BigInteger unsigned(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 1 || offset > data.length - length)
            throw new IllegalArgumentException("代币账户字段被截断");
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[length - i - 1] = data[offset + i];
        return new BigInteger(1, bytes);
    }

    /** 验证基础账户长度。 */
    private static void require(byte[] data, int length) {
        if (data == null || data.length < length) throw new IllegalArgumentException("代币账户数据长度不足：" + length);
    }

    /** 验证 Token-2022 类型标记并解析 TLV；未知扩展保留原始内容。 */
    private static Map<Integer, byte[]> extensions(PublicKey program, byte[] data, int accountType, int baseLength) {
        Map<Integer, byte[]> extensions = new LinkedHashMap<>();
        if (DexPda.TOKEN_PROGRAM.equals(program)) {
            if (data.length != baseLength) throw new IllegalArgumentException("普通 Token 基础账户长度不匹配");
            return extensions;
        }
        if (data.length == baseLength) return extensions;
        require(data, 166);
        if (data.length == 355) throw new IllegalArgumentException("多签账户不能按 mint 或代币账户解析");
        for (int i = baseLength; i < 165; i++)
            if (data[i] != 0) throw new IllegalArgumentException("Token-2022 mint 填充区必须为零");
        if (Byte.toUnsignedInt(data[165]) != accountType)
            throw new IllegalArgumentException("Token-2022 账户类型不匹配");
        int position = 166;
        while (position < data.length) {
            if (data.length - position < 2) { requireZeroTail(data, position); break; }
            int type = unsigned(data, position, 2).intValue();
            if (type == 0) { requireZeroTail(data, position); break; }
            if (data.length - position < 4) throw new IllegalArgumentException("Token-2022 扩展头被截断");
            int length = unsigned(data, position + 2, 2).intValue();
            position += 4;
            if (length > data.length - position) throw new IllegalArgumentException("Token-2022 扩展长度无效");
            if (accountType == 2 && type == 2 && length != 8)
                throw new IllegalArgumentException("转账费账户扩展长度不正确");
            if (extensions.putIfAbsent(type, Arrays.copyOfRange(data, position, position + length)) != null)
                throw new IllegalArgumentException("Token-2022 扩展类型重复：" + type);
            position += length;
        }
        return extensions;
    }

    /** 仅在空扩展尾部扫描剩余数据，避免每解析一个扩展都重复扫描整段缓冲区。 */
    private static void requireZeroTail(byte[] data, int offset) {
        for (int i = offset; i < data.length; i++)
            if (data[i] != 0) throw new IllegalArgumentException("Token-2022 未初始化扩展尾部含有非零数据");
    }

    /** 深复制扩展数组，避免共享订阅缓冲区。 */
    private static Map<Integer, byte[]> copyExtensions(Map<Integer, byte[]> source) {
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        source.forEach((type, bytes) -> result.put(type, bytes.clone()));
        return Collections.unmodifiableMap(result);
    }
}
