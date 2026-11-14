# DEX 账户解析、PDA 与本地缓存

所有实现位于 `org.p2p.solanaj.programs.parse`。解析器只接收原始账户字节；PDA 方法只做本地计算；本地缓存只接收调用方推送的快照。它们不会自行请求 RPC、创建账户、签名或提交交易，也不需要私钥。

布局为 2026-09-29 核对的官方源文件快照，随 SDK 的资源目录一起发布。每种协议首次使用时编译一次布局，后续账户更新复用读取器，无需联网获取 IDL。此包没有修改旧版 Router、买卖流程或 Rust 合约。

## 覆盖范围

| 解析类 | 可解析的链上状态 | 本地地址计算 |
| --- | --- | --- |
| `PumpSwapParser` | Pool、GlobalConfig；包括有符号虚拟 quote 储备、费率和禁用标记 | Pool、LP mint、Pool 的 base/quote ATA、GlobalConfig |
| `RaydiumAmmV4Parser` | 752 字节 AmmInfo、传统 Serum/OpenBook v1 OpenOrders 和 Market | authority、标准初始化池及 vault/LP/OpenOrders/TargetOrders、market vault signer |
| `RaydiumCpmmParser` | PoolState、AmmConfig、ObservationState；含 creator/protocol/fund fee 累计 | authority、标准池、vault、LP mint、observation、AmmConfig |
| `RaydiumClmmParser` | PoolState、AmmConfig、DynamicFeeConfig、TickArrayState、扩展位图、ObservationState | Pool、vault、reward vault、TickArray、observation、扩展位图 |
| `RaydiumLaunchLabParser` | 429 字节 PoolState；可直接转换为 `LaunchLabLegs.Pool` | 路由转换仍须提供实际池地址和平台/创建者费用 vault |
| `MeteoraDlmmParser` | LbPair、BinArray、扩展位图、两种 PresetParameter、Oracle 头部 | 多种 LbPair、reserve、BinArray、oracle、扩展位图、reward vault、event authority |
| `MeteoraDammV1Parser` | Pool、Config、Dynamic Vault；稳定/常量乘积曲线枚举及锁定利润 | 多种 permissionless Pool、新版 LP mint、Dynamic Vault、token vault、池持有的 vault LP、协议费账户 |
| `MeteoraDammV2Parser` | Pool、Config、基础费用调度器；动态费用、激活状态和新版金额字段 | 多种 Pool、固定 authority、Config、token vault、token badge、reward vault、event authority |
| `OrcaWhirlpoolParser` | Whirlpool、固定/动态 TickArray、adaptive-fee Oracle | Whirlpool、FeeTier、TickArray、Oracle |

`TokenAccountParser` 另行解析 SPL Token / Token-2022 的 vault 余额、用户 ATA、mint supply、权限和原始 TLV 扩展。Token-2022 扣留的转账费可通过 `withheldTransferFee()` 获取；未知扩展仅保留原始字节，不等于已经支持该扩展的所有交易规则。

## 统一解析池

以下示例中的 `rpc`、`poolAddress` 由调用方提供。RPC 读取在调用方进行，解析入口本身完全离线。

```java
// 使用当前 SDK 的 AccountInfo：先判空，再解码 Base64 数据。
AccountInfo account = rpc.getAccountInfo(poolAddress);
if (account.getValue() == null) {
    throw new IllegalArgumentException("池账户不存在");
}

// owner 是账户所属的 DEX 程序，不是钱包地址或 mint。
DexAccountData pool = DexParsers.parsePool(
        account.getValue().getOwner(), account.getDecodedData());

// 发现信息可以缓存；动态状态要用最新快照。
Map<String, Object> discovery = pool.staticFields();
Map<String, Object> currentState = pool.dynamicFields();
```

`DexParsers.parsePool` 按九种主网 Program ID 分派，并在 Anchor 协议中验证账户判别符。不能仅凭 mint 推断 DEX，也不能将 TickArray、BinArray、vault 或 Config 交给这个池入口。已知协议时可以直接调用 `具体解析类.parsePool(owner, bytes)`。多路由所需的轻量池对象可调用 `router.pda` 下对应转换器的 `fromDexAccountData(...)` 创建，无需再次解析池字节。

