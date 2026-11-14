# 多路由快速 Pool 转换

先用 `DexParsers.parsePool(owner, bytes)` 得到 `DexAccountData`，再调用本目录对应类的 `fromDexAccountData(...)`，即可生成现有 `*Legs.Pool` / `Cpmm` / `Clmm` / `Dlmm`。转换步骤只读取已解析字段，不重复读取账户字节、访问 RPC、检查 owner 或账户判别符。`DexParsers` 本身仍会校验 owner/判别符，其 JSON 布局在每种协议首次使用时加载一次，不是每次账户更新都加载。`parse(poolAddress, byte[], ...)` 固定偏移直读入口仍保留，适合上游已经确认账户类型且要求跳过通用解析的场景。

| 程序池型 | 快速转换类 | 返回的路由对象 | Pool 之外还要提供 |
| --- | --- | --- | --- |
| PumpSwap AMM | `PumpAmmPoolParser` | `PumpAmmLegs.Pool` | 两侧 Token 程序、协议费接收方、创建者金库账户、回购费接收方 |
| Raydium AMM V4 | `ray.RaydiumAmmV4PoolParser` | `RaydiumLegs.AmmV4` | 无；该旧池使用普通 SPL Token |
| Raydium CPMM | `ray.RaydiumCpmmPoolParser` | `RaydiumLegs.Cpmm` | 无；实际 pool 地址由调用方传入，兼容随机 signer 池 |
| Raydium CLMM | `ray.RaydiumClmmPoolParser` | `RaydiumLegs.Clmm` | 两侧 mint 的 Token 程序、可选 bitmap extension、所需 TickArray 列表 |
| Raydium LaunchLab | `ray.LaunchLabRouterPoolParser` | `LaunchLabLegs.Pool` | 平台/创建者费用 vault；原有 `LaunchLabLegs.Pool` 构造约束不变 |
| Meteora DLMM | `MeteoraDlmmPoolParser` | `MeteoraLegs.Dlmm` | 可选 bitmap extension、所需 BinArray 列表 |
| Meteora DAMM v2 | `MeteoraDammV2PoolParser` | `MeteoraLegs.DammV2` | 可选转介费账户、当前费用模式是否要求指令 sysvar |
| Orca Whirlpool | `OrcaWhirlpoolPoolParser` | `OrcaLegs.Pool` | 两侧 mint 的 Token 程序、按 swap 顺序提供的三个 TickArray |

例如，调用方取得 DLMM 池的最新账户快照后：

```java
DexAccountData state = DexParsers.parsePool(owner, poolBytes);
List<PublicKey> bins = MeteoraDlmmPoolParser.defaultBinArrays(
        lbPairAddress, state, true, 3); // true 为 X → Y
MeteoraLegs.Dlmm pool = MeteoraDlmmPoolParser.fromDexAccountData(
        lbPairAddress, state, bitmapExtension, bins);
RouterLeg leg = MeteoraLegs.dlmm(pool, user, inputMint, amountIn, minOut);
```

`defaultBinArrays` 只查池内默认位图，超出索引 -512～511 时不会假装知道扩展位图；交易量可能跨越更多数组，须由调用方用最新位图/数组状态补齐。CLMM 与 Orca 的 `tickCurrent(...)`、`tickSpacing(...)` 只暴露当前池位置，不计算完整跨 tick 路径。PDA 方法与本次 swap 必须携带哪些数组是两个不同问题。

请传真实池地址，不要把 DEX Program ID 当成具体 Pool 地址。Pump 手续费接收方、DAMM v2 费用模式和 tick/bin 数组都可能随链上状态变化，需要同步刷新。转换器只跳过重复验证，仍保留 `DexParsers` 与 `RouterLegs` 的必要约束；不会发起交易或部署合约。

仅运行离线测试：`mvn -o -Dtest=RouterFastPoolParsersTest test`。项目中其他测试可能连接 RPC 或签名，不属于本功能的验证范围。
