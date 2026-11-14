# 发布到 Maven Central

发布坐标为 `io.github.masrt-wu:solanaj-17:1.0.0`，代码仓库为
[masrt-wu/cloud-sol-rpc](https://github.com/masrt-wu/cloud-sol-rpc)。
Java 包名仍为 `org.p2p.solanaj`。本配置不会自动部署 Solana 合约或执行实网交易。

## 1. 确认命名空间与版本

使用 GitHub 账号登录 [Central Portal](https://central.sonatype.com/)，在 Namespaces 中确认
`io.github.masrt-wu` 为 Verified，且当前账号拥有发布权限。GitHub 仓库存在不代表已完成 Central 验证。

正式发布后的相同坐标不能覆盖。若 `1.0.0` 已发布，应修改 POM 版本并同步 README 后再发布。
本配置面向正式版本，不使用 `-SNAPSHOT`。

## 2. 准备 JDK 17、Maven 3 和 GPG

```powershell
mvn -version
gpg --version
gpg --list-secret-keys --keyid-format LONG
```

Maven 必须使用 JDK 17。本机可通过发布脚本的 `-JavaHome 'D:\Program Files\JAVA\jdk17'`
参数选择 JDK，脚本结束后会恢复原进程环境。

如果没有自己的 GPG 签名密钥：

```powershell
gpg --full-generate-key
gpg --list-secret-keys --keyid-format LONG
```

将下面占位符替换为自己的完整公钥指纹，公开公钥：

```powershell
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_FULL_PUBLIC_KEY_FINGERPRINT
```

GPG 是 Maven 制品的签名密钥，不是 Solana 钱包私钥。私钥由本机 GPG 管理，不要导出进仓库。
口令优先通过 GPG Agent 提示输入；自动化场景也可使用 `MAVEN_GPG_PASSPHRASE`。

## 3. 配置发布凭据

在 Central Portal 的账号页面生成发布 Token，保存它给出的用户名和密码。
这不是 GitHub Token，也不是网页登录密码。

在项目根目录的 `.env` 中填写以下项目；已有 `.env` 时直接编辑，不要覆盖：

```dotenv
CENTRAL_TOKEN_USERNAME=
CENTRAL_TOKEN_PASSWORD=
GPG_KEYNAME=
MAVEN_GPG_PASSPHRASE=
```

前两项只在上传时必需。`GPG_KEYNAME` 可填自己的完整公钥指纹，存在多把签名密钥时建议设置。
交互式签名时可将 `MAVEN_GPG_PASSPHRASE` 留空。不要将真实值提交到 GitHub。

`scripts/publish-central.ps1` 只加载这四个发布配置项，不导入 Solana 钱包私钥。
进程环境中已有的非空配置优先；脚本退出时会恢复原值。`.mvn/central-settings.xml` 只包含环境变量引用。
Maven 本身不会读取 `.env`，因此直接运行 `mvn` 时需要事先配置对应的进程环境变量。
脚本使用该独立 settings 模板；如果需要企业代理或镜像，可在模板中补充不含凭据的相关配置。

## 4. 先生成本地签名发布包

在项目根目录执行：

```powershell
.\scripts\publish-central.ps1 -JavaHome 'D:\Program Files\JAVA\jdk17'
```

默认只构建、签名并生成本地 bundle，不向 Central 上传。不需要 Central Token，但需要本机 GPG 签名密钥。
脚本使用 `"-Dmaven.test.skip=true"`，不会编译或执行仓库中的实网买卖测试。
若系统执行策略阻止脚本，可仅对本次进程执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-central.ps1 -JavaHome 'D:\Program Files\JAVA\jdk17'
```

检查 `target` 下的主 JAR、sources JAR、Javadoc JAR、对应 `.asc` 签名，以及
`target/central-publishing/central-bundle.zip`。核对包内不包含 `.env`、Token、钱包文件或崩溃日志。
POM 中 `central.skipUpload` 默认也为 `true`，避免直接调用 deploy 时意外上传。

## 5. 上传并由网页确认发布

确认 namespace 已验证、版本可用、发布包正确之后，显式选择上传：

```powershell
.\scripts\publish-central.ps1 -Upload -JavaHome 'D:\Program Files\JAVA\jdk17'
```

此操作将发布包上传到 Central 并等待校验，**不会自动正式发布**。
插件的 `autoPublish=false`，最后需由你在 Central Portal 的 Publishing 页面检查状态并点击发布。

若不使用脚本，对应 Maven 命令为：

```powershell
mvn --settings .mvn/central-settings.xml -Pdeploy "-Dmaven.test.skip=true" "-Dcentral.skipUpload=false" clean deploy
```

此直接命令要求你已设置 JDK 17、Token 和 GPG 的进程环境；不会替你读取 `.env`。

## 6. 发布后引用

```xml
<dependency>
    <groupId>io.github.masrt-wu</groupId>
    <artifactId>solanaj-17</artifactId>
    <version>1.0.0</version>
</dependency>
```

上传通过不等于已正式发布；发布完成并同步可用后，其他项目才可从 Maven Central 下载。
配置修改尚未提交 GitHub 时，应先将 POM、文档、脚本和空模板提交并推送；`.env` 始终留在本机。

## 官方依据

- [Central Maven 发布插件](https://central.sonatype.org/publish/publish-portal-maven/)
- [Central 发布要求](https://central.sonatype.org/publish/requirements/)
- [命名空间验证](https://central.sonatype.org/register/namespace/)
- [发布 Token](https://central.sonatype.org/publish/generate-portal-token/)
- [GPG 签名](https://central.sonatype.org/publish/requirements/gpg/)
