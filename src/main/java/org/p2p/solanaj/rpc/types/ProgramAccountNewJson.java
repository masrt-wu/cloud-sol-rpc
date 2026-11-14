package org.p2p.solanaj.rpc.types;


import com.alibaba.fastjson2.JSONObject;
import com.squareup.moshi.Json;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.AbstractMap;

@Data
public class ProgramAccountNewJson {

    private Account account;

    private String pubkey;

    @Data
    public static class Account {
        private AccountData data;

        private boolean executable;

        private double lamports;

        private String owner;

        private double rentEpoch;

        private String encoding;
    }

    @Data
    public static class AccountData{

        @Json(name = "program")
        private String program;
        @Json(name = "space")
        private Long space;
        @Json(name = "parsed")
        private Parsed parsed;

    }
    @Data
    public static class  Parsed{
        @Json(name = "type")
        private String type;

        @Json(name = "info")
        private AccountInfo info;

    }

    @Data
    public static class  AccountInfo{
        @Json(name = "isNative")
        private Boolean isNative;

        @Json(name = "mint")
        private String mint;

        @Json(name = "owner")
        private String owner;

        @Json(name = "state")
        private String state;

        @Json(name = "tokenAmount")
        private AccountTokenAmountInfo tokenAmount;
    }
    @Data
    public static class  AccountTokenAmountInfo{
        @Json(name = "amount")
        private String amount;

        @Json(name = "decimals")
        private int decimals;

        @Json(name = "uiAmount")
        private BigDecimal uiAmount;

        @Json(name = "uiAmountString")
        private String uiAmountString;

    }




}
