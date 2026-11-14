# Cloud Sol RPC

[English](../README.md) | 简体中文

Cloud Sol RPC 是基于 SolanaJ 包结构扩展的 Java 17 Solana 客户端库，提供 JSON-RPC 调用、WebSocket 订阅、交易构造与签名、DEX 账户解析，以及自定义链上交易 Router 的指令构造功能。

本项目打包产物为 JAR 库，不是独立运行的 RPC 服务端。当前 Maven 坐标为 `com.duonian:solanaj-jdk17:1.7.9`，Java 包名保留为 `org.p2p.solanaj`。

## 主要功能

- **RPC 调用：** 查询余额、账户、代币、区块、交易和签名状态，模拟与发送交易；支持自定义节点、超时、OkHttp 客户端、SOCKS 代理和按权重选择节点。
- **WebSocket 订阅：** 通过 `SubscriptionWebSocketClient` 订阅账户、签名、日志、程序、slot 和区块通知。
- **账户与交易：** 公钥、密钥对、助记词派生、交易签名与序列化，以及程序派生地址（PDA）计算。
- **程序指令：** System、SPL Token、关联代币账户（ATA）、计算预算、Memo、地址查找表，以及协议专用交易工具。
- **DEX 解析：** PumpSwap；Raydium AMM V4、CPMM、CLMM、LaunchLab；Meteora DLMM、DAMM v1、DAMM v2；Orca Whirlpool。另有 Token / Token-2022 账户解析、PDA 工具和由调用方推送快照的本地缓存。
- **交易路由：** 单跳交换、固定金额或动态两跳、三跳，以及使用地址查找表（ALT）的 Solana v0 交易。
- **事件解析：** 分别提供 Pump.fun 内盘日志和 Pump AMM 成交事件解析工具。

账户解析与 Router 的支持范围不同。例如 DAMM v1 有解析器，但没有路由跳构造器；Router 另有面向非 WSOL SPL quote 的 Pump.fun V2 路由跳，这不代表统一解析入口支持 Pump.fun 内盘池。

## 环境要求与安装

使用 **JDK 17** 和本地安装的 **Maven**。仓库未提供 Maven Wrapper。请确认 `JAVA_HOME` 和 Maven 实际使用的都是 JDK 17：

```shell
java -version
mvn -version
```

在项目根目录构建，并安装到本地 Maven 仓库：

```shell
mvn -Dmaven.test.skip=true install
```

该命令跳过测试编译和执行。测试目录包含实网 RPC 和交易示例，运行前应检查具体测试类。普通本地构建不需要配置 GPG；签名配置位于 `deploy` profile 中。

本地安装后，在使用本库的项目 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.duonian</groupId>
    <artifactId>solanaj-jdk17</artifactId>
    <version>1.7.9</version>
