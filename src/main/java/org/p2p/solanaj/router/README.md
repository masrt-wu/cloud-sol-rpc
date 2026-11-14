# 自部署 Router：Java 买入与卖出

此目录将 `sol-trade-router-sdk` 的**精确输入 Route CPI 指令格式**移植到 Java。
当前 `RouterTradeBuilder.ROUTER_PROGRAM` 指向
`CmNFUmRJL7YcnVn22oZzwG5Xg5WJqbcHEc6BK5mzDNR8`；若重新部署到新地址，
必须先同步修改该常量。`RouterTrader` 会读取链上配置，并要求 `fee_bps=0`。

支持构造的 CPI 跳如下：

| DEX | 构造方法 |
| --- | --- |
| Raydium | `RaydiumLegs.buyAmmV4/sellAmmV4`、`buyCpmm/sellCpmm`、`buyClmm/sellClmm`，也可按输入 mint 调用 `ammV4/cpmm/clmm` |
| Orca | `OrcaLegs.buy/sell`，也可调用 `swap`（Whirlpool `swap_v2`） |
| Meteora | `MeteoraLegs.buyDammV2/sellDammV2`、`buyDlmm/sellDlmm`，也可调用 `dammV2/dlmm` |
| Pump AMM | `PumpAmmLegs.buy`, `.sell`（PumpSwap，不是未毕业 bonding curve） |
| Pump.fun 内盘 V2 | `PumpFunLegs.buy/sell`（仅非 WSOL 的 SPL quote，分别编码 `buy_exact_quote_in_v2`、`sell_v2`） |
| Raydium LaunchLab | `LaunchLabLegs.buy/sell`，也可调用 `buyExactIn/sellExactIn` |

买卖命名约定为池记录的 quote/B/Y/token1/pc → base/A/X/token0/coin 是“买入”，反向为“卖出”。
真实交易应以 `inputMint` 和 `outputMint` 检查方向，不能只凭池名推断方向。

调用方需提供**当前且已解码的池账户快照**，包括池地址、mint、vault、Token Program，
以及相关的 tick/bin 数组、费用账户等。Java 代码不会自动发现池子、报价、解码池账户或选择路由。
集中流动性池的数组会随价格变化，交易前应使用最新数据。
提交前，客户端会通过 RPC 核对 mint owner、池 owner 和 Router 配置。

单跳示例（所有池字段都应替换为核实过的真实数据）：

```java
RpcApi rpc = new RpcApi(new RpcClient(rpcUrl));
Account signer = org.p2p.solanaj.utils.Env.account(); // 从 .env 或进程环境加载，不输出私钥。
PublicKey quote = new PublicKey(quoteMintAddress);
PublicKey token = new PublicKey(tokenMintAddress);
RaydiumLegs.AmmV4 pool = new RaydiumLegs.AmmV4(
    amm, coinMint, pcMint, coinVault, pcVault, TokenProgram.PROGRAM_ID);
long inputRaw = Long.parseLong(org.p2p.solanaj.utils.Env.require("AMOUNT_IN_RAW"));
long minOutputRaw = Long.parseLong(org.p2p.solanaj.utils.Env.require("MIN_OUT_RAW"));
// MIN_OUT_RAW 应根据最新报价和允许的滑点计算。

RouterLeg buyLeg = RaydiumLegs.ammV4(pool, signer.getPublicKey(), quote,
    inputRaw, minOutputRaw);
RouterTradeBuilder.Swap buy = new RouterTradeBuilder.Swap(
    signer.getPublicKey(), quote, token, TokenProgram.PROGRAM_ID,
    TokenProgram.PROGRAM_ID, inputRaw, minOutputRaw, buyLeg, true);
RouterTrader.Result dryRun = new RouterTrader(rpc).buy(signer, buy, false);
// 核对模拟结果及参数后，传入 true 才会广播交易：
// RouterTrader.Result sent = new RouterTrader(rpc).buy(signer, buy, true);

RouterLeg sellLeg = RaydiumLegs.ammV4(pool, signer.getPublicKey(), token,
    inputRaw, minOutputRaw);
RouterTradeBuilder.Swap sell = new RouterTradeBuilder.Swap(
    signer.getPublicKey(), token, quote, TokenProgram.PROGRAM_ID,
    TokenProgram.PROGRAM_ID, inputRaw, minOutputRaw, sellLeg, true);
RouterTrader.Result sellDryRun = new RouterTrader(rpc).sell(signer, sell, false);
```

要使用 CPMM、CLMM、Whirlpool、DAMM v2 或 DLMM，请替换对应的池参数及 `Legs`
构造器，并确保 `Swap` 的输入/输出 mint 和最小单位金额与该跳一致。
PumpSwap 买入消耗 quote，卖出消耗 base。

