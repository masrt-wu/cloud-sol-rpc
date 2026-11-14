package org.p2p.solanaj.rpc.types;

import com.squareup.moshi.Json;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Memcmp {

    @Json(name = "offset")
    private long offset;

    @Json(name = "bytes")
    private String bytes;

    public Memcmp() {

    }

    public long getOffset() {
        return offset;
    }

    public String getBytes() {
        return bytes;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public void setBytes(String bytes) {
        this.bytes = bytes;
    }
}