</dependency>
```

以上坐标以当前仓库的 [pom.xml](../pom.xml) 为准。版本变更后应同步调整依赖；此处采用本地安装方式，不假定该版本已发布到公共 Maven 仓库。

## 本地配置与私钥

通过 `org.p2p.solanaj.utils.Env` 从应用工作目录的 `.env` 加载私钥。新克隆的项目可在 PowerShell 中创建本地配置文件：

```powershell
Copy-Item .env.example .env
```

如果 `.env` 已存在，请直接编辑并保留原有内容。配置项见 [.env.example](../.env.example)。钱包格式二选一：`SOLANA_PRIVATE_KEY_BASE58` 填 Base58 编码的 64 字节密钥对，或 `SOLANA_SECRET_KEY_JSON` 填包含 64 个无符号字节的单行 JSON 数组，另一项留空。`Env.account()` 负责加载钱包。历史示例中不同的钱包分别使用 `SOLANA_PUMP_V3_PRIVATE_KEY_BASE58`、`SOLANA_PUMP_ALTERNATE_PRIVATE_KEY_BASE58` 和 `SOLANA_ATA_PRIVATE_KEY_BASE58`，保留各自独立的配置。

其他配置（如 RPC 地址、交易金额）通过 `Env.require("NAME")` 读取。进程环境变量优先于 `.env` 中同名项，空值也会覆盖。配置文件每个进程只读取一次，修改后需要重启。支持空行、注释、可选的 `export` 前缀及单/双引号值，不支持多行值或变量插值。缺失配置时会明确报错，不打印配置内容，也不会回退到其他签名钱包。

历史 Pump 示例中的带凭据节点分别通过 `.env` 的 `HELIUS_RPC_URL`、`JITO_AUTH_RPC_URL` 加载，助记词口令回归测试读取 `TEST_MNEMONIC_PASSPHRASE`。API Key、鉴权 UUID 和口令也应与钱包私钥一样放在配置中，不要写入源码。

Git 已忽略 `.env` 和 `.env.*`，只有不含真实凭据的 `.env.example` 模板用于版本管理。请将 `.env` 放在应用工作目录，不要放入 `src/main/resources`，以免打包进 SDK JAR。历史回归测试的凭据使用独立的 `TEST_*` 配置，绝不回退到交易钱包；未配置对应凭据时，相关测试会跳过。

## 快速开始

下面的 Java 示例使用上述配置加载器。RPC 调用可能抛出 `RpcException`；为便于阅读，完整示例统一声明 `throws Exception`。

### 查询余额

将 `SOLANA_RPC_URL` 设置为 RPC 节点地址，将 `SOLANA_ACCOUNT` 设置为 Base58 公钥。此示例不需要私钥。

```java
import org.p2p.solanaj.utils.Env;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;

public class BalanceExample {
    public static void main(String[] args) throws Exception {
        RpcClient client = new RpcClient(Env.require("SOLANA_RPC_URL"));
        PublicKey address = new PublicKey(Env.require("SOLANA_ACCOUNT"));
        long lamports = client.getApi().getBalance(address);
        System.out.println("Balance (lamports): " + lamports);
    }
}
```

SOL 余额单位是 lamports：`1 SOL = 1_000_000_000 lamports`。代币金额使用对应 mint 的最小单位，展示时再按其 decimals 换算。

### 构造并模拟 SOL 转账

此示例应使用 devnet 节点和有足够测试余额的 devnet 钱包。在 `.env` 中配置上述两种钱包格式之一，将 `SOLANA_RECIPIENT` 设置为接收方公钥，`AMOUNT_LAMPORTS` 设置为正整数。私钥不应写入源码、版本库或日志。

```java
import org.p2p.solanaj.utils.Env;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;

public class TransferExample {
    public static void main(String[] args) throws Exception {
        var rpc = new RpcClient(Env.require("SOLANA_RPC_URL")).getApi();
        Account signer = Env.account();
        PublicKey recipient = new PublicKey(Env.require("SOLANA_RECIPIENT"));
        long lamports = Long.parseLong(Env.require("AMOUNT_LAMPORTS"));
        if (lamports <= 0) {
            throw new IllegalArgumentException("AMOUNT_LAMPORTS must be positive");
        }

        Transaction tx = new Transaction().addInstruction(
                SystemProgram.transfer(signer.getPublicKey(), recipient, lamports));
        String blockhash = rpc.getLatestBlockhash().getValue().getBlockhash();
        SimulatedTransaction simulation = rpc.simulateTransaction(tx, signer, blockhash);
        if (simulation.getValue() == null || simulation.getValue().getErr() != null) {
            throw new IllegalStateException("Simulation failed: " + simulation);
        }
        System.out.println(simulation.getValue().getLogs());

        // 确实需要广播转账时，再取消下一行的注释：
        // String signature = rpc.sendTransaction(tx, signer);
    }
}
```

模拟不会广播交易。获得交易签名不代表确认成功，发送后还需调用 `getSignatureStatuses(...)` 等接口检查结果；模拟成功也不保证最终上链或执行成功。

### 订阅账户更新

将 `SOLANA_WS_URL` 设置为节点服务商提供的完整 WebSocket 地址。通过 `URI` 直接构造客户端，可以保留地址中的路径、端口和查询参数。

```java
import org.p2p.solanaj.utils.Env;
import java.net.URI;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.p2p.solanaj.ws.SubscriptionWebSocketClient;