只有 `parsePool(bytes)` 的重载无法验证 owner。生产环境优先传入真实 owner；辅助账户同样有带 owner 的重载。DAMM v1 的 Dynamic Vault owner 是独立的 Vault 程序，OpenOrders/Market owner 是池保存的 market program，不是 AMM 程序。

## 字段、余额和 PDA

字段名称支持官方下划线与对应驼峰写法；支持嵌套结构和数组路径。所有整数字段以 `BigInteger` 保留完整精度，金额都是最小单位，不是带小数的人类显示金额。

```java
// 示例一：DLMM 最新池状态。
DexAccountData pair = MeteoraDlmmParser.parsePool(owner, poolBytes);
PublicKey mintX = pair.publicKey("token_x_mint");
int activeId = pair.intValue("activeId");
BigInteger volatility = pair.integer("v_parameters.volatility_accumulator");

// activeId 可以是负数；每个 BinArray 七十个 bin，索引必须向下取整。
long arrayIndex = MeteoraDlmmParser.binArrayIndex(activeId);
PublicKey arrayAddress = MeteoraDlmmParser.deriveBinArray(poolAddress, arrayIndex);

// arrayBytes 必须由调用方获得并持续刷新；计算地址不会生成其内容。
DexAccountData array = MeteoraDlmmParser.parseBinArray(owner, arrayBytes);
BigInteger binAmountX = array.integer("bins[0].amount_x");

// 示例二：从 Token / Token-2022 vault 账户读取当前余额。
TokenAccountParser.TokenAccount vault = TokenAccountParser.parseTokenAccount(
        vaultOwner, vaultBytes);
BigInteger amount = vault.amount();

// ATA 的种子包括 tokenProgram；不能把 Token-2022 的 ATA 按普通 Token 推导。
PublicKey ata = DexPda.associatedTokenAccount(wallet, mintX,
        MeteoraDlmmParser.tokenProgramX(pair));
```

使用 `integer()` 读取 u64/u128，不要直接强转 `long`。`longValueExact()` 和 `intValue()` 仅用于确定范围内的字段，溢出会抛错。u8 数组用 `bytes()`，结构数组用 `list()`，嵌套结构用 `struct()`。枚举是 `DexAccountData.EnumValue`，通过 `variant()`、`fields()` 读取分支及参数。

关键字段速查：

| 协议 | 首次发现后缓存的典型字段 | 必须刷新或监控变更的典型字段/账户 |
| --- | --- | --- |
| PumpSwap | `index`、`creator`、`base_mint`、`quote_mint`、vault、`coin_creator` | 两侧 Token amount、`virtual_quote_reserves`；GlobalConfig 的费率、禁用标记、接收方 |
| AMM V4 | mint、`market_id`、`market_program_id`、vault、OpenOrders 地址 | vault amount、`base_need_take_pnl` / `quote_need_take_pnl`；按交换路径需要的 OpenOrders/market 状态 |
| CPMM | mint、token program、decimals、`amm_config`、vault | vault amount、协议/基金/creator fee 累计、status；AmmConfig 费率 |
| CLMM | mint、tick spacing、config、vault | `sqrt_price_x64`、`tick_current`、liquidity、`tick_array_bitmap`、`dynamic_fee_info`、status，以及路径上的 TickArray/扩展位图 |
| DLMM | mint、bin step、静态参数、token program flags、reserve | `active_id`、`v_parameters`、status、bitmap，以及路径上的 BinArray/扩展位图 |
| DAMM v1 | mint、池类型、Vault/LP 地址 | Pool、Dynamic Vault、reserve、vault LP mint supply、池持有的 vault LP amount；稳定/depeg 池还需相关外部状态 |
| DAMM v2 | mint、vault、token flags、池类型 | reserves/price/liquidity、费用调度器/动态费用、status/activation；新版 tracked amount 与 vault amount |
| Orca | mint、tick spacing、真实 vault、fee tier seed | `sqrt_price`、`tick_current_index`、liquidity、费用状态、路径上的 TickArray；adaptive-fee Oracle |

