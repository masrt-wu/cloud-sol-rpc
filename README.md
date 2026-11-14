# Cloud Sol RPC

English | [简体中文](docs/README.md)

Cloud Sol RPC is a Java 17 Solana client library built on the SolanaJ package structure. It provides JSON-RPC access, WebSocket subscriptions, transaction construction and signing, DEX account decoding, and instructions for a custom on-chain trade router.

This repository builds a JAR library, rather than a standalone RPC server. Its Maven coordinates are `com.duonian:solanaj-jdk17:1.7.9`; Java packages remain under `org.p2p.solanaj`.

## Features

- **RPC:** query balances, accounts, tokens, blocks, transactions, and signature statuses; simulate and submit transactions. Configure custom endpoints, timeouts, an OkHttp client, a SOCKS proxy, or weighted endpoint selection.
- **WebSocket:** subscribe to account, signature, log, program, slot, and block notifications through `SubscriptionWebSocketClient`.
- **Accounts and transactions:** public keys, keypairs, mnemonic derivation, transaction signing and serialization, and program-derived addresses (PDAs).
- **Program instructions:** System, SPL Token, associated token accounts (ATAs), compute budget, memo, and address lookup tables, plus protocol-specific trading helpers.
- **DEX decoding:** PumpSwap; Raydium AMM V4, CPMM, CLMM, and LaunchLab; Meteora DLMM, DAMM v1, and DAMM v2; Orca Whirlpool. Includes Token / Token-2022 account decoding, PDA helpers, and a caller-fed local snapshot cache.
- **Trade router:** single-hop swaps, fixed or dynamic two-hop routes, three-hop routes, and Solana v0 transactions using address lookup tables (ALTs).
- **Event decoding:** separate helpers for Pump.fun bonding-curve logs and Pump AMM trade events.

Parser coverage and router coverage differ. For example, DAMM v1 has account parsers but no route-leg builder. The router also includes Pump.fun V2 legs for non-WSOL SPL quote tokens; this does not imply a unified Pump.fun pool parser.

## Requirements and installation

Use **JDK 17** and a local **Maven** installation. There is no Maven wrapper in this repository. Verify that `JAVA_HOME` and Maven both use JDK 17:

```shell
java -version
mvn -version
```

Build and install the current checkout into your local Maven repository:

```shell
mvn -Dmaven.test.skip=true install
```

This command skips test compilation and execution. The test tree includes live RPC and transaction examples; review individual classes before running them. Normal local builds do not require GPG configuration; signing is configured in the `deploy` profile.

Add the following dependency to the consuming project's `pom.xml` after local installation:

```xml
<dependency>
    <groupId>com.duonian</groupId>
    <artifactId>solanaj-jdk17</artifactId>
    <version>1.7.9</version>
</dependency>
```

These coordinates come from this checkout's [pom.xml](pom.xml). If its version changes, update your dependency accordingly. Local installation is sufficient for the example above; availability in a public Maven repository is not assumed.

## Local configuration and private keys

Private keys are loaded from `.env` in the application's working directory through `org.p2p.solanaj.utils.Env`. For a new checkout, create your local file in PowerShell:

```powershell
Copy-Item .env.example .env
```

If `.env` already exists, edit it directly and preserve its contents. Fill in the configuration shown in [.env.example](.env.example). Configure exactly one wallet format: `SOLANA_PRIVATE_KEY_BASE58` for a Base58-encoded 64-byte keypair, or `SOLANA_SECRET_KEY_JSON` for a single-line JSON array of 64 unsigned bytes; leave the other empty. `Env.account()` loads that wallet. The separate `SOLANA_PUMP_V3_PRIVATE_KEY_BASE58`, `SOLANA_PUMP_ALTERNATE_PRIVATE_KEY_BASE58`, and `SOLANA_ATA_PRIVATE_KEY_BASE58` entries preserve the distinct wallets used by legacy examples.

`Env.require("NAME")` reads other settings, such as RPC URLs and amounts. Process environment variables override the same `.env` keys, including empty values. The file is read once per process; restart after editing it. Blank lines, comments, optional `export`, and single/double quoted values are supported; multiline values and variable interpolation are not. Missing keys fail explicitly without printing their contents. There is no fallback signing wallet.

