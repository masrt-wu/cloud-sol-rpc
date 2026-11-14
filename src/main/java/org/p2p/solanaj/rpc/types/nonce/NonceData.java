package org.p2p.solanaj.rpc.types.nonce;


import com.squareup.moshi.Json;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.p2p.solanaj.rpc.types.RecentBlockhash;

@Data
public class NonceData {

    @Data
    public static class FeeCalculator {

        @Json(name = "lamportsPerSignature")
        private double lamportsPerSignature;
    }

    @Data
    public static class Value {
        @Json(name = "blockhash")
        private String blockhash;

        @Json(name = "authority")
        private String authority;

        @Json(name = "feeCalculator")
        private RecentBlockhash.FeeCalculator feeCalculator;
    }


    @Data
    public static class Parsed{

        private Value info;

        private String type;
    }

    private String program;


    private int space;

    private Parsed parsed;
}
