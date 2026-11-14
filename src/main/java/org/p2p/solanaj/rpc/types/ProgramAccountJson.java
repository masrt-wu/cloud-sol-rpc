package org.p2p.solanaj.rpc.types;

import com.alibaba.fastjson2.JSONObject;
import com.squareup.moshi.Json;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig.Encoding;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Getter
@ToString
public class ProgramAccountJson {

    @Getter
    @ToString
    public static class Account {
        @Json(name = "data")
        private AccountData data;

        @Json(name = "executable")
        private boolean executable;

        @Json(name = "lamports")
        private double lamports;

        @Json(name = "owner")
        private String owner;

        @Json(name = "rentEpoch")
        private double rentEpoch;

        private String encoding;

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Account(Object acc) {
            AbstractMap account = (AbstractMap) acc;

            String json = JSONObject.toJSONString(account.get("data"));
            this.data  = JSONObject.parseObject( json,AccountData.class);

            this.executable = (boolean) account.get("executable");
            this.lamports = (double) account.get("lamports");
            this.owner = (String) account.get("owner");
            this.rentEpoch = (double) account.get("rentEpoch");
        }

    }

    @Setter
    @Getter
    @ToString
    public static class AccountData{

        @Json(name = "program")
        private String program;
        @Json(name = "space")
        private Long space;
        @Json(name = "parsed")
        private Parsed parsed;

    }
    @Setter
    @Getter
    @ToString
    public static class  Parsed{
        @Json(name = "type")
        private String type;

        @Json(name = "info")
        private AccountInfo info;

    }
    @Setter
    @Getter
    @ToString
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
    @Setter
    @Getter
    @ToString
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



    @Json(name = "account")
    private Account account;

    @Json(name = "pubkey")
    private String pubkey;

    public PublicKey getPublicKey() {
        return new PublicKey(pubkey);
    }

    @SuppressWarnings({ "rawtypes" })
    public ProgramAccountJson(AbstractMap pa) {
        this.account = new Account(pa.get("account"));
        this.pubkey = (String) pa.get("pubkey");
    }
}