The legacy Pump examples also read `HELIUS_RPC_URL` and `JITO_AUTH_RPC_URL` from `.env` for endpoints containing service credentials. The mnemonic passphrase regression test reads `TEST_MNEMONIC_PASSPHRASE`. Keep API keys, authentication UUIDs, and passphrases out of source code as well as wallet secrets.

`.env` and `.env.*` are ignored by Git; only the empty credential template `.env.example` is intended for version control. Keep `.env` in the application working directory, outside `src/main/resources`, so it is not bundled in the SDK JAR. Historical regression credentials use separate `TEST_*` keys and never fall back to the trading wallet; tests requiring those fixtures skip when they are not configured.

## Quick start

The following Java snippets use the configuration loader above. RPC calls can throw `RpcException`; the complete examples below declare `throws Exception` for brevity.

### Read a balance

Set `SOLANA_RPC_URL` to your RPC endpoint and `SOLANA_ACCOUNT` to a Base58 public key. This example needs no private key.

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

SOL balances are returned in lamports: `1 SOL = 1_000_000_000 lamports`. Token amounts use each mint's smallest unit; apply its decimals only when formatting display values.

### Construct and simulate a SOL transfer

Use a devnet endpoint and a funded devnet wallet for this example. Set one of the wallet keys described above in `.env`, `SOLANA_RECIPIENT` to the recipient's public key, and `AMOUNT_LAMPORTS` to a positive integer. Keep secrets out of source control and logs.

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

        // Uncomment only when you intend to broadcast the transfer:
        // String signature = rpc.sendTransaction(tx, signer);
    }
}
```

Simulation does not broadcast. A returned transaction signature is not confirmation: query `getSignatureStatuses(...)` and check the result after submission. Simulation success does not guarantee inclusion or execution success.

### Subscribe to account updates

Set `SOLANA_WS_URL` to the complete WebSocket URL supplied by your RPC provider. Constructing the client with a `URI` preserves the endpoint's path, port, and query parameters.

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
            System.in.read(); // Keep the example running until input is received.
        } finally {
            ws.closeBlocking();
        }
    }
}
```

### Decode a DEX pool

The caller fetches the account; `DexParsers` decodes its bytes locally. Use the account's actual owner program to select the protocol.

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

This entry point accepts pool accounts, not token vaults or tick/bin arrays. Decode those with their specific parsers. Integer fields retain full precision as `BigInteger`; parsing reserves does not calculate a complete swap quote.

## Using the trade router

`RouterTradeBuilder` builds instructions locally. `RouterTrader` checks on-chain configuration and relevant account owners, simulates transactions, and optionally broadcasts them. For dynamic two-hop execution, use the `buyDynamic*` / `sellDynamic*` methods; three-hop execution uses the corresponding `buy*` / `sell*` overloads.

Supply current pool snapshots, token programs, fee accounts, and required tick/bin arrays. The library does not automatically discover pools, calculate full quotes, choose routes, or create/fetch ALTs. Set minimum outputs from a current quote and your slippage limit.

- The configured `RouterTradeBuilder.ROUTER_PROGRAM` must match your deployed program. `RouterTrader` currently requires its on-chain `fee_bps` to be zero.
- Dynamic two-hop and three-hop routes require an on-chain router supporting their instruction formats (tags `3` and `4`). The Java library alone does not deploy or upgrade that program.
- `broadcast=false` simulates only; `broadcast=true` simulates before sending. Continue tracking the signature after broadcasting.
- Larger routes may need `RouterV0Transaction` and existing, active ALT snapshots to stay within the 1232-byte transaction limit.
- SOL wrapping helpers leave the WSOL ATA open. Router support does not automatically supply Token-2022 transfer-hook accounts, all tick/bin extensions, or priority fees.

See the [router guide](src/main/java/org/p2p/solanaj/router/README.md) for pool parameters, route examples, and protocol-specific limits. The detailed guides linked below are currently in Chinese.

## Buy and sell examples

The first three examples below execute through the custom Router described above. Save `TradeSupport.java` alongside the example classes; they intentionally use the same helper. Pass a configured `RpcApi`, a signer loaded with `Env.account()`, and a verified pool snapshot. Set `buy=true` to buy and `buy=false` to sell; execute only the selected direction.

