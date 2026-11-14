package org.p2p.solanaj.programs.parse;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 两侧可用储备的计算结果，不代表已计算滑点、价格影响或转账费。
 * @param first 第一侧储备，按具体协议的 base/0/A 顺序
 * @param second 第二侧储备，按具体协议的 quote/1/B 顺序
 */
public record DexReserves(BigInteger first, BigInteger second) {
    public DexReserves {
        Objects.requireNonNull(first, "第一侧储备不能为空");
        Objects.requireNonNull(second, "第二侧储备不能为空");
        if (first.signum() < 0 || second.signum() < 0)
            throw new IllegalArgumentException("可用储备不能为负数，请检查账户快照是否一致");
    }
}
