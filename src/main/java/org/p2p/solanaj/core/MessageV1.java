package org.p2p.solanaj.core;

import org.bitcoinj.core.Base58;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MessageV1 {

    /*
     * V1 version byte:
     *
     * 0x80 | 1
     * = 0x81
     */
    private static final int VERSION = 0x81;

    private static final int RECENT_BLOCK_HASH_LENGTH = 32;

    private static final int MAX_ACCOUNTS = 64;
    private static final int MAX_INSTRUCTIONS = 64;
    private static final int MAX_SIGNATURES = 12;

    private static class MessageHeader {

        int numRequiredSignatures;
        int numReadonlySignedAccounts;
        int numReadonlyUnsignedAccounts;

        byte[] toByteArray() {
            return new byte[]{(byte) numRequiredSignatures, (byte) numReadonlySignedAccounts, (byte) numReadonlyUnsignedAccounts};
        }
    }

    private static class CompiledInstruction {

        int programIdIndex;

        byte[] accountIndexes;

        byte[] data;

        CompiledInstruction(int programIdIndex, byte[] accountIndexes, byte[] data) {
            this.programIdIndex = programIdIndex;
            this.accountIndexes = accountIndexes;
            this.data = data;
        }
    }

    private String recentBlockhash;

    private final AccountKeysList accountKeys;

    private final List<TransactionInstruction> instructions;

    private Account feePayer;

    private TransactionConfigV1 config = new TransactionConfigV1();

    public MessageV1() {

        this.accountKeys = new AccountKeysList();

        this.instructions = new ArrayList<>();
    }

    public MessageV1 addInstruction(TransactionInstruction instruction) {

        accountKeys.addAll(instruction.getKeys());

        /*
         * Program ID 本身也是一个 readonly unsigned account。
         */
        accountKeys.add(new AccountMeta(instruction.getProgramId(), false, false));

        instructions.add(instruction);

        return this;
    }

    public void setRecentBlockHash(String recentBlockhash) {
        this.recentBlockhash = recentBlockhash;
    }

    public String getRecentBlockhash() {
        return recentBlockhash;
    }

    protected void setFeePayer(Account feePayer) {
        this.feePayer = feePayer;
    }

    public void setConfig(TransactionConfigV1 config) {
        this.config = config == null ? new TransactionConfigV1() : config;
    }

    public TransactionConfigV1 getConfig() {
        return config;
    }

    /*
     * 最重要的方法。
     */
    public byte[] serialize() {

        validateBasic();

        /*
         * 1.
         * 编译 account addresses。
         */
        List<AccountMeta> keysList = getAccountKeys();

        if (keysList.size() > MAX_ACCOUNTS) {
            throw new IllegalArgumentException("V1 supports max " + MAX_ACCOUNTS + " accounts, got " + keysList.size());
        }

        /*
         * 2.
         * 生成 LegacyHeader
         */
        MessageHeader header = buildHeader(keysList);

        if (header.numRequiredSignatures > MAX_SIGNATURES) {
            throw new IllegalArgumentException("Too many signatures: " + header.numRequiredSignatures);
        }

        /*
         * 3.
         * 编译 instructions。
         */
        List<CompiledInstruction> compiled = compileInstructions(keysList);

        /*
         * 4.
         * TransactionConfigMask
         */
        int configMask = buildConfigMask();

        /*
         * 5.
         * recent blockhash
         */
        byte[] blockhash = Base58.decode(recentBlockhash);

        if (blockhash.length != RECENT_BLOCK_HASH_LENGTH) {
            throw new IllegalArgumentException("Recent blockhash must decode to 32 bytes");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        /*
         * =========================
         * V1 wire format
         * =========================
         */

        /*
         * VersionByte
         */
        out.write(VERSION);

        /*
         * LegacyHeader
         *
         * 3 bytes
         */
        writeBytes(out, header.toByteArray());

        /*
         * TransactionConfigMask
         *
         * u32 little endian
         */
        writeU32LE(out, configMask);

        /*
         * LifetimeSpecifier
         *
         * 32 byte recent blockhash
         */
        writeBytes(out, blockhash);

        /*
         * NumInstructions
         *
         * u8
         */
        out.write(compiled.size());

        /*
         * NumAddresses
         *
         * u8
         */
        out.write(keysList.size());

        /*
         * Addresses
         */
        for (AccountMeta account : keysList) {

            writeBytes(out, account.getPublicKey().toByteArray());
        }

        /*
         * ConfigValues
         */
        writeConfigValues(out);

        /*
         * =========================
         * InstructionHeaders
         * =========================
         *
         * 注意：
         *
         * V1 是把所有 header
         * 一次性全部写完。
         */
        for (CompiledInstruction instruction : compiled) {

            /*
             * ProgramAccountIndex
             *
             * u8
             */
            out.write(instruction.programIdIndex);

            /*
             * NumInstructionAccounts
             *
             * u8
             */
            out.write(instruction.accountIndexes.length);

            /*
             * NumInstructionDataBytes
             *
             * u16 LE
             */
            writeU16LE(out, instruction.data.length);
        }

        /*
         * =========================
         * InstructionPayloads
         * =========================
         */
        for (CompiledInstruction instruction : compiled) {

            /*
             * account indexes
             */
            writeBytes(out, instruction.accountIndexes);

            /*
             * instruction data
             */
            writeBytes(out, instruction.data);
        }

        return out.toByteArray();
    }

    private void validateBasic() {

        if (recentBlockhash == null) {
            throw new IllegalArgumentException("recentBlockhash required");
        }

        if (feePayer == null) {
            throw new IllegalArgumentException("feePayer required");
        }

        if (instructions.isEmpty()) {
            throw new IllegalArgumentException("No instructions provided");
        }

        if (instructions.size() > MAX_INSTRUCTIONS) {
            throw new IllegalArgumentException("V1 supports max " + MAX_INSTRUCTIONS + " instructions");
        }
    }

    private MessageHeader buildHeader(List<AccountMeta> keysList) {

        MessageHeader header = new MessageHeader();

        for (AccountMeta accountMeta : keysList) {

            if (accountMeta.isSigner()) {

                header.numRequiredSignatures++;

                if (!accountMeta.isWritable()) {
                    header.numReadonlySignedAccounts++;
                }

            } else {

                if (!accountMeta.isWritable()) {
                    header.numReadonlyUnsignedAccounts++;
                }
            }
        }

        /*
         * fee payer 必须 writable signer。
         */
        if (header.numRequiredSignatures == 0) {
            throw new IllegalStateException("V1 requires at least one signer");
        }

        return header;
    }

    private List<CompiledInstruction> compileInstructions(List<AccountMeta> keysList) {

        List<CompiledInstruction> result = new ArrayList<>();

        for (TransactionInstruction instruction : instructions) {

            int keysSize = instruction.getKeys().size();

            if (keysSize > 255) {

                throw new IllegalArgumentException("Instruction has too many accounts: " + keysSize);
            }

            if (instruction.getData().length > 0xffff) {

                throw new IllegalArgumentException("Instruction data > 65535 bytes");
            }

            byte[] keyIndices = new byte[keysSize];

            for (int i = 0; i < keysSize; i++) {

                int index = findAccountIndex(keysList, instruction.getKeys().get(i).getPublicKey());

                keyIndices[i] = (byte) index;
            }

            int programIdIndex = findAccountIndex(keysList, instruction.getProgramId());

            result.add(new CompiledInstruction(programIdIndex, keyIndices, instruction.getData()));
        }

        return result;
    }

    /*
     * ==========================================
     * TransactionConfigMask
     * ==========================================
     */
    private int buildConfigMask() {

        int mask = 0;

        /*
         * bits 0 + 1
         *
         * Priority fee u64
         */
        if (config.getPriorityFeeLamports() != null) {

            mask |= 1 << 0;
            mask |= 1 << 1;
        }

        /*
         * bit 2
         *
         * compute-unit-limit
         */
        if (config.getComputeUnitLimit() != null) {

            mask |= 1 << 2;
        }

        /*
         * bit 3
         *
         * loaded account data size
         */
        if (config.getLoadedAccountsDataSizeLimit() != null) {

            mask |= 1 << 3;
        }

        /*
         * bit 4
         *
         * heap size
         */
        if (config.getHeapSize() != null) {

            mask |= 1 << 4;
        }

        return mask;
    }

    private void writeConfigValues(ByteArrayOutputStream out) {

        /*
         * Config values 必须按照 bit 顺序写。
         *
         * bits 0+1:
         * u64 priority fee
         */
        if (config.getPriorityFeeLamports() != null) {

            writeU64LE(out, config.getPriorityFeeLamports());
        }

        /*
         * bit 2
         */
        if (config.getComputeUnitLimit() != null) {

            writeU32LE(out, config.getComputeUnitLimit());
        }

        /*
         * bit 3
         */
        if (config.getLoadedAccountsDataSizeLimit() != null) {

            writeU32LE(out, config.getLoadedAccountsDataSizeLimit());
        }

        /*
         * bit 4
         */
        if (config.getHeapSize() != null) {

            int heap = config.getHeapSize();

            if (heap < 32 * 1024 || heap > 256 * 1024 || heap % 1024 != 0) {

                throw new IllegalArgumentException("V1 heapSize must be 32KB~256KB " + "and multiple of 1KB");
            }

            writeU32LE(out, heap);
        }
    }

    /*
     * ==========================================
     * Accounts
     * ==========================================
     */
    private List<AccountMeta> getAccountKeys() {

        List<AccountMeta> keysList = new ArrayList<>(accountKeys.getList());

        /*
         * fee payer 必须存在。
         */
        int feePayerIndex = findAccountIndex(keysList, feePayer.getPublicKey());

        AccountMeta feePayerMeta = keysList.get(feePayerIndex);

        /*
         * 先确保 payer:
         *
         * signer = true
         * writable = true
         */
        List<AccountMeta> result = new ArrayList<>();

        result.add(new AccountMeta(feePayerMeta.getPublicKey(), true, true));

        String payer = feePayer.getPublicKey().toBase58();

        /*
         * V1 / LegacyHeader 对 accounts 顺序有要求：
         *
         * 1. writable signers
         * 2. readonly signers
         * 3. writable unsigned
         * 4. readonly unsigned
         */

        /*
         * additional writable signers
         */
        keysList.stream().filter(d -> !d.getPublicKey().toBase58().equals(payer) && d.isSigner() && d.isWritable()).forEach(result::add);

        /*
         * readonly signers
         */
        keysList.stream().filter(d -> !d.getPublicKey().toBase58().equals(payer) && d.isSigner() && !d.isWritable()).forEach(result::add);

        /*
         * writable unsigned
         */
        keysList.stream().filter(d -> !d.getPublicKey().toBase58().equals(payer) && !d.isSigner() && d.isWritable()).forEach(result::add);

        /*
         * readonly unsigned
         */
        keysList.stream().filter(d -> !d.getPublicKey().toBase58().equals(payer) && !d.isSigner() && !d.isWritable()).forEach(result::add);

        /*
         * 防止 AccountKeysList
         * 如果内部没有完全去重。
         */
        List<AccountMeta> unique = new ArrayList<>();

        for (AccountMeta meta : result) {

            boolean exists = unique.stream().anyMatch(d -> d.getPublicKey().equals(meta.getPublicKey()));

            if (!exists) {
                unique.add(meta);
            }
        }

        return unique;
    }

    private int findAccountIndex(List<AccountMeta> accountMetaList, PublicKey key) {

        for (int i = 0; i < accountMetaList.size(); i++) {

            if (accountMetaList.get(i).getPublicKey().equals(key)) {

                return i;
            }
        }

        throw new RuntimeException("Unable to find account index: " + key.toBase58());
    }

    /*
     * ==========================================
     * Little Endian writers
     * ==========================================
     */

    private static void writeU16LE(ByteArrayOutputStream out, int value) {

        out.write(value & 0xff);

        out.write((value >>> 8) & 0xff);
    }

    private static void writeU32LE(ByteArrayOutputStream out, long value) {

        out.write((int) value & 0xff);

        out.write((int) (value >>> 8) & 0xff);

        out.write((int) (value >>> 16) & 0xff);

        out.write((int) (value >>> 24) & 0xff);
    }

    private static void writeU64LE(ByteArrayOutputStream out, long value) {

        for (int i = 0; i < 8; i++) {

            out.write((int) (value >>> (i * 8)) & 0xff);
        }
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {

        out.write(bytes, 0, bytes.length);
    }
}