All amounts are positive integers in the relevant mint's smallest unit. For these exact-input Router examples, buying spends `amountInRaw` quote for at least `minOutRaw` base; selling spends `amountInRaw` base for at least `minOutRaw` quote. Quote is the pool's actual quote token, which is not necessarily SOL.

The helper creates the output ATA if needed, but the input ATA must already exist and contain enough tokens. For WSOL input, wrap and sync SOL beforehand; single-hop `Swap` does not fund WSOL automatically. Keep sufficient native SOL for transaction fees and ATA rent. These methods simulate only; change `broadcast` to `true` in the helper only when you intend to submit.

### Shared Router simulation helper

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

### Pump AMM / PumpSwap: buy or sell

Use `PumpAmmExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)` to buy, or `PumpAmmExample.trade(rpc, signer, pool, false, baseInRaw, minQuoteOutRaw)` to sell.

Populate `PumpAmmLegs.Pool` with the real pool address, base/quote mints and vaults, both token programs, protocol fee recipient, creator vault ATA/authority, buyback recipient, and applicable creator/cashback fields. Refresh fee accounts from the current configuration; a mint address alone is insufficient. The [pool adapter guide](src/main/java/org/p2p/solanaj/router/pda/README.md) describes conversion from decoded pool data.

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

### Raydium pools: buy or sell

Use `RaydiumExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)` to buy, or the same overload with `false, baseInRaw, minQuoteOutRaw` to sell. The pool type selects AMM V4, CPMM, or CLMM.

For AMM V4, quote/base means pc/coin; for CLMM, it means token1/token0. Check the actual mints before treating either direction as buying a target token. If your intended input is on the other side, use `RaydiumLegs.ammV4/cpmm/clmm(..., inputMint, ...)` and wrap the resulting leg with matching token programs.

AMM V4 needs its AMM address, coin/pc mints and vaults, and classic Token Program. CPMM also needs config, observation, and both token programs. CLMM needs config, observation, both token programs, current ordered tick arrays, and the bitmap extension when required. Use the corresponding `router.pda.ray` adapters to construct these records from verified account data.

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

### Pump.fun bonding curve: buy or sell with SPL quote

Use `PumpFunExample.trade(rpc, signer, pool, true, quoteInRaw, minBaseOutRaw)` to buy, or the same method with `false, baseInRaw, minQuoteOutRaw` to sell.

This example uses `PumpFunLegs.Pool` and the V2 `buy_exact_quote_in_v2` / `sell_v2` instructions. Supply base/quote mints, their actual token programs, bonding curve, curve base token account, actual creator vault authority, protocol fee recipient, and buyback fee recipient. The creator vault can be a shared vault; do not infer it from a creator public key alone.

This Router path accepts a **non-WSOL SPL quote**. It rejects the WSOL mint because native SOL bonding curves use a different settlement path. It is separate from PumpSwap AMM.

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

### Pump.fun bonding curve: native SOL V3 helper

For a native SOL pair, the repository also exposes `PumpDIumProgram.makePumpTotalToken2022V3`, which builds a direct Pump instruction. This example does not use `TradeSupport` or the custom Router. Pass a fully populated, verified `PumpToken2022PoolInfoV3` matching the target V3 layout and signer; the method does not fetch or validate that layout on-chain.

Here `buy=true` encodes type `0` (`buy_v3`): `baseAmountRaw` is the requested base amount and `quoteLimitLamports` is the maximum SOL cost. `buy=false` encodes type `1` (`sell_v3`): those values are the base amount sold and minimum SOL received. These are different amount semantics from the exact-input Router buy examples.

For this native SOL path, `info.quoteMint` uses the WSOL mint identifier, but settlement uses native SOL. Prepare the user's base token account and other required accounts beforehand; the example only adds the swap instruction. `refPumpToken2022V3(...)` is a local convenience factory: it hardcodes some Token-2022/classic Token ATA derivations and selects a recipient from a built-in list, so verify every generated field against the actual mint owners and current protocol configuration.

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

Call `PumpFunSolExample.simulate(rpc, signer, info, true, baseToBuyRaw, maxSolLamports)` to simulate buying, or use `false, baseToSellRaw, minSolLamports` to simulate selling. No transaction is broadcast. Before using this helper with a live pool, confirm that its current instruction layout and required accounts match this checkout's implementation.

### Existing `TestPumpToken2022`: buying on the Pump.fun bonding curve

