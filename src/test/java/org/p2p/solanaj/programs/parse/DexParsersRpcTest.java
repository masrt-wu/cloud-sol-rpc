package org.p2p.solanaj.programs.parse;

import cn.hutool.core.codec.Base64;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.router.RaydiumLegs;
import org.p2p.solanaj.router.pda.MeteoraDammV2PoolParser;
import org.p2p.solanaj.router.pda.ray.RaydiumClmmPoolParser;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.AccountInfo;

import java.math.BigDecimal;

public class DexParsersRpcTest {


    @Test
    public void getRay() throws Exception{
        String url = Cluster.HELIUS.getEndpoint();
        url = "http://64.130.57.42:8899";
        BigDecimal buySol = new BigDecimal("0.001");
        RpcApi rpc = new RpcApi(new RpcClient(url));

        var lp = new PublicKey("9wvoS3q8JyUKdMVDNQvXktvC1PJSKzW1tmnyVtAsHn9o");
        AccountInfo accountInfo = rpc.getAccountInfo(lp);
        if(accountInfo==null)return;
        if(accountInfo.getValue()==null)return;
        DexAccountData parsed = DexParsers.parsePool(accountInfo.getValue().getOwner(), Base64.decode(accountInfo.getValue().getData().get(0)));
        //RaydiumLegs.Clmm clmm = RaydiumClmmPoolParser.fromDexAccountData(lp,parsed, TokenProgram.PROGRAM_ID,TokenProgram.PROGRAM_ID);
        //System.out.println(clmm);
        var item = MeteoraDammV2PoolParser.fromDexAccountData(lp,parsed,null);
        System.out.println(parsed);
        System.out.println(item);
    }
}