`staticFields()` 表示“可复用的发现信息”，不是“协议保证永远不变”。每次收到新池或配置快照仍应检查变化，管理员修改手续费、权限、接收方时应替换缓存。

## 本地缓存与持续更新

```java
// 池、vault、tick/bin 数组建议分别使用对应解码器建立缓存。
DexAccountCache<DexAccountData> pools = new DexAccountCache<>(DexParsers::parsePool);
DexAccountCache<TokenAccountParser.TokenAccount> vaults =
        new DexAccountCache<>(TokenAccountParser::parseTokenAccount);

// slot、writeVersion 和 bytes 来自调用方的订阅；普通 RPC 重载可不传 writeVersion。
boolean accepted = pools.update(poolAddress, owner, slot, writeVersion, poolBytes);
pools.get(poolAddress).ifPresent(snapshot -> {
    DexAccountData latest = snapshot.data();
    Map<String, Object> liveFields = latest.dynamicFields();
});

// 确认链上账户关闭后，仅标记本地缓存；此方法不会关闭任何链上账户。
pools.markClosed(poolAddress, closeSlot, closeWriteVersion);
```

缓存按 `(slot, writeVersion)` 拒绝旧更新和重复更新；旧更新不会重复解析。解析失败保留上次有效结果，关闭事件保留版本屏障。解析结果对外返回字节数组/公钥副本，避免调用方修改共享状态；传入的原始数组在解析过程中仍须保持稳定。

缓存不是订阅器，也不保证多个账户来自同一 slot、同一分叉或同一确认级别。报价前的跨账户一致性、数据新鲜度、订阅重连和 fork 回滚由调用方处理。`processed` 回滚时应 `clear()` 并重新同步一致快照。普通 RPC 没有 writeVersion 时，同槽更新默认不覆盖已缓存结果。

## 不能混用的推导规则与版本边界

- **确定性地址不代表账户已经存在。** 不能仅有 PDA 就省略第一次读取，更不能省略储备、tick、bin 的后续更新。
- **PumpSwap** 保留传入 base/quote 顺序，index 是 u16 小端。旧版账户缺失追加尾部字段时采用零值，`defaultedFields()` 标明哪些字段缺失；任何字段读到一半都会报截断，不能当作合法旧版本。
- **AMM V4** `deriveStandard*` 仅对应标准初始化模型；已有池以实际账户记录为准。OpenOrders/Market 只覆盖传统 Serum/OpenBook v1 布局，不是 OpenBook v2。market vault signer 使用链上保存的 u64 nonce，不替它搜索另一个 bump。
- **CPMM** 支持随机 signer Pool；此时不能用 `deriveStandardPool` 替换实际池地址。fee config 种子是 u16 **大端**。
- **CLMM / Orca / DLMM** 当前数组不等于全部 swap 所需数组：交换方向、数量和实时流动性决定是否跨到相邻数组或位图扩展。CLMM tick 种子为 i32 **大端**，DLMM array index 为 i64 **小端**，Orca tick 起点为**十进制文本**。
- **Orca** vault 必须读取 Pool 保存的 `token_vault_a/b`，本包没有编造“vault PDA”。adaptive-fee Whirlpool 的 feeTierIndex 不一定等于 tickSpacing，应读 `fee_tier_index_seed` 后推导 Pool。
- **DAMM v1** permissioned Pool 可由任意 signer 创建；历史 pool/vault 的 LP mint 可能不是 PDA。`deriveModern*LpMint` 只适用对应新版创建方式，旧池使用账户保存的地址。Dynamic Vault 归独立 Vault 程序。
- **DAMM v2** 当前 Pool 布局没有保存 Config 地址，需调用方从建池信息发现并提供，不能伪造一个 pool.config 字段。`trackedAmounts()` 仅接受当前含金额字段的 layout version 1；旧布局不能把填充区的零值当成储备。DLMM 奖励索引种子是 u64，DAMM v2 是 u8，不能混用。
- **DLMM Oracle** 当前提供 `parseOracleHeader`，只解析官方 IDL 定义的头部，不包含其变长历史观察记录。

