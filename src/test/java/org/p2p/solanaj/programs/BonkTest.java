package org.p2p.solanaj.programs;

import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SplTokenAccountInfo;
import org.p2p.solanaj.utils.ray.LiquidityBockIsComPool;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class BonkTest {

    @Test
    public void bonk() throws RpcException, InterruptedException {
        //LiquidityBockIsComPool
        String url = Cluster.QUINET.getEndpoint();

        RpcApi rpcApi = new RpcApi(new RpcClient(url));

        //(virtual_quote + real_quote) / (virtual_base - real_base)
        while (true){
            AccountInfo accountInfo = rpcApi.getAccountInfo(new PublicKey("CZw5JaB3cesfz96LCaKTSZtNf9UiBAediURgtFAGrBjF"));
            LiquidityBockIsComPool pool = LiquidityBockIsComPool.decode(accountInfo.getValue().getData().get(0));
            SplTokenAccountInfo qTokenAcc = rpcApi.getSplTokenAccountInfo(new PublicKey(pool.getQuoteVault()));
            SplTokenAccountInfo bTokenAcc = rpcApi.getSplTokenAccountInfo(new PublicKey(pool.getBaseVault()));
            String qStr = qTokenAcc.getValue().getData().getParsed().getInfo().getTokenAmount().getAmount();
            BigDecimal q =  BigDecimal.valueOf(pool.getVirtualQuote())
                    .add(new BigDecimal(qStr));

            q = q.divide(BigDecimal.valueOf( Math.pow(10, pool.getQuoteDecimals())),15, RoundingMode.HALF_UP);
            BigDecimal bStr = new BigDecimal(bTokenAcc.getValue().getData().getParsed().getInfo().getTokenAmount().getAmount());
            BigDecimal sub = new BigDecimal(pool.getSupply()).subtract(bStr);

            BigDecimal b =  BigDecimal.valueOf(pool.getVirtualBase())
                    .subtract(sub);
            b = b.divide(BigDecimal.valueOf( Math.pow(10, pool.getBaseDecimals())),15, RoundingMode.HALF_UP);
            System.out.println("price----"+q.divide(b,13, RoundingMode.HALF_UP).toPlainString());


            BigDecimal qNew =  BigDecimal.valueOf(pool.getVirtualQuote())
                    //.add(BigDecimal.valueOf(pool.getQuoteProtocolFee()))
                    .add(BigDecimal.valueOf(pool.getRealQuote()));
            BigDecimal bNew =  BigDecimal.valueOf(pool.getVirtualBase())
                    .subtract(BigDecimal.valueOf(pool.getRealBase()));
            System.out.println("newPrice----"+qNew.divide(bNew,10, RoundingMode.HALF_UP).toPlainString());
            System.out.println(pool);
//            System.out.println(qStr);
//            System.out.println(sub);
            Thread.sleep(100L);
            break;
        }
    }
}
