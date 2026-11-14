package org.p2p.solanaj.core;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.utils.TweetNaclFast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TransactionV1 {

    public static final int SIGNATURE_LENGTH = 64;

    private static final int MAX_TRANSACTION_SIZE = 4096;

    private final MessageV1 message;

    public List<String> signatures;

    public List<byte[]> signaturesV1;

    private byte[] serializedMessage;

    public TransactionV1() {

        this.message = new MessageV1();

        this.signatures = new ArrayList<>();

        this.signaturesV1 = new ArrayList<>();
    }

    public TransactionV1 addInstruction(TransactionInstruction instruction) {

        Objects.requireNonNull(instruction, "Instruction cannot be null");

        message.addInstruction(instruction);

        return this;
    }

    public void setRecentBlockHash(String recentBlockhash) {

        Objects.requireNonNull(recentBlockhash, "Recent blockhash cannot be null");

        message.setRecentBlockHash(recentBlockhash);
    }

    public void setConfig(TransactionConfigV1 config) {

        message.setConfig(config);
    }

    public void sign(Account signer) {

        sign(List.of(Objects.requireNonNull(signer)));
    }

    public void sign(List<Account> signers) {

        if (signers == null || signers.isEmpty()) {

            throw new IllegalArgumentException("No signers provided");
        }

        signatures = new ArrayList<>();

        /*
         * signer[0] 仍然作为 fee payer
         */
        Account feePayer = signers.get(0);

        message.setFeePayer(feePayer);

        /*
         * 重要：
         *
         * V1 签名的是：
         *
         * VersionByte 开始
         * 一直到 signatures 之前
         *
         * 也就是整个 MessageV1。
         */
        serializedMessage = message.serialize();

        for (Account signer : signers) {

            try {

                TweetNaclFast.Signature provider = new TweetNaclFast.Signature(new byte[0], signer.getSecretKey());

                byte[] signature = provider.detached(serializedMessage);

                if (signature.length != SIGNATURE_LENGTH) {

                    throw new IllegalStateException("Invalid signature size");
                }

                signatures.add(Base58.encode(signature));

            } catch (Exception e) {

                throw new RuntimeException("Error signing V1 transaction", e);
            }
        }
    }

    public void signV1(List<Account> signers) {

        if (signers == null || signers.isEmpty()) {

            throw new IllegalArgumentException("No signers provided");
        }

        signatures = new ArrayList<>();

        /*
         * signer[0] 仍然作为 fee payer
         */
        Account feePayer = signers.get(0);

        message.setFeePayer(feePayer);

        /*
         * 重要：
         *
         * V1 签名的是：
         *
         * VersionByte 开始
         * 一直到 signatures 之前
         *
         * 也就是整个 MessageV1。
         */
        serializedMessage = message.serialize();

        for (Account signer : signers) {

            try {

                TweetNaclFast.Signature provider = new TweetNaclFast.Signature(new byte[0], signer.getSecretKey());

                byte[] signature = provider.detached(serializedMessage);

                if (signature.length != SIGNATURE_LENGTH) {

                    throw new IllegalStateException("Invalid signature size");
                }

                signaturesV1.add(signature);

            } catch (Exception e) {

                throw new RuntimeException("Error signing V1 transaction", e);
            }
        }
    }

    public byte[] serialize() {

        if (serializedMessage == null) {

            throw new IllegalStateException("Transaction must be signed first");
        }

        /*
         * V1:
         *
         * message
         * +
         * fixed-size signatures
         *
         * 不再有 ShortVec(signature count)
         */
        int totalSize = serializedMessage.length + signatures.size() * SIGNATURE_LENGTH;

        if (totalSize > MAX_TRANSACTION_SIZE) {

            throw new IllegalStateException("V1 transaction too large: " + totalSize + " bytes");
        }

        ByteBuffer out = ByteBuffer.allocate(totalSize);

        /*
         * Message 在前
         */
        out.put(serializedMessage);

        /*
         * Signatures 在最后
         */
        for (String signature : signatures) {

            byte[] raw = Base58.decode(signature);

            if (raw.length != SIGNATURE_LENGTH) {

                throw new IllegalStateException("Invalid signature length: " + raw.length);
            }

            out.put(raw);
        }

        return out.array();
    }



    public byte[] serializeV1() {

        if (serializedMessage == null) {

            throw new IllegalStateException("Transaction must be signed first");
        }

        /*
         * V1:
         *
         * message
         * +
         * fixed-size signatures
         *
         * 不再有 ShortVec(signature count)
         */
        int totalSize = serializedMessage.length + signaturesV1.size() * SIGNATURE_LENGTH;

        if (totalSize > MAX_TRANSACTION_SIZE) {

            throw new IllegalStateException("V1 transaction too large: " + totalSize + " bytes");
        }

        ByteBuffer out = ByteBuffer.allocate(totalSize);

        /*
         * Message 在前
         */
        out.put(serializedMessage);

        /*
         * Signatures 在最后
         */
        for (byte[] signature : signaturesV1) {

            if (signature.length != SIGNATURE_LENGTH) {

                throw new IllegalStateException("Invalid signature length: " + signature.length);
            }

            out.put(signature);
        }

        return out.array();
    }
}