## 固定金额多跳：Meteora DLMM quote 池 → Raydium LaunchLab 目标币池

调用方必须提供**两个池子的当前快照**，包括 DLMM bin 数组和 LaunchLab 所需账户。
第一跳连接 WSOL 与 `quoteMint`，第二跳使用同一个 `quoteMint` 购买目标 base mint。
例如历史交易中的 DLMM `lb_pair`
`3C6qVymTAwWNKCSspmd1qbUH9avaqhsjgW2yntvEYBXt` 与 LaunchLab `pool_state`
`HEqWyRYknxkpb8XXbq1LFhoDV6m75NjBQrGLSeCEMK8B`，中间 mint 为
`6GmAFSYs4gk3FDao5FzzySQpPZaWsa4rUJHacpMpUNgx`。
注意：DEX 程序 ID 不等于具体池地址。

```java
// 两个池记录必须使用已核实的数据，下面仅为参数占位。
MeteoraLegs.Dlmm quotePool = /* 已解码的 WSOL/quote 池 */;
LaunchLabLegs.Pool mintPool = /* 已解码的 quote/目标币池 */;
PublicKey user = signer.getPublicKey();
long wsolIn = /* 准备花费的 lamports */;
long quoteGuaranteed = /* 根据最新报价设置的第一跳最低输出 */;
long quoteForMint = /* 不超过 quoteGuaranteed 的第二跳固定输入 */;
long mintMinOut = /* 扣除 Token-2022 费用后可接受的最低到账量 */;

RouterTradeBuilder.RouteSwap route = RouterTradeBuilder.buyViaDlmmLaunchLab(
        user, quotePool, mintPool, wsolIn, quoteGuaranteed,
        quoteForMint, mintMinOut);

// WSOL ATA 已有余额时：RouterTradeBuilder.buy(route, feeRecipient)
// 使用原生 SOL 时：在同一笔交易中创建、充值并同步 WSOL ATA。
List<TransactionInstruction> ixs = RouterTradeBuilder.buyWithSol(route, feeRecipient);
// 确认最新链上状态和模拟结果之后，才考虑广播：
// new RouterTrader(rpc).buyWithSol(signer, route, true);
```

上述工厂会检查 WSOL 和中间 quote 的池子衔接关系，并推导 Token Program。
这是**旧版固定金额路由**：每跳的输入量都已写入指令，不能把第一跳的实际输出自动填进第二跳。
多出的 quote 留在中间 ATA；若第一跳产出不足以支付第二跳，整笔交易会原子失败。
原生 SOL 路径会保留 WSOL ATA，不会自动关闭可能已有余额的账户。
报价和滑点限额仍由调用方负责。

## 动态两跳：第二跳花掉第一跳本笔实际收到的 quote

`RouterTradeBuilder.DynamicRouteSwap` 使用标签 `3`。第二跳数据中的输入量 `1`
只是占位符；**已部署且支持标签 3 的链上 Router** 会在第一跳执行后读取中间
quote ATA 的余额增量，并把这个实际到账量写入第二跳 CPI 指令。
旧版标签 `2` 仍然保留，用于固定金额路由。不要把标签 `3` 发给尚未升级的链上程序。
第一跳可以是受支持的 Raydium、Orca、Meteora、PumpSwap 等精确输入池；
第二跳支持链上当前白名单中的 LaunchLab 买入，以及 Raydium AMM V4/CPMM/CLMM、
Meteora DLMM/DAMM v2、Orca Whirlpool 精确输入交换。
本次 Rust 新版还支持 PumpSwap `buy_exact_quote_in/sell`，以及 Pump 内盘 V2
`buy_exact_quote_in_v2/sell_v2` 作为动态后续跳；需要同步升级链上程序。
使用 `RouterTradeBuilder.buy(...)`、`sell(...)` 或 `buyWithSol(...)` 的动态两跳重载构造指令，
或通过 `RouterTrader` 的对应方法先模拟再选择是否广播。

`RouterTrader.buyWithSol` 使用传统交易格式，广播前会拒绝超过 1232 字节的交易。
较大的两跳路由通常需要**标准 Solana v0 交易和地址查找表（ALT）**。
调用方应传入已经在链上创建并生效的 ALT 快照：

```java
List<RouterV0Transaction.LookupTable> tables = List.of(
        new RouterV0Transaction.LookupTable(tableAddress, tableAddressesInOnChainOrder));
RouterTrader.Result dryRun = new RouterTrader(rpc)
        .buyWithSolV0(signer, route, tables, false);
// true 表示模拟通过后广播；返回的签名还需要继续查询确认状态。
```

v0 编译器将非签名 DEX 账户放入传入的 ALT，付款人和程序 ID 保留在静态账户表中。
ALT 地址及顺序必须与已生效的链上内容一致。本目录不会创建、填充或读取 ALT。
若 v0 交易仍超过 1232 字节，代码会在本地拒绝；可将更多路由账户纳入 ALT。

