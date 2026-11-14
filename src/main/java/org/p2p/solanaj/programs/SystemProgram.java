package org.p2p.solanaj.programs;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

import cn.hutool.core.util.HexUtil;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.core.AccountMeta;

import static org.bitcoinj.core.Utils.*;

/**
 * Represents the System Program on the Solana blockchain.
 * This class provides methods to create various system instructions.
 */
public class SystemProgram {
    /** The program ID for the System Program */
    public static final PublicKey PROGRAM_ID = new PublicKey("11111111111111111111111111111111");


    public static final PublicKey Block_Hashes_ID = new PublicKey("SysvarRecentB1ockHashes11111111111111111111");

    /**
     * The public key of the Solana rent sysvar.
     * This sysvar provides information about the rent exempt minimum balance.
     */
    public static final PublicKey SYSVAR_RENT_PUBKEY = new PublicKey("SysvarRent111111111111111111111111111111111");

    private static final int PROGRAM_INDEX_CREATE_ACCOUNT = 0;
    private static final int PROGRAM_INDEX_ASSIGN = 1;
    private static final int PROGRAM_INDEX_TRANSFER = 2;

    private static final int UINT32_SIZE = 4;
    private static final int INT64_SIZE = 8;
    private static final int PUBKEY_SIZE = 32;
    public static final int ADVANCE_NONCE_INSTRUCTION = 4;

    private SystemProgram() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a transfer instruction.
     *
     * @param fromPublicKey The public key of the account to transfer from
     * @param toPublicKey The public key of the account to transfer to
     * @param lamports The number of lamports to transfer
     * @return A TransactionInstruction object representing the transfer instruction
     * @throws IllegalArgumentException if lamports is negative
     */
    public static TransactionInstruction transfer(PublicKey fromPublicKey, PublicKey toPublicKey, long lamports) {
        if (lamports < 0) {
            throw new IllegalArgumentException("Lamports must be non-negative");
        }

        List<AccountMeta> keys = Arrays.asList(
            new AccountMeta(fromPublicKey, true, true),
            new AccountMeta(toPublicKey, false, true)
        );

        byte[] data = new byte[UINT32_SIZE + INT64_SIZE]; // 4 bytes for program index, 8 bytes for lamports
        uint32ToByteArrayLE(PROGRAM_INDEX_TRANSFER, data, 0);
        int64ToByteArrayLE(lamports, data, UINT32_SIZE);

        return new TransactionInstruction(PROGRAM_ID, keys, data);
    }

    /**
     * Creates an instruction to create a new account.
     *
     * @param fromPublicKey The public key of the account funding the new account
     * @param newAccountPublicKey The public key of the new account to be created
     * @param lamports The number of lamports to transfer to the new account
     * @param space The amount of space in bytes to allocate to the new account
     * @param programId The program id to assign as the owner of the new account
     * @return A TransactionInstruction object representing the create account instruction
     * @throws IllegalArgumentException if lamports or space is negative
     */
    public static TransactionInstruction createAccount(PublicKey fromPublicKey, PublicKey newAccountPublicKey,
            long lamports, long space, PublicKey programId) {
        if (lamports < 0 || space < 0) {
            throw new IllegalArgumentException("Lamports and space must be non-negative");
        }

        List<AccountMeta> keys = Arrays.asList(
            new AccountMeta(fromPublicKey, true, true),
            new AccountMeta(newAccountPublicKey, true, true)
        );

        byte[] data = new byte[UINT32_SIZE + INT64_SIZE + INT64_SIZE + PUBKEY_SIZE]; // 4 + 8 + 8 + 32 = 52 bytes
        uint32ToByteArrayLE(PROGRAM_INDEX_CREATE_ACCOUNT, data, 0);
        int64ToByteArrayLE(lamports, data, UINT32_SIZE);
        int64ToByteArrayLE(space, data, UINT32_SIZE + INT64_SIZE);
        System.arraycopy(programId.toByteArray(), 0, data, UINT32_SIZE + INT64_SIZE + INT64_SIZE, PUBKEY_SIZE);

        return new TransactionInstruction(PROGRAM_ID, keys, data);
    }