解析器验证已知布局的 owner、判别符、长度、布尔/枚举编码等，不等于完成所有业务有效性检查。调用方仍须检查账户是否初始化、是否禁用/暂停、mint/vault/数组是否与目标池相连。账户超过已知布局长度时，解析器可能忽略额外尾部；可结合 `consumedBytes()` 与原始长度判断。本包不返回或解释这些未知尾部，也不承诺自动支持未来布局；升级前需重新核对官方定义。

## 储备辅助计算不是完整报价引擎

`PumpSwapParser.effectiveReserves` 将 quote vault 与有符号 virtual quote 合并；`RaydiumAmmV4Parser.effectiveReserves` 使用 vault + OpenOrders total − needTakePnl；`RaydiumCpmmParser.effectiveReserves` 扣除三类已累计费用。DAMM v1 的 `unlockedVaultAmount` / `poolUnderlyingAmount` 处理锁定利润与 vault LP 份额；DAMM v2 的 `trackedAmounts` 读取新版布局金额。

这些方法只计算对应储备或份额，不会遍历所有 tick/bin，不会计算各 DEX 的完整 swap quote、动态费、Token-2022 transfer hook/transfer fee 或稳定/depeg 曲线报价。传入余额前要校验 mint、vault、pool 关联关系，不能混用不同快照。

## 官方布局来源

| 协议 | 随包快照的原始依据 |
| --- | --- |
| PumpSwap | [官方 pump_amm IDL](https://github.com/pump-fun/pump-public-docs/blob/main/idl/pump_amm.json)、[Pool/PDA 文档](https://github.com/pump-fun/pump-public-docs/blob/main/docs/PUMP_SWAP_README.md) |
| AMM V4 | [Raydium SDK layout](https://github.com/raydium-io/raydium-sdk-v1/blob/master/src/liquidity/layout.ts) |
| CPMM | [官方 IDL](https://github.com/raydium-io/raydium-idl/blob/master/raydium_cpmm/raydium_cp_swap.json)、[PoolState](https://github.com/raydium-io/raydium-cp-swap/blob/master/programs/cp-swap/src/states/pool.rs) |
| CLMM | [官方 IDL](https://github.com/raydium-io/raydium-idl/blob/master/raydium_clmm/raydium_clmm.json)、[PoolState](https://github.com/raydium-io/raydium-clmm/blob/master/programs/amm/src/states/pool.rs) |
| DLMM | [官方 IDL](https://github.com/MeteoraAg/dlmm-sdk/blob/main/idls/dlmm.json)、[PDA helper](https://github.com/MeteoraAg/dlmm-sdk/blob/main/ts-client/src/dlmm/helpers/derive.ts) |
| DAMM v1 | [官方 IDL](https://github.com/MeteoraAg/damm-v1-sdk/blob/main/ts-client/src/amm/idl.ts)、[Dynamic Vault IDL](https://github.com/MeteoraAg/vault-sdk/blob/main/ts-client/src/vault/idl.ts) |
| DAMM v2 | [官方 IDL](https://github.com/MeteoraAg/cp-amm-sdk/blob/main/src/idl/cp_amm.json)、[PDA helper](https://github.com/MeteoraAg/cp-amm-sdk/blob/main/src/pda.ts) |
| Orca | [官方生成账户定义](https://github.com/orca-so/whirlpools/tree/main/rust-sdk/client/src/generated/accounts)、[PDA helper](https://github.com/orca-so/whirlpools/blob/main/legacy-sdk/whirlpool/src/utils/public/pda-utils.ts) |

资源位于 `src/main/resources/org/p2p/solanaj/programs/parse/layouts`。本目录的 `.gitignore` 仅将这些公开布局从项目的 `*.json` 忽略规则中排除，确保打包/版本管理包含布局，不涉及钱包 JSON 文件。

## 仅运行离线测试

```shell
mvn -o -Dtest=DexParsersTest test
```

测试覆盖八种协议分派、金额符号边界、辅助账户布局、旧 Pump 尾部、DAMM 稳定曲线/费用枚举、负 tick/bin 索引、两种 Orca TickArray、公开 PDA 向量、Token-2022 和缓存乱序/关闭屏障。不读取钱包、不访问 RPC、不执行买卖。这里只建议运行指定测试，不运行项目里其他可能发送真实交易的测试。