## 三跳：上一跳实际到账量全部用于下一跳

`RouterTradeBuilder.ThreeHopRouteSwap` 的 `spendActualOutput=true` 使用标签 `4`：
第一跳按给定金额执行，第二、三跳在 Java 对象中用输入量 `1` 构造；最终序列化时
这两处输入量会写成 `0`。**链上新版 Router** 分别用第一、二跳新到账的净数量
覆盖这两个零值。两种中间币都须在本交易结束时回到交易前余额；少花会使整笔交易回滚。
`spendActualOutput=false` 保留标签 `2`，后两跳输入量由调用方固定填写。

```java
// 以下四个池对象均须由调用方用实时链上数据填充，不能仅传 mint 地址。
RaydiumLegs.Cpmm wsolUsdcPool = /* WSOL/USDC 池 */;
OrcaLegs.Pool usdcQuotePool = /* USDC/quote 池和当前 tick 数组 */;
LaunchLabLegs.Pool targetPool = /* quote/目标币 LaunchLab 池 */;
PublicKey user = signer.getPublicKey();

RouterLeg first = RaydiumLegs.cpmm(wsolUsdcPool, user, RouterTradeBuilder.WSOL_MINT,
        wsolInRaw, usdcMinOutRaw);
RouterLeg middle = OrcaLegs.swap(usdcQuotePool, user, usdcMint,
        1, quoteMinOutRaw);
RouterLeg last = LaunchLabLegs.buy(targetPool, user, 1, targetMinOutRaw);
RouterTradeBuilder.ThreeHopRouteSwap buy = new RouterTradeBuilder.ThreeHopRouteSwap(
        user, first, middle, last,
        TokenProgram.PROGRAM_ID, usdcTokenProgram, quoteTokenProgram,
        targetTokenProgram, wsolInRaw, usdcMinOutRaw, quoteMinOutRaw,
        targetMinOutRaw, true, true, true);

// false 表示只模拟；true 表示模拟通过后广播。大量账户时可改用 buyWithSolV0。
RouterTrader.Result dryRun = new RouterTrader(rpc).buyWithSol(signer, buy, false);

// 卖出按相反顺序构造：LaunchLab 卖目标币 → quote/USDC 池 → USDC/WSOL 池。
RouterLeg sellFirst = LaunchLabLegs.sell(targetPool, user, targetInRaw, quoteMinOutRaw);
RouterLeg sellMiddle = OrcaLegs.swap(usdcQuotePool, user, quoteMint,
        1, usdcMinOutRaw);
RouterLeg sellLast = RaydiumLegs.cpmm(wsolUsdcPool, user, usdcMint,
        1, wsolMinOutRaw);
RouterTradeBuilder.ThreeHopRouteSwap sell = new RouterTradeBuilder.ThreeHopRouteSwap(
        user, sellFirst, sellMiddle, sellLast,
        targetTokenProgram, quoteTokenProgram, usdcTokenProgram,
        TokenProgram.PROGRAM_ID, targetInRaw, quoteMinOutRaw,
        usdcMinOutRaw, wsolMinOutRaw, true, true, true);
RouterTrader.Result sellDryRun = new RouterTrader(rpc).sell(signer, sell, false);
```

标签 `4` 只有部署了相同 Rust 新版本的链上程序才支持。Java 不自动发现池、计算报价、
设置优先费或解包 WSOL；调用方须准备正确的 Token Program、tick/bin 数组、
最新的最低输出以及充足的计算预算。`RouterTrader` 会读取 RPC 并模拟；
单独调用 `RouterTradeBuilder` 则只在本地构造，不提交交易。

`broadcast=false` 只模拟、不发送；`broadcast=true` 先模拟再发送。
返回交易签名**不等于确认成功**，模拟成功也不保证最终上链；广播后还需查询签名状态。
私钥从项目工作目录的 `.env` 或进程环境加载，配置说明见根目录 `.env.example`。
测试示例仍包含历史 RPC 地址、池地址和交易金额，使用前请检查。

当前限制：不自动发现池子或报价，不自动配置 ALT，不自动关闭/解包 WSOL，
不支持精确输出交换、Pump 内盘原生 SOL 配对的动态 ATA 路由、Meteora DAMM v1/DBC，
不自动补充 Token-2022 transfer-hook 额外账户或 tick/bin 扩展数组，
也不自动设置优先费。发送前须按目标池的要求逐项核对。

## 本次同步：Pump 内外盘动态两跳、三跳