public class SubscriptionExample {
    public static void main(String[] args) throws Exception {
        var ws = new SubscriptionWebSocketClient(
                URI.create(Env.require("SOLANA_WS_URL")));
        try {
            if (!ws.connectBlocking()) {
                throw new IllegalStateException("WebSocket connection failed");
            }
            ws.accountSubscribe(Env.require("SOLANA_ACCOUNT"),
                    data -> System.out.println(data), Commitment.CONFIRMED, "base64");
            System.in.read(); // 等待输入，保持示例进程运行。
        } finally {
            ws.closeBlocking();
        }
    }
}
```

### 解析 DEX 池账户

调用方通过 RPC 获取账户，`DexParsers` 在本地解析原始字节，并根据账户真实的 owner 程序分派协议。

```java
import org.p2p.solanaj.utils.Env;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.programs.parse.DexParsers;
import org.p2p.solanaj.rpc.RpcClient;

public class PoolExample {
    public static void main(String[] args) throws Exception {
        var rpc = new RpcClient(Env.require("SOLANA_RPC_URL")).getApi();
        var account = rpc.getAccountInfo(new PublicKey(Env.require("SOLANA_POOL")));
        if (account == null || account.getValue() == null) {
            throw new IllegalArgumentException("Pool account not found");
        }
        var pool = DexParsers.parsePool(
                account.getValue().getOwner(), account.getDecodedData());
        System.out.println(pool.staticFields());
        System.out.println(pool.dynamicFields());
    }
}
```

该入口只接受池账户，不能用于 token vault 或 tick/bin 数组，其他账户应使用对应解析器。整数字段使用 `BigInteger` 保留完整精度；解析储备不等于计算完整交换报价。

## 使用交易 Router

`RouterTradeBuilder` 在本地生成指令；`RouterTrader` 检查链上配置和相关账户 owner、模拟交易，并按参数选择是否广播。动态两跳执行使用 `buyDynamic*` / `sellDynamic*` 方法，三跳执行使用对应的 `buy*` / `sell*` 重载。

调用方必须提供最新池快照、Token Program、费用账户和所需 tick/bin 数组。本库不自动发现池子、计算完整报价、选择路由，也不自动创建或读取 ALT。最低输出应根据最新报价和允许的滑点设置。

- `RouterTradeBuilder.ROUTER_PROGRAM` 必须与实际部署的程序一致；`RouterTrader` 当前要求链上 `fee_bps` 为零。
- 动态两跳和三跳分别要求链上 Router 支持对应格式（标签 `3`、`4`）；Java 库不会自动部署或升级链上程序。
- `broadcast=false` 仅模拟；`broadcast=true` 先模拟再发送，广播后仍需跟踪签名状态。
- 账户较多的路由可能需要 `RouterV0Transaction` 和已生效的 ALT 快照，才能满足 1232 字节交易限制。
- 原生 SOL 包装工具会保留 WSOL ATA；Router 不自动补齐 Token-2022 transfer-hook 账户、全部 tick/bin 扩展账户或优先费。

池参数、多跳示例和协议限制请阅读 [Router 使用指南](../src/main/java/org/p2p/solanaj/router/README.md)。

## 买入与卖出示例

下面前三种示例通过前文所述的自定义 Router 执行。将公共的 `TradeSupport.java` 与示例类放在一起；这些示例共用该辅助类。传入已配置的 `RpcApi`、通过 `Env.account()` 加载的签名账户和已核实的池快照。设置 `buy=true` 买入，`buy=false` 卖出，每次只执行选择的方向。

金额均为对应 mint 最小单位的正整数。以下 Router 示例采用精确输入：买入花费 `amountInRaw` 个 quote，至少得到 `minOutRaw` 个 base；卖出花费 `amountInRaw` 个 base，至少得到 `minOutRaw` 个 quote。quote 以池中实际记录为准，不一定是 SOL。

公共辅助类按需创建输出 ATA，但输入 ATA 必须已存在且余额充足。输入为 WSOL 时，请提前包装 SOL 并调用 sync；单跳 `Swap` 不会自动充值 WSOL。钱包还需保留支付交易费和 ATA 租金的原生 SOL。这些方法默认仅模拟；确实需要提交时，再将辅助类中的 `broadcast` 改为 `true`。

### 公共 Router 模拟辅助类

```java
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.router.RouterLeg;
import org.p2p.solanaj.router.RouterTradeBuilder;
import org.p2p.solanaj.router.RouterTrader;
import org.p2p.solanaj.rpc.RpcApi;

