package org.p2p.solanaj.router;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 自部署 Router 的 Route 指令中封装的一跳 DEX CPI。 */
public final class RouterLeg {
    /** 本跳要调用的 DEX 程序地址，不是具体池地址。 */
    private final PublicKey programId;
    /** 按目标 DEX 指令要求排列的账户元数据。 */
    private final List<AccountMeta> accounts;
    /** 目标 DEX 的原始指令数据；构造时复制，避免外部修改。 */
    private final byte[] data;
    /** 本跳花费的代币 mint。 */
    private final PublicKey inputMint;
    /** 本跳收到的代币 mint。 */
    private final PublicKey outputMint;
    /** 本跳精确输入数量，单位为代币最小单位。 */
    private final long amountInRaw;
    /** 本跳最低可接受输出数量，单位为代币最小单位。 */
    private final long minOutRaw;

    /**
     * 创建一跳路由，并校验账户数量、指令长度、代币方向及原始数量。
     *
     * @param programId 目标 DEX 程序地址
     * @param accounts CPI 使用的账户，顺序须与目标 DEX 一致
     * @param data DEX 原始指令数据
     * @param inputMint 输入代币 mint
     * @param outputMint 输出代币 mint
     * @param amountInRaw 精确输入数量，代币最小单位
     * @param minOutRaw 最低输出数量，代币最小单位
     */
    public RouterLeg(PublicKey programId, List<AccountMeta> accounts, byte[] data,
                     PublicKey inputMint, PublicKey outputMint, long amountInRaw, long minOutRaw) {
        this.programId = Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(data, "data");
        if (accounts.isEmpty() || accounts.size() > 64) {
            throw new IllegalArgumentException("Router leg requires 1..64 accounts");
        }
        if (data.length == 0 || data.length > 0xffff) {
            throw new IllegalArgumentException("Router leg data requires 1..65535 bytes");
        }
        this.accounts = List.copyOf(new ArrayList<>(accounts));
        this.data = Arrays.copyOf(data, data.length);
        this.inputMint = Objects.requireNonNull(inputMint, "inputMint");
        this.outputMint = Objects.requireNonNull(outputMint, "outputMint");
        if (inputMint.equals(outputMint) || amountInRaw <= 0 || minOutRaw <= 0) {
            throw new IllegalArgumentException("invalid leg mint direction or raw amounts");
        }
        this.amountInRaw = amountInRaw;
        this.minOutRaw = minOutRaw;
    }

    /** 返回本跳调用的 DEX 程序地址。 */
    public PublicKey programId() { return programId; }
    /** 返回按 DEX 指令顺序排列的账户元数据。 */
    public List<AccountMeta> accounts() { return accounts; }
    /** 返回指令数据副本，防止调用方修改已构造的路由。 */
    public byte[] data() { return Arrays.copyOf(data, data.length); }
    /** 返回本跳输入代币的 mint。 */
    public PublicKey inputMint() { return inputMint; }
    /** 返回本跳输出代币的 mint。 */
    public PublicKey outputMint() { return outputMint; }
    /** 返回本跳固定输入量，单位为代币最小单位。 */
    public long amountInRaw() { return amountInRaw; }
    /** 返回本跳最低输出量，单位为代币最小单位。 */
    public long minOutRaw() { return minOutRaw; }
}