外盘继续使用 `PumpAmmLegs.Pool` 和 `PumpAmmLegs.buy/sell`，接口未改变。
买入现在也能作为动态第二、第三跳；交易量标志为 `1`，数据为 25 字节。
协议手续费 ATA 改为根据 `protocolFeeRecipient + quoteMint + quoteTokenProgram`
本地推导，不再依赖 WSOL 固定映射，因此也适用于其他 SPL quote。

内盘使用新的 `PumpFunLegs.Pool`，必须提供真实的 base/quote Token Program、
曲线地址、曲线 base 代币账户、实际 creatorVault、协议及回购费用接收方。
`creatorVault` 可能是费用共享金库，不能简单从 creator 公钥猜测。
quote 为经典 Token 或 Token-2022 均可显式传入；不要因为 mint 不认识就默认经典 Token。
账户按 Rust V2 顺序生成：买入 27 个、卖出 26 个，用户签名位于索引 13，
用户 base/quote ATA 位于索引 14/15，所有金额均为最小单位。

```java
// innerPool 由调用方填入已解析且核实的非 WSOL quote 内盘参数。
// 外盘只需将 innerPool 和 PumpFunLegs 换成对应的 PumpAmmLegs 类型。
RouterLeg targetBuy = PumpFunLegs.buy(innerPool, user, 1, mintMinOutRaw);
RouterTradeBuilder.DynamicRouteSwap buyTwo = new RouterTradeBuilder.DynamicRouteSwap(
        user, wsolToQuoteLeg, targetBuy,
        TokenProgram.PROGRAM_ID, innerPool.quoteTokenProgram(), innerPool.baseTokenProgram(),
        wsolInRaw, quoteMinOutRaw, mintMinOutRaw, true, true);
// 只生成指令，不签名、不提交；第一跳须真实消耗经典 WSOL。
List<TransactionInstruction> buyInstructions = RouterTradeBuilder.buyWithSol(buyTwo, feeRecipient);

// 卖出时第一跳支付真实的 mint 数量，后续兑换使用金额 1 占位。
RouterLeg targetSell = PumpFunLegs.sell(innerPool, user, mintInRaw, quoteMinOutRaw);
RouterTradeBuilder.DynamicRouteSwap sellTwo = new RouterTradeBuilder.DynamicRouteSwap(
        user, targetSell, quoteToWsolLeg,
        innerPool.baseTokenProgram(), innerPool.quoteTokenProgram(), TokenProgram.PROGRAM_ID,
        mintInRaw, quoteMinOutRaw, wsolMinOutRaw, true, true);
List<TransactionInstruction> sellInstructions = RouterTradeBuilder.sell(sellTwo, feeRecipient);

// 三跳买入：WSOL → USDC → quote → 内盘 mint，第二、三跳均先用金额 1 构造。
RouterTradeBuilder.ThreeHopRouteSwap buyThree = new RouterTradeBuilder.ThreeHopRouteSwap(
        user, wsolToUsdcLeg, usdcToQuoteLeg, targetBuy,
        TokenProgram.PROGRAM_ID, usdcTokenProgram,
        innerPool.quoteTokenProgram(), innerPool.baseTokenProgram(),
        wsolInRaw, usdcMinOutRaw, quoteMinOutRaw, mintMinOutRaw, true, true, true);

// 三跳卖出：内盘 mint → quote → USDC → WSOL。
RouterTradeBuilder.ThreeHopRouteSwap sellThree = new RouterTradeBuilder.ThreeHopRouteSwap(
        user, targetSell, quoteToUsdcLeg, usdcToWsolLeg,
        innerPool.baseTokenProgram(), innerPool.quoteTokenProgram(),
        usdcTokenProgram, TokenProgram.PROGRAM_ID,
        mintInRaw, quoteMinOutRaw, usdcMinOutRaw, wsolMinOutRaw, true, true, true);
```

动态两跳仍使用标签 `3`，三跳使用 `spendActualOutput=true` 和标签 `4`。
Java 后续跳输入仍填 `1`：两跳由链上替换占位量，三跳在编码时把后两跳输入改为 `0`。
每次替换的是上一跳本笔实际**净新增量**，不是中间 ATA 的全部历史余额。
最低输出不被改写；旧版单跳和固定金额标签 `2` 保留。

内盘的 WSOL 标识 quote 实际走原生 SOL 结算，并不是从 WSOL ATA 扣款。
因此本次内盘构造器明确拒绝 WSOL quote，不能套用上述动态 ATA 路由；
这不影响 PumpSwap 外盘的 WSOL quote，也不改变仓库其他原生 SOL 内盘交易代码。
单独构造池账户不会自动创建协议、回购、曲线或共享金库的代币账户，
调用方需保证这些账户符合目标池当前要求。

离线回归用例在 `RouterPumpDynamicSyncTest`、`RouterThreeHopSyncTest`，不读取私钥或访问 RPC。
上线前仍需使用真实池最新状态模拟；离线编码通过不代表链上交易必然成功。
