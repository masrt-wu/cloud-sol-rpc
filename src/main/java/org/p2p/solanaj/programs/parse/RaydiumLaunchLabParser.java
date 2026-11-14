package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.util.Objects;

/** Raydium LaunchLab PoolState 解析器；字段布局与现有链上 PoolState 解析器一致。 */
public final class RaydiumLaunchLabParser {
    /** LaunchLab 主网程序地址。 */
    public static final PublicKey PROGRAM_ID = new PublicKey("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
    /** 可缓存的池发现字段；状态和费用金额仍需及时刷新。 */
    private static final String[] STATIC_FIELDS =
            "global_config platform_config base_mint quote_mint base_vault quote_vault creator auth_bump base_decimals quote_decimals migrate_type token_program_flag"
                    .split(" ");

    private RaydiumLaunchLabParser() { }

    /**
     * 解析原始 PoolState，不校验账户 owner。
     * @param data PoolState 原始账户字节
     * @return 已解析的池数据
     */
    public static DexAccountData parsePool(byte[] data) {
        return DexLayouts.parse("raydium-launchlab", "PoolState", null, data, STATIC_FIELDS);
    }

    /**
     * 解析 PoolState，同时确认 RPC 返回的账户 owner。
     * @param owner PoolState 的链上所属程序
     * @param data PoolState 原始账户字节
     * @return 已解析的池数据
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        return DexLayouts.parse("raydium-launchlab", "PoolState",
                Objects.requireNonNull(owner, "账户 owner 不能为空"), data, STATIC_FIELDS);
    }
}