public class TradeSupport {
    public static RouterTrader.Result simulate(
            RpcApi rpc, Account signer, RouterLeg leg,
            PublicKey inputProgram, PublicKey outputProgram, boolean buy)
            throws Exception {
        var swap = new RouterTradeBuilder.Swap(
                signer.getPublicKey(), leg.inputMint(), leg.outputMint(),
                inputProgram, outputProgram, leg.amountInRaw(), leg.minOutRaw(),
                leg, true);
        boolean broadcast = false;
        var trader = new RouterTrader(rpc);
        return buy ? trader.buy(signer, swap, broadcast)
                   : trader.sell(signer, swap, broadcast);
    }
}
```

### Pump AMM / PumpSwap 外盘：买入或卖出

买入调用 `PumpAmmExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)`；卖出调用 `PumpAmmExample.trade(rpc, signer, pool, false, baseInRaw, minQuoteOutRaw)`。

`PumpAmmLegs.Pool` 需要填入真实池地址、base/quote mint 与 vault、两侧 Token Program、协议费接收方、创建者金库 ATA 及权限账户、回购接收方，以及适用的 creator/cashback 字段。费用账户应根据最新配置刷新，不能只传入一个 mint 地址。已解析数据的转换方式见 [池快照转换指南](../src/main/java/org/p2p/solanaj/router/pda/README.md)。

```java
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.router.PumpAmmLegs;
import org.p2p.solanaj.router.RouterTrader;
import org.p2p.solanaj.rpc.RpcApi;

public class PumpAmmExample {
    public static RouterTrader.Result trade(
            RpcApi rpc, Account signer, PumpAmmLegs.Pool pool,
            boolean buy, long amountInRaw, long minOutRaw) throws Exception {
        var user = signer.getPublicKey();
        var leg = buy ? PumpAmmLegs.buy(pool, user, amountInRaw, minOutRaw)
                      : PumpAmmLegs.sell(pool, user, amountInRaw, minOutRaw);
        return TradeSupport.simulate(rpc, signer, leg,
                buy ? pool.quoteTokenProgram() : pool.baseTokenProgram(),
                buy ? pool.baseTokenProgram() : pool.quoteTokenProgram(), buy);
    }
}
```

### Raydium 池：买入或卖出

买入调用 `RaydiumExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)`；卖出在同一重载中传入 `false, baseInRaw, minQuoteOutRaw`。池对象类型决定使用 AMM V4、CPMM 还是 CLMM。

AMM V4 的 quote/base 对应 pc/coin；CLMM 对应 token1/token0。请先检查实际 mint，再判断该方向是否为买入目标币。如果需要从另一侧输入，可调用 `RaydiumLegs.ammV4/cpmm/clmm(..., inputMint, ...)`，并使用匹配的 Token Program 封装返回的路由跳。

AMM V4 需要 AMM 地址、coin/pc mint 与 vault，以及经典 Token Program。CPMM 还需要 config、observation 和两侧 Token Program。CLMM 需要 config、observation、两侧 Token Program、最新且按方向排列的 tick 数组，以及适用时的 bitmap extension。可使用 `router.pda.ray` 下对应转换器，将已核实的账户数据转换为这些池对象。

```java
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.router.RaydiumLegs;
import org.p2p.solanaj.router.RouterTrader;
import org.p2p.solanaj.rpc.RpcApi;