The repository's [TestPumpToken2022.java](src/test/java/org/p2p/solanaj/programs/TestPumpToken2022.java) contains direct **Pump.fun bonding-curve Token-2022 buys**, separate from PumpSwap AMM and the custom Router. It is example/test code under `src/test/java`, not a class included in the SDK JAR.

- `buyPumpAss()` uses `PumpApiIsComToken2022PoolInfo`, `refPumpToken2022(...)`, and `makePumpTotalToken2022(info, 0, baseAmountRaw, maxSolLamports)`. Its signer comes from `Env.account()`. The current method simulates; its send call is commented out.
- `buyPumpAssV3()` uses `PumpToken2022PoolInfoV3`, `refPumpToken2022V3(...)`, and `makePumpTotalToken2022V3(info, 0, baseAmountRaw, maxSolLamports)`. Its signer comes from `SOLANA_PUMP_V3_PRIVATE_KEY_BASE58` in `.env`. **The current method broadcasts a real transaction after successful simulation**, using the configured durable nonce account.

Both buy paths use type `0`: the first amount is the requested number of base tokens in raw units, and the second is the maximum SOL cost in lamports. They derive the user's Token-2022 ATA, add an idempotent ATA-creation instruction, and include compute-budget and nonce-advance instructions before the buy.

Before adapting either example, replace its historical RPC choice, mint, nonce account, price, and amount with your verified configuration. The legacy path additionally supplies a creator address. Verify the mint owner, actual decimals, current curve state and instruction layout; the examples assume six token decimals and use a hardcoded price rather than a live quote. The nonce account must exist, and the signing wallet must be its nonce authority with enough SOL for the purchase, fees, and ATA creation.

To run only the legacy buy simulation after reviewing those parameters:

```shell
mvn "-Dtest=TestPumpToken2022#buyPumpAss" test
```

The following selects the V3 buy method and **can spend real SOL and advance the nonce**. Review its active `sendTransaction(...)` call before running it; comment out the send block if you only want simulation.

```shell
mvn "-Dtest=TestPumpToken2022#buyPumpAssV3" test
```

Select a single method rather than running the entire class, which also contains selling and account-closing operations. These network examples are not part of the offline verification commands below.

## Project layout and documentation

```text
src/main/java/org/p2p/solanaj/
  core/                 Keys, messages, transactions, and signing
  rpc/                  JSON-RPC client, API, models, and configuration
  ws/                   WebSocket subscriptions and listeners
  programs/             Program instruction builders
    parse/              DEX layouts, account parsers, PDA helpers, cache
  router/               Route legs, builders, execution, and v0 transactions
    pda/                Pool-to-route adapters and address helpers
  token/                Token manager
  utils/                Encoding, wallet derivation, and protocol utilities
src/main/resources/     Bundled DEX layout snapshots
src/test/java/          Unit tests and network/trading examples
docs/                   Chinese README, event guide, and license
```

- [中文项目说明](docs/README.md)
- [DEX account parsing, PDAs, and caching](src/main/java/org/p2p/solanaj/programs/parse/README.md)
- [Trade router](src/main/java/org/p2p/solanaj/router/README.md)
- [Pool-to-route adapters](src/main/java/org/p2p/solanaj/router/pda/README.md)
- [Pump AMM event decoding](docs/PUMP_AMM_EVENTS.md)

## Development and verification

Compile production code without running examples:

```shell
mvn -Dmaven.test.skip=true compile
```

Run selected offline regression classes, with the comma-separated selector quoted for PowerShell:

```shell
mvn "-Dtest=DexParsersTest,RouterFastPoolParsersTest,PumpAmmLogParserTest,RouterPumpDynamicSyncTest,RouterThreeHopSyncTest" test
```

Add `-o` when all dependencies and Maven plugins are already cached. Avoid running the entire test tree without reviewing it: some classes contain historical endpoints, pool addresses, and transaction-sending examples. Supply your own configuration before adapting those examples. The configuration loader can be checked offline with `mvn "-Dtest=EnvTest" test`.

When changing protocol layouts or instruction builders, update the relevant documentation and offline regression cases. Bundled layouts describe the versions included in this checkout; future on-chain changes require review and SDK updates.

## License

Distributed under the [MIT License](docs/LICENSE). Retain the existing copyright and license notices when redistributing the code.