    public static TransactionInstruction createNonceAccount(
            PublicKey payer,
            PublicKey nonceAccount,
            long lamports
    ) {
        // createAccount 部分
        List<AccountMeta> keys = Arrays.asList(
                new AccountMeta(payer, true, true),
                new AccountMeta(nonceAccount, true, true) // ← 必须 true!
        );

        byte[] data = new byte[4 + 8 + 8 + 32]; // instruction(4) + lamports(8) + space(8) + programId(32)
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0); // CreateAccount instruction index
        buffer.putLong(lamports);
        buffer.putLong(80); // space for nonce account
        buffer.put(SystemProgram.PROGRAM_ID.toByteArray());

        return new TransactionInstruction(SystemProgram.PROGRAM_ID, keys, buffer.array());
    }

    /**
     * 手动构造 CreateAccountWithSeed 指令
     *
     * 对应 Rust 中的 SystemInstruction::CreateAccountWithSeed( base, seed, lamports, space, program_id )
     */
    public static TransactionInstruction createAccountWithSeedInstruction(
            PublicKey fromPublicKey,
            PublicKey newAccountPubkey,
            PublicKey basePublicKey,
            String seed,
            long lamports,
            long space,
            PublicKey programId
    ) {
        // 指令索引：3 (CreateAccountWithSeed)
        int instructionIndex = 3;

        // ---- 1) 构造 data ----
        // data布局如下：
        //   [ u32:instructionIndex (LE) ]
        //   [ Pubkey: basePublicKey (32 bytes) ]
        //   [ u32:seedLength (LE) ]
        //   [ seed的UTF-8字节 ]
        //   [ u64: lamports (LE) ]
        //   [ u64: space (LE) ]
        //   [ Pubkey: programId (32 bytes) ]

        byte[] seedBytes = seed.getBytes();
        ByteBuffer dataBuf = ByteBuffer.allocate(
                4 + 32 + 4 + 4 + seedBytes.length + 8 + 8 + 32
        );
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        // 写入指令索引
        dataBuf.putInt(instructionIndex);
        // 写入 basePublicKey
        dataBuf.put(toBuffer(basePublicKey.toByteArray()));
        // 写入 seed length + seed
        dataBuf.putInt(seedBytes.length);

        dataBuf.put(new byte[]{0,0,0,0});
        dataBuf.put(seedBytes);
        // 写入 lamports
        dataBuf.putLong(lamports);
        // 写入 space
        dataBuf.putLong(space);
        // 写入 programId
        dataBuf.put(toBuffer(programId.toByteArray()));

        byte[] instructionData = dataBuf.array();
        //System.out.println(Arrays.toString(instructionData));
        //System.out.println(HexUtil.encodeHex(instructionData));
        // ---- 2) 构造 AccountMeta 列表 ----
        // 0: [WRITE, SIGNER] = fromPublicKey (付钱的人)
        // 1: [WRITE]         = newAccountPubkey (要创建的新账户)
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(fromPublicKey, true, true));   // isSigner=true, isWritable=true
        keys.add(new AccountMeta(newAccountPubkey, false, true)); // not signer, but writable

        if (!basePublicKey.equals(fromPublicKey)) {
            keys.add(new AccountMeta(basePublicKey, true, false));
        }
        // ---- 3) 封装成 TransactionInstruction ----
        return new TransactionInstruction(
                PROGRAM_ID, // System Program ID
                keys,
                instructionData
        );
    }

    public static void main(String[] args) {
        var hex = HexUtil.decodeHex("00000000402419000000000050000000000000000000000000000000000000000000000000000000000000000000000000000000");
        //System.out.println(Arrays.toString(hex));

        System.out.println(Arrays.toString(hex));

        byte[] data = new byte[4 + 8 + 8 + 32]; // instruction(4) + lamports(8) + space(8) + programId(32)
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0); // CreateAccount instruction index
        buffer.putLong(1647680L);
        buffer.putLong(80); // space for nonce account
        buffer.put(SystemProgram.PROGRAM_ID.toByteArray());

        System.out.println(HexUtil.encodeHexStr(buffer.array()));
    }

    /**
     * Creates an instruction to assign a new owner to an account.
     *
     * @param owner The current owner of the account
     * @param newOwner The new owner to assign to the account
     * @return A TransactionInstruction object representing the assign instruction
     */
    public static TransactionInstruction assign(PublicKey owner, PublicKey newOwner) {
        List<AccountMeta> keys = Collections.singletonList(new AccountMeta(owner, true, true));

        byte[] data = new byte[UINT32_SIZE + PUBKEY_SIZE]; // 4 + 32 = 36 bytes
        uint32ToByteArrayLE(PROGRAM_INDEX_ASSIGN, data, 0);
        System.arraycopy(newOwner.toByteArray(), 0, data, UINT32_SIZE, PUBKEY_SIZE);

        return new TransactionInstruction(PROGRAM_ID, keys, data);
    }
    /**
     * 等价于TS中 `toBuffer()`:
     *  - 将 BigInteger 转为 byte[] (大端序),
     *  - 若长度不足32, 在前面补零,
     *  - 若长度超过32, 截取后32字节 (如果必须).
     */
    public static byte[] toBuffer(byte[] raw) {
        // 1. BigInteger默认 toByteArray() 返回 big-endian, 且可能包含符号位

        if (raw.length == 32) {
            // 长度正好32
            return raw;
        }

        if (raw.length > 32) {
            // 如果超过32字节, 一般是因为最高位符号或数值过大
            // 只截取最后32字节
            // (前面若有额外符号位 或超出 32字节范围)
            return Arrays.copyOfRange(raw, raw.length - 32, raw.length);
        } else {
            // 长度不足32, 在前面补零
            byte[] zeroPad = new byte[32];
            // 复制 raw 到 zeroPad 的末尾, 前面自动留零
            System.arraycopy(raw, 0, zeroPad, 32 - raw.length, raw.length);
            return zeroPad;
        }
    }

    /**
     * nonce 初始化
     * @param nonceAccount
     * @param nonceAuthority
     * @return
     */
    /**
     * 构造 initializeNonce 指令
     */
    public static TransactionInstruction initializeNonce(
            PublicKey noncePubkey,
            PublicKey authorizedPubkey
    ) {
        // --- Account Metas ---
        List<AccountMeta> keys = Arrays.asList(
                new AccountMeta(noncePubkey, true, true),
                new AccountMeta(SystemProgram.Block_Hashes_ID, false, false),
                new AccountMeta(SystemProgram.SYSVAR_RENT_PUBKEY, false, false)
        );

        // --- Instruction data (4 + 32 = 36 bytes) ---
        ByteBuffer buffer = ByteBuffer.allocate(UINT32_SIZE + PUBKEY_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(6);

        buffer.put(authorizedPubkey.toByteArray());

        return new TransactionInstruction(
                SystemProgram.PROGRAM_ID,
                keys,
                buffer.array()
        );
    }

    /**
     * Advances a nonce.
     *
     * @param nonce      the public key of the nonce account
     * @param authorized the public key of the authorized account
     * @return A {@code TransactionInstruction} of the created instruction
     */
    public static TransactionInstruction nonceAdvance(PublicKey nonce, PublicKey authorized){

        List<AccountMeta> keys = Arrays.asList(
                new AccountMeta(nonce, false, true),
                new AccountMeta(Block_Hashes_ID, false, false),
                new AccountMeta(authorized, true, false)
        );
        byte[] data = new byte[UINT32_SIZE]; // 4 bytes
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ADVANCE_NONCE_INSTRUCTION);

        return new TransactionInstruction(PROGRAM_ID, keys, data);
    }




}