public class RaydiumExample {
    public static RouterTrader.Result trade(
            RpcApi rpc, Account signer, RaydiumLegs.AmmV4 pool,
            boolean buy, long amountInRaw, long minOutRaw) throws Exception {
        var user = signer.getPublicKey();
        var leg = buy ? RaydiumLegs.buyAmmV4(pool, user, amountInRaw, minOutRaw)
                      : RaydiumLegs.sellAmmV4(pool, user, amountInRaw, minOutRaw);
        return TradeSupport.simulate(rpc, signer, leg,
                pool.tokenProgram(), pool.tokenProgram(), buy);
    }

    public static RouterTrader.Result trade(
            RpcApi rpc, Account signer, RaydiumLegs.Cpmm pool,
            boolean buy, long amountInRaw, long minOutRaw) throws Exception {
        var user = signer.getPublicKey();
        var leg = buy ? RaydiumLegs.buyCpmm(pool, user, amountInRaw, minOutRaw)
                      : RaydiumLegs.sellCpmm(pool, user, amountInRaw, minOutRaw);
        return TradeSupport.simulate(rpc, signer, leg,
                buy ? pool.quoteTokenProgram() : pool.baseTokenProgram(),
                buy ? pool.baseTokenProgram() : pool.quoteTokenProgram(), buy);
    }

    public static RouterTrader.Result trade(
            RpcApi rpc, Account signer, RaydiumLegs.Clmm pool,
            boolean buy, long amountInRaw, long minOutRaw) throws Exception {
        var user = signer.getPublicKey();
        var leg = buy ? RaydiumLegs.buyClmm(pool, user, amountInRaw, minOutRaw)
                      : RaydiumLegs.sellClmm(pool, user, amountInRaw, minOutRaw);
        return TradeSupport.simulate(rpc, signer, leg,
                buy ? pool.token1Program() : pool.token0Program(),
                buy ? pool.token0Program() : pool.token1Program(), buy);
    }
}
```

### Pump.fun 内盘：使用 SPL quote 买入或卖出

买入调用 `PumpFunExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)`；卖出在同一方法中传入 `false, baseInRaw, minQuoteOutRaw`。

该示例使用 `PumpFunLegs.Pool`，对应 V2 的 `buy_exact_quote_in_v2` / `sell_v2` 指令。需要提供 base/quote mint、各自真实 Token Program、bonding curve、曲线 base 代币账户、实际 creator vault 权限账户、协议费接收方和回购费接收方。creator vault 可能是共享金库，不能只凭创建者公钥猜测。

此 Router 路径只接受 **非 WSOL 的 SPL quote**。原生 SOL 内盘采用不同结算方式，因此该路径明确拒绝 WSOL mint；它也不是 PumpSwap 外盘。

```java
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.router.PumpFunLegs;
import org.p2p.solanaj.router.RouterTrader;
import org.p2p.solanaj.rpc.RpcApi;

