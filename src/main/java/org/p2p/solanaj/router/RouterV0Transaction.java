package org.p2p.solanaj.router;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ShortvecEncoding;
import org.p2p.solanaj.utils.TweetNaclFast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 使用调用方提供的地址查找表快照，离线编译并签名标准 Solana v0 交易。 */
public final class RouterV0Transaction {
    /** 工具类不允许实例化。 */
    private RouterV0Transaction() { }

    /**
     * 地址查找表快照；地址列表顺序必须与已生效的链上 ALT 完全一致。
     * @param accountKey ALT 账户地址
     * @param addresses ALT 中按链上索引排列的地址列表
     */
    public record LookupTable(PublicKey accountKey, List<PublicKey> addresses) {
        /** 校验 ALT 地址和长度，并复制传入的地址列表。 */
        public LookupTable {
            Objects.requireNonNull(accountKey, "accountKey");
            addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses"));
            if (addresses.size() > 256) throw new IllegalArgumentException("ALT has more than 256 addresses");
        }
    }

    /** 汇总同一公钥在所有指令中的签名、可写及程序调用权限。 */
    private static final class Flags {
        /** 该公钥是否必须签名。 */
        boolean signer;
        /** 该公钥是否必须可写。 */
        boolean writable;
        /** 该公钥是否作为程序 ID 被指令调用。 */
        boolean invoked;

        /**
         * 合并同一账户在多条指令中的权限，任一指令要求为真即保留。
         * @param signer 本条指令是否要求签名
         * @param writable 本条指令是否要求可写
         * @param invoked 本账户是否作为程序被调用
         */
        void merge(boolean signer, boolean writable, boolean invoked) {
            this.signer |= signer;
            this.writable |= writable;
            this.invoked |= invoked;
        }
    }

    /** 记录一个 ALT 中被实际引用的可写与只读索引及对应地址。 */
    private static final class ResolvedLookup {
        /** 当前 ALT 的账户地址。 */
        final PublicKey table;
        /** 本交易引用的 ALT 可写地址索引。 */
        final List<Integer> writableIndexes = new ArrayList<>();
        /** 本交易引用的 ALT 只读地址索引。 */
        final List<Integer> readonlyIndexes = new ArrayList<>();
        /** 与可写索引对应的公钥，供交易账户索引表使用。 */
        final List<PublicKey> writableAddresses = new ArrayList<>();
        /** 与只读索引对应的公钥，供交易账户索引表使用。 */
        final List<PublicKey> readonlyAddresses = new ArrayList<>();

        /**
         * 初始化一个已解析的 ALT 引用。
         * @param table 地址查找表账户地址
         */
        ResolvedLookup(PublicKey table) { this.table = table; }
        /** 判断本交易是否实际使用了该 ALT 中的地址。 */
        boolean isUsed() { return !writableIndexes.isEmpty() || !readonlyIndexes.isEmpty(); }
    }

