package org.p2p.solanaj.rpc.types;

import java.util.List;

import com.alibaba.fastjson2.JSONObject;
import com.squareup.moshi.Json;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Data
public class ConfirmedTransaction {

    @Data
    public static class Header {

        @Json(name = "numReadonlySignedAccounts")
        private long numReadonlySignedAccounts;

        @Json(name = "numReadonlyUnsignedAccounts")
        private long numReadonlyUnsignedAccounts;

        @Json(name = "numRequiredSignatures")
        private long numRequiredSignatures;
    }

    @Data
    public static class Instruction {

        @Json(name = "accounts")
        private List<Long> accounts;

        @Json(name = "data")
        private String data;

        @Json(name = "programIdIndex")
        private long programIdIndex;
    }

    @Data
    public static class Message {

        @Json(name = "accountKeys")
        private List<String> accountKeys;

        @Json(name = "header")
        private Header header;

        @Json(name = "instructions")
        private List<Instruction> instructions;

        @Json(name = "instructionsJson")
        private List<InnerInstructionV1> instructionsJson;


        @Json(name = "recentBlockhash")
        private String recentBlockhash;

        private Object addressTableLookups;
    }

    @Data
    public static class Status {

        @Json(name = "Ok")
        private Object ok;
    }

    @Data
    public static class TokenBalance {

        @Json(name = "accountIndex")
        private Integer accountIndex;

        @Json(name = "mint")
        private String mint;

        @Json(name = "owner")
        private String owner;

        @Json(name = "programId")
        private String programId;

        @Json(name = "uiTokenAmount")
        private TokenResultObjects.TokenAmountInfo uiTokenAmount;
    }

    @Data
    public static class innerInstructionsJson{
        private int index;

        private List<InnerInstructionV1> instructions;
    }

    @Data
    public static class  InnerInstructionV1{
        private int program_id_index;


        private String programId;
        private List<String> accounts;

        private String data;

        private int stack_height;
    }
    @Data
    public static class Meta {

        @Json(name = "err")
        private Object err;

        @Json(name = "fee")
        private long fee;

        @Json(name = "innerInstructions")
        private List<Object> innerInstructions;

        @Json(name = "innerInstructionsJson")
        private List<innerInstructionsJson> innerInstructionsJson;

        @Json(name = "preTokenBalances")
        private List<TokenBalance> preTokenBalances;

        @Json(name = "postTokenBalances")
        private List<TokenBalance> postTokenBalances;

        @Json(name = "postBalances")
        private List<Long> postBalances;

        @Json(name = "preBalances")
        private List<Long> preBalances;

        @Json(name = "status")
        private Status status;

        @Json(name = "accountKeys")
        private List<String >accountKeys;

        @Json(name = "logMessages")
        private List<String > logMessages;

        @Json(name = "loadedAddresses")
        private Object loadedAddresses;
    }

    @Data
    public static class Transaction {

        @Json(name = "message")
        private Message message;

        @Json(name = "signatures")
        private List<String> signatures;

        



    }

    @Json(name = "meta")
    private Meta meta;

    @Json(name = "slot")
    private long slot;
    @Json(name = "blockTime")
    private String blocktime;

    @Json(name = "transaction")
    private Transaction transaction;

    @Json(name = "index")
    private long index;
}