public class PumpFunExample {
    public static RouterTrader.Result trade(
            RpcApi rpc, Account signer, PumpFunLegs.Pool pool,
            boolean buy, long amountInRaw, long minOutRaw) throws Exception {
        var user = signer.getPublicKey();
        var leg = buy ? PumpFunLegs.buy(pool, user, amountInRaw, minOutRaw)
                      : PumpFunLegs.sell(pool, user, amountInRaw, minOutRaw);
        return TradeSupport.simulate(rpc, signer, leg,
                buy ? pool.quoteTokenProgram() : pool.baseTokenProgram(),
                buy ? pool.baseTokenProgram() : pool.quoteTokenProgram(), buy);
    }
}
```

### Pump.fun 内盘：原生 SOL 的 V3 接口

原生 SOL 配对可参考仓库的 `PumpDIumProgram.makePumpTotalToken2022V3`，它直接构造 Pump 指令。下面的示例不使用 `TradeSupport` 或自定义 Router。请传入已完整填充、经过核实且与目标 V3 布局及签名者匹配的 `PumpToken2022PoolInfoV3`；该方法不会从链上获取或验证布局。

这里 `buy=true` 编码为类型 `0`（`buy_v3`）：`baseAmountRaw` 为希望买入的 base 数量，`quoteLimitLamports` 为最多支付的 SOL。`buy=false` 编码为类型 `1`（`sell_v3`）：这两个参数分别表示卖出的 base 数量和最低收到的 SOL。其买入金额语义与前面的精确输入 Router 示例不同。

此原生 SOL 路径的 `info.quoteMint` 使用 WSOL mint 标识，但实际结算使用原生 SOL。用户 base 代币账户及其他必需账户须提前准备，示例只添加交换指令。`refPumpToken2022V3(...)` 是本地便捷工厂，其中部分 ATA 推导固定使用 Token-2022/经典 Token，并从内置列表选取接收方；使用前应按真实 mint owner 和当前协议配置逐项核对生成的字段。

```java
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.PumpDIumProgram;
import org.p2p.solanaj.router.RouterTradeBuilder;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.utils.ray.PumpToken2022PoolInfoV3;

public class PumpFunSolExample {
    public static SimulatedTransaction simulate(
            RpcApi rpc, Account signer, PumpToken2022PoolInfoV3 info,
            boolean buy, long baseAmountRaw, long quoteLimitLamports)
            throws Exception {
        if (!signer.getPublicKeyBase58().equals(info.getUser())
                || !RouterTradeBuilder.WSOL_MINT.toBase58().equals(info.getQuoteMint())
                || baseAmountRaw <= 0 || quoteLimitLamports <= 0) {
            throw new IllegalArgumentException("Invalid user, quote mint, or amounts");
        }
        var instruction = PumpDIumProgram.makePumpTotalToken2022V3(
                info, buy ? 0 : 1, baseAmountRaw, quoteLimitLamports);
        var tx = new Transaction().addInstruction(instruction);
        String blockhash = rpc.getLatestBlockhash().getValue().getBlockhash();
        var result = rpc.simulateTransaction(tx, signer, blockhash);
        if (result.getValue() == null || result.getValue().getErr() != null) {
            throw new IllegalStateException("Simulation failed: " + result);
        }
        return result;
    }
}
```

买入模拟调用 `PumpFunSolExample.simulate(rpc, signer, info, true, baseToBuyRaw, maxSolLamports)`；卖出模拟传入 `false, baseToSellRaw, minSolLamports`。示例不广播交易。用于真实池之前，应确认当前指令布局和必需账户与本仓库实现一致。

### 现有 `TestPumpToken2022`：Pump.fun 内盘购买

仓库中的 [TestPumpToken2022.java](../src/test/java/org/p2p/solanaj/programs/TestPumpToken2022.java) 提供直接调用 **Pump.fun 内盘 bonding curve 的 Token-2022 购买示例**，与 PumpSwap AMM 外盘、自定义 Router 分开使用。它位于 `src/test/java`，属于测试/示例代码，不包含在 SDK JAR 中。

- `buyPumpAss()`：使用 `PumpApiIsComToken2022PoolInfo`、`refPumpToken2022(...)` 和 `makePumpTotalToken2022(info, 0, baseAmountRaw, maxSolLamports)`。签名账户通过 `Env.account()` 从配置加载。当前方法只模拟，发送代码已被注释。
- `buyPumpAssV3()`：使用 `PumpToken2022PoolInfoV3`、`refPumpToken2022V3(...)` 和 `makePumpTotalToken2022V3(info, 0, baseAmountRaw, maxSolLamports)`。签名私钥从 `.env` 的 `SOLANA_PUMP_V3_PRIVATE_KEY_BASE58` 读取。**当前方法模拟成功后会真实广播交易**，使用配置的持久 nonce 账户。

两个买入入口的类型参数均为 `0`：第一个金额是希望买入的 base 代币最小单位数量，第二个金额是最多支付的 SOL，单位为 lamports。流程会推导用户的 Token-2022 ATA，添加幂等创建 ATA 的指令，并在买入前添加计算预算和 nonce 推进指令。

使用前请将方法中的历史 RPC 选择、mint、nonce 账户、价格和金额替换为已核实的配置，旧版入口还需提供 creator 地址。请核对 mint owner、实际 decimals、当前曲线状态和指令布局；现有示例假设代币为六位小数，且使用固定价格，并非实时报价。nonce 账户必须已存在，签名钱包必须是其 nonce authority，并准备足够的 SOL 支付购买金额、交易费和 ATA 创建费用。

检查上述参数后，只运行旧版买入模拟方法：

```shell
mvn "-Dtest=TestPumpToken2022#buyPumpAss" test
```

下面的命令只选择 V3 买入方法，**可能真实花费 SOL 并推进 nonce**。执行前应检查其中已启用的 `sendTransaction(...)`；如果只需要模拟，请先注释发送代码块。

```shell
mvn "-Dtest=TestPumpToken2022#buyPumpAssV3" test
```

请选择单个方法，不要直接运行整个类；类中还包含卖出和关闭账户操作。这些网络示例不属于下文的离线验证范围。

## 项目结构与文档导航

```text
src/main/java/org/p2p/solanaj/
  core/                 公钥、消息、交易与签名
  rpc/                  JSON-RPC 客户端、接口、模型与配置
  ws/                   WebSocket 订阅与监听器
  programs/             链上程序指令构造
    parse/              DEX 布局、账户解析、PDA 与本地缓存
  router/               路由跳、构造器、执行与 v0 交易
    pda/                池快照转换与地址工具
  token/                代币管理
  utils/                编码、钱包派生与协议工具
