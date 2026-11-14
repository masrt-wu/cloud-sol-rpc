package org.p2p.solanaj.rpc.types.config;

import com.squareup.moshi.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RpcSendTransactionConfig {

    public static enum Encoding {
        base64("base64"),
        base58("base58"),
        jsonParsed("jsonParsed");

        private String enc;

        Encoding(String enc) {
            this.enc = enc;
        }

        public String getEncoding() {
            return enc;
        }

    }

    @Json(name = "encoding")
    private Encoding encoding = Encoding.base64;

    @Json(name ="skipPreflight")
    private boolean skipPreFlight = false;

    @Json(name = "maxRetries")
    private long maxRetries = 0;

}
