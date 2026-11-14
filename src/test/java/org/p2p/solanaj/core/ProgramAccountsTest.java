package org.p2p.solanaj.core;


import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.Memcmp;
import org.p2p.solanaj.rpc.types.ProgramAccount;
import org.p2p.solanaj.rpc.types.ProgramAccountJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProgramAccountsTest {

    public static final Logger log = LoggerFactory.getLogger(ProgramAccountsTest.class);
    @Test
    public void getProgramAccountsTest() throws RpcException {
        String url = Cluster.HELIUS.getEndpoint();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        List<Memcmp> memcmpList = new ArrayList<>();
        Memcmp memcmp = new Memcmp();
        memcmp.setOffset(0);
        memcmp.setBytes("5miFyenAa2wZj6xmu1i6qdMD8Aa9F4Bv94BdojDbpump");
        memcmpList.add(memcmp);
        List<ProgramAccountJson> list = rpcApi.getProgramAccountsJson(new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),memcmpList,165);
        while (true){
            list = list.stream().filter(d-> d.getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmount().compareTo(BigDecimal.ZERO)>0).collect(Collectors.toList());
            list = list.stream().sorted(Comparator.comparing((ProgramAccountJson d) -> d.getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmount().doubleValue()).reversed()).collect(Collectors.toList());
            for (ProgramAccountJson item : list) {
                log.info("持有地址:{}",item.getAccount().getData().getParsed().getInfo().getOwner());
                log.info("持有数量:{}",item.getAccount().getData().getParsed().getInfo().getTokenAmount().getUiAmountString());
            }

            log.info("持有人:{}",list.size());
        }
    }
}
