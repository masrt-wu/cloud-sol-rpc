package org.p2p.solanaj.rpc.types;


import com.alibaba.fastjson2.JSONObject;
import com.squareup.moshi.Json;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.p2p.solanaj.rpc.types.nonce.NonceData;

import java.util.AbstractMap;
import java.util.List;

@Data
public class SplNonceAccountInfo extends RpcResultObject{


    @Getter
    @ToString
    public static class Value {

        public Value(AbstractMap am) {
            this.data = (NonceData) am.get("data");
            this.executable = (boolean) am.get("executable");
            this.lamports = (double) am.get("lamports");
            this.owner = (String) am.get("owner");
            this.rentEpoch = (double) am.get("rentEpoch");
        }

        @Json(name = "data")
        private NonceData data;

        @Json(name = "executable")
        private boolean executable;

        @Json(name = "lamports")
        private double lamports;

        @Json(name = "owner")
        private String owner;

        @Json(name = "rentEpoch")
        private double rentEpoch;
    }

    @Json(name = "value")
    private Value value;

}