src/main/resources/     随库打包的 DEX 布局快照
src/test/java/          单元测试与网络、交易示例
docs/                   中文说明、事件文档与许可证
```

- [英文项目说明](../README.md)
- [DEX 账户解析、PDA 与本地缓存](../src/main/java/org/p2p/solanaj/programs/parse/README.md)
- [交易 Router 使用指南](../src/main/java/org/p2p/solanaj/router/README.md)
- [池快照到路由对象的转换](../src/main/java/org/p2p/solanaj/router/pda/README.md)
- [Pump AMM 成交事件解析](PUMP_AMM_EVENTS.md)

## 开发与验证

仅编译主代码，不运行示例：

```shell
mvn -Dmaven.test.skip=true compile
```

选择指定的离线回归测试执行；在 PowerShell 中，用引号包裹包含逗号的参数：

```shell
mvn "-Dtest=DexParsersTest,RouterFastPoolParsersTest,PumpAmmLogParserTest,RouterPumpDynamicSyncTest,RouterThreeHopSyncTest" test
```

依赖和 Maven 插件已完整缓存时，可添加 `-o` 离线运行。不要未经检查直接执行整个测试目录：部分测试类包含历史节点、池地址和发送交易的示例，改用这些示例前应替换为自己的配置。配置加载器可通过 `mvn "-Dtest=EnvTest" test` 离线验证。

修改协议布局或指令构造时，应同步更新相关文档和离线回归用例。随库提供的布局对应当前仓库内的版本；后续链上协议变更需要重新核对并更新 SDK。

## 许可证

本项目采用 [MIT License](LICENSE)。分发代码时请保留已有版权与许可声明。
