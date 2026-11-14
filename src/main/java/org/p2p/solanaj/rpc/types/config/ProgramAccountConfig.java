package org.p2p.solanaj.rpc.types.config;

import java.util.List;

import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig.Encoding;

public class ProgramAccountConfig {

    private String  encoding = null;

    private List<Object> filters = null;

    private String commitment = "processed";

    public ProgramAccountConfig(List<Object> filters) {
        this.filters = filters;
    }

    public ProgramAccountConfig(String  encoding) {
        this.encoding = encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setFilters(List<Object> filters) {
        this.filters = filters;
    }
}