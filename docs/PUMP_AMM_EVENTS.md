# Pump AMM 成交事件解析

入口：`org.p2p.solanaj.rpc.types.pump.PumpAmmLogParser`，与内盘 `PumpLogFdvParser` 分开使用。

本实现核对当前官方 `pump_amm.json` 的 BuyEvent/SellEvent 字段顺序：
https://github.com/pump-fun/pump-public-docs/blob/main/idl/pump_amm.json

## 原始字节及调用

```java
import cn.hutool.core.codec.Base58;
import org.p2p.solanaj.rpc.types.pump.PumpAmmLogParser;

// RPC inner instruction.data 是Base58字符串；protobuf已经是byte[]时直接传原始字节。
byte[] bytes = Base58.decode(instruction.getData());
var event = PumpAmmLogParser.decodeEvent(bytes);
if (event instanceof PumpAmmLogParser.BuyEventFull buy) {
    String wallet = buy.user();
    var tokensOutRaw = buy.baseAmountOut();
    var userQuoteSpendRaw = buy.userQuoteAmountIn();
} else if (event instanceof PumpAmmLogParser.SellEventFull sell) {
    String wallet = sell.user();
    var tokensInRaw = sell.baseAmountIn();
    var userQuoteReceiveRaw = sell.userQuoteAmountOut();
}

// 已确定买/卖时可直接调用，省去类型分派。
var buy = PumpAmmLogParser.decodeBuyEventFull(bytes);
var sell = PumpAmmLogParser.decodeSellEventFull(bytes);

// 日志输入：Program data: 后面必须是Base64，不是Base58。
var fromLog = PumpAmmLogParser.decodeLog(logLine);
```

非目标事件返回null，识别到的损坏/旧版短事件或未知尾部抛IllegalArgumentException。
调用方应按自己策略明确处理异常，不能把错误解析的余额、市值继续当作正常信号。
解码器只识别数据布局，不能证明来源真实或交易成功；调用前仍应核验产生事件的程序/调用上下文属于
`PumpAmmLogParser.PROGRAM_ID`，并检查交易meta.err。同一交易里包含某个程序，不代表每条事件都来自该程序。
这是独立解码工具，本轮未接入现有策略、未改变下单，也未更改内盘解析方法。

## 数值及字段口径

- `virtualQuoteReserves` 是 **i128小端、16字节、有符号**，使用BigInteger；不能按u64读取。
- 金额、费率、储备、供应量等u64字段也用BigInteger，避免高位为1时变成负数。两个时间戳为i64 Unix秒，用long。
- 不自动缩放decimals、不假定quote是WSOL、不自动把quote换算为SOL。
- 不把maxQuoteAmountIn/minQuoteAmountOut等限额当成实际成交额；费用前金额、用户支出/收到量分别保留。
- `baseSupply` 来自当前事件，不固定假设总供应量为10亿；本类不计算FDV。
- 同tx多钱包或同钱包多次交易，需要调用方按user、pool及具体事件顺序定位/聚合；本类只解析这一条事件。
- BuyEvent有trackVolume/成交量跟踪/minBaseAmountOut/ixName，SellEvent没有这些字段；两个布局不能共用固定偏移。
- JSON数字/字符串格式由序列化层决定。若需要把u64发给JavaScript，注意超过其安全整数范围时按字符串传输，不要先转double。

## 性能与范围

判别符为静态常量；使用小端ByteBuffer顺序读取，不构造JSON/Map，不查RPC、不打印、不加锁。
u64不逐字段申请8字节数组；i128在常见long范围内走快速路径。
完整方法仍会创建BigInteger、地址字符串及事件对象，因此按需调用；没有自动替换任何现有热路径。
支持当前完整布局，故意不猜测历史可选字段，不把不存在的数据默认补成0；未来IDL变更需更新解码器。

## 离线验证

在SDK目录使用JDK17运行：

```text
mvn -o -Dtest=PumpAmmLogParserTest test
```

只执行该测试类，不要为验证本解析器运行项目中包含实网RPC/交易行为的全部示例测试。
测试包括用户提供的497字节买入事件全部字段、独立卖出布局、直接/CPI/Base64信封、u64上限、
i128最小/最大及随机正负数、UTF-8动态ixName、所有截断位置、非法布尔值、未知尾部。

本轮不修改pom版本号，不自动install或发布SDK。其他Java项目若从Maven仓库引用SDK，需按现有流程重新打包/安装后才能使用新类。
