package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

/** DEX 池账户的统一入口；通过链上 owner 识别协议，不通过 mint 猜测协议。 */
public final class DexParsers {
    private DexParsers() { }

    /**
     * 按程序 owner 解析池账户；不适用于 vault、mint 或 tick/bin 数组账户。
     * @param owner 池账户的链上所属程序，不是钱包地址或 Router 地址
     * @param data 池账户原始字节
     * @return 含完整字段、可缓存发现信息和动态状态的解析结果
     */
    public static DexAccountData parsePool(PublicKey owner, byte[] data) {
        if (PumpSwapParser.PROGRAM_ID.equals(owner)) return PumpSwapParser.parsePool(owner, data);
        if (RaydiumAmmV4Parser.PROGRAM_ID.equals(owner)) return RaydiumAmmV4Parser.parsePool(owner, data);
        if (RaydiumCpmmParser.PROGRAM_ID.equals(owner)) return RaydiumCpmmParser.parsePool(owner, data);
        if (RaydiumClmmParser.PROGRAM_ID.equals(owner)) return RaydiumClmmParser.parsePool(owner, data);
        if (RaydiumLaunchLabParser.PROGRAM_ID.equals(owner)) return RaydiumLaunchLabParser.parsePool(owner, data);
        if (MeteoraDlmmParser.PROGRAM_ID.equals(owner)) return MeteoraDlmmParser.parsePool(owner, data);
        if (MeteoraDammV1Parser.PROGRAM_ID.equals(owner)) return MeteoraDammV1Parser.parsePool(owner, data);
        if (MeteoraDammV2Parser.PROGRAM_ID.equals(owner)) return MeteoraDammV2Parser.parsePool(owner, data);
        if (OrcaWhirlpoolParser.PROGRAM_ID.equals(owner)) return OrcaWhirlpoolParser.parsePool(owner, data);
        throw new IllegalArgumentException("不支持的池账户 owner：" + owner);
    }

    /**
     * 使用 RPC 返回的 Base58 owner 文本解析池。
     * @param owner 池账户链上所属程序的 Base58 文本
     * @param data 池账户原始字节
     * @return 解析结果
     */
    public static DexAccountData parsePool(String owner, byte[] data) {
        if (owner == null) throw new IllegalArgumentException("池账户 owner 不能为空");
        return parsePool(new PublicKey(owner), data);
    }
}