    /**
     * 编译并签名一笔 v0 交易；仅返回序列化字节，不连接 RPC 或广播。
     * @param signer 付款和签名账户
     * @param recentBlockhash 本次交易的有效区块哈希
     * @param instructions 按执行顺序排列的指令
     * @param lookupTables 已在链上生效的地址查找表快照
     * @return 已签名的 v0 交易序列化字节
     */
    public static byte[] compileAndSign(Account signer, String recentBlockhash,
                                        List<TransactionInstruction> instructions,
                                        List<LookupTable> lookupTables) {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(recentBlockhash, "recentBlockhash");
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        lookupTables = List.copyOf(Objects.requireNonNull(lookupTables, "lookupTables"));
        if (instructions.isEmpty()) throw new IllegalArgumentException("v0 transaction has no instructions");
        PublicKey payer = signer.getPublicKey();
        LinkedHashMap<PublicKey, Flags> flags = new LinkedHashMap<>();
        add(flags, payer, true, true, false);
        for (TransactionInstruction ix : instructions) {
            for (AccountMeta meta : ix.getKeys())
                add(flags, meta.getPublicKey(), meta.isSigner(), meta.isWritable(), false);
            add(flags, ix.getProgramId(), false, false, true);
        }
        for (Map.Entry<PublicKey, Flags> entry : flags.entrySet()) {
            if (entry.getValue().signer && !entry.getKey().equals(payer))
                throw new IllegalArgumentException("v0 helper accepts only the payer as signer");
        }

        // 只把非签名且不是程序 ID 的账户放入 ALT，避免同一地址被重复分配。
        Map<PublicKey, Boolean> assigned = new HashMap<>();
        List<ResolvedLookup> resolved = new ArrayList<>();
        for (LookupTable table : lookupTables) {
            ResolvedLookup entry = new ResolvedLookup(table.accountKey());
            for (int i = 0; i < table.addresses().size(); i++) {
                PublicKey address = table.addresses().get(i);
                Flags meta = flags.get(address);
                if (meta == null || meta.signer || meta.invoked || assigned.containsKey(address)) continue;
                if (meta.writable) {
                    entry.writableIndexes.add(i);
                    entry.writableAddresses.add(address);
                } else {
                    entry.readonlyIndexes.add(i);
                    entry.readonlyAddresses.add(address);
                }
                assigned.put(address, true);
            }
            if (entry.isUsed()) resolved.add(entry);
        }

        // 静态账户先排付款人，再排可写账户，最后排只读账户。
        List<PublicKey> staticKeys = new ArrayList<>();
        staticKeys.add(payer);
        for (Map.Entry<PublicKey, Flags> e : flags.entrySet())
            if (!e.getKey().equals(payer) && !assigned.containsKey(e.getKey()) && e.getValue().writable)
                staticKeys.add(e.getKey());
        int readonlyStart = staticKeys.size();
        for (Map.Entry<PublicKey, Flags> e : flags.entrySet())
            if (!e.getKey().equals(payer) && !assigned.containsKey(e.getKey()) && !e.getValue().writable)
                staticKeys.add(e.getKey());
        int readonlyUnsigned = staticKeys.size() - readonlyStart;

        // 编译指令前先建立“静态账户 + ALT 可写 + ALT 只读”的统一索引表。
        List<PublicKey> allKeys = new ArrayList<>(staticKeys);
        for (ResolvedLookup entry : resolved) allKeys.addAll(entry.writableAddresses);
        for (ResolvedLookup entry : resolved) allKeys.addAll(entry.readonlyAddresses);
        if (allKeys.size() > 64) throw new IllegalArgumentException("v0 route exceeds 64 account keys");
        Map<PublicKey, Integer> keyIndex = new HashMap<>();
        for (int i = 0; i < allKeys.size(); i++) keyIndex.put(allKeys.get(i), i);

        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(0x80); // v0 消息版本前缀。
        message.write(1); // 仅付款人需要签名。
        message.write(0); // 不存在只读签名账户。
        message.write(readonlyUnsigned);
        writeLen(message, staticKeys.size());
        for (PublicKey key : staticKeys) message.writeBytes(key.toByteArray());
        byte[] hash = Base58.decode(recentBlockhash);
        if (hash.length != 32) throw new IllegalArgumentException("recent blockhash must be 32 bytes");
        message.writeBytes(hash);
        writeLen(message, instructions.size());
        for (TransactionInstruction ix : instructions) {
            message.write(keyIndex.get(ix.getProgramId()));
            writeLen(message, ix.getKeys().size());
            for (AccountMeta meta : ix.getKeys()) message.write(keyIndex.get(meta.getPublicKey()));
            byte[] data = ix.getData();
            writeLen(message, data.length);
            message.writeBytes(data);
        }
        writeLen(message, resolved.size());
        for (ResolvedLookup entry : resolved) {
            message.writeBytes(entry.table.toByteArray());
            writeLen(message, entry.writableIndexes.size());
            for (int index : entry.writableIndexes) message.write(index);
            writeLen(message, entry.readonlyIndexes.size());
            for (int index : entry.readonlyIndexes) message.write(index);
        }

        // 签名覆盖完整消息字节；返回值仍须由调用方自行模拟或广播。
        byte[] messageBytes = message.toByteArray();
        byte[] signature = new TweetNaclFast.Signature(new byte[0], signer.getSecretKey())
                .detached(messageBytes);
        if (signature.length != 64) throw new IllegalStateException("invalid signature length");
        ByteArrayOutputStream transaction = new ByteArrayOutputStream(messageBytes.length + 65);
        writeLen(transaction, 1);
        transaction.writeBytes(signature);
        transaction.writeBytes(messageBytes);
        byte[] encoded = transaction.toByteArray();
        if (encoded.length > 1232)
            throw new IllegalArgumentException("v0 transaction still exceeds 1232 bytes ("
                    + encoded.length + "); add more route accounts to activated lookup tables");
        return Arrays.copyOf(encoded, encoded.length);
    }

    /**
     * 合并账户在多条指令中的权限，保留最宽的签名和可写要求。
     * @param flags 当前交易的账户权限映射
     * @param key 待合并的账户公钥
     * @param signer 本次引用是否要求签名
     * @param writable 本次引用是否要求可写
     * @param invoked 本次引用是否把账户作为程序调用
     */
    private static void add(Map<PublicKey, Flags> flags, PublicKey key, boolean signer,
                            boolean writable, boolean invoked) {
        Flags current = flags.computeIfAbsent(Objects.requireNonNull(key), ignored -> new Flags());
        current.merge(signer, writable, invoked);
    }

    /**
     * 按 Solana shortvec 格式写入数组长度。
     * @param out 目标字节流
     * @param length 待编码的数组长度
     */
    private static void writeLen(ByteArrayOutputStream out, int length) {
        out.writeBytes(ShortvecEncoding.encodeLength(length));
    }
}
