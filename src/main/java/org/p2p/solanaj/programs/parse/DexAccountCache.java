package org.p2p.solanaj.programs.parse;

import org.p2p.solanaj.core.PublicKey;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * 本地账户快照缓存，按 slot 和 writeVersion 拒绝乱序更新。
 * 本类不订阅链上、不发 RPC、不提供跨账户同槽一致性或交易确认保证。
 * 若使用 processed 订阅遇到分叉回滚，应清空缓存并重新读取一致快照。
 * @param <T> 解析结果类型，建议使用不可变对象
 */
public final class DexAccountCache<T> {
    /** 按真实账户地址保存当前快照；每个地址的更新是原子的。 */
    private final ConcurrentHashMap<PublicKey, Entry<T>> entries = new ConcurrentHashMap<>();
    /** 只解析数据的函数，不应访问 RPC 或提交交易。 */
    private final BiFunction<PublicKey, byte[], T> decoder;
    /** 本地关闭标记使用的程序地址，不会向该程序发送指令。 */
    private static final PublicKey SYSTEM_OWNER = new PublicKey("11111111111111111111111111111111");

    /**
     * 一条账户快照。
     * @param owner 账户所属程序
     * @param slot 来源 slot
     * @param writeVersion 订阅提供的写入版本；普通 RPC 可传零
     * @param data 已解析状态；null 表示内部关闭标记
     * @param <T> 解析结果类型
     */
    public record Entry<T>(PublicKey owner, long slot, long writeVersion, T data) {
        public Entry { owner = new PublicKey(Objects.requireNonNull(owner).toByteArray()); }
        /** @return owner 的副本，避免外部修改缓存元数据 */
        @Override public PublicKey owner() { return new PublicKey(owner.toByteArray()); }
    }

    /**
     * 建立缓存；例如 DexParsers::parsePool 或 TokenAccountParser::parseTokenAccount。
     * @param decoder 接收实际 owner 与原始字节的本地解析函数
     */
    public DexAccountCache(BiFunction<PublicKey, byte[], T> decoder) {
        this.decoder = Objects.requireNonNull(decoder, "解析函数不能为空");
    }

    /**
     * 接受新快照；解析失败不会覆盖已有状态，旧事件不重复解析。
     * @param address 被更新的真实账户地址
     * @param owner 该账户实际所属程序
     * @param slot 来源 slot
     * @param writeVersion 写入版本，同槽旧版本或相同版本被忽略
     * @param data 新账户原始字节
     * @return 是否成功接受该更新
     */
    public boolean update(PublicKey address, PublicKey owner, long slot, long writeVersion, byte[] data) {
        Objects.requireNonNull(owner, "账户 owner 不能为空");
        Objects.requireNonNull(data, "账户数据不能为空");
        return updateEntry(address, slot, writeVersion,
                () -> new Entry<>(owner, slot, writeVersion,
                        Objects.requireNonNull(decoder.apply(owner, data), "解析结果不能为空")));
    }

    /**
     * 接受没有 writeVersion 的普通 RPC 快照；同槽不会覆盖已缓存快照。
     * @param address 真实账户地址
     * @param owner 实际所属程序
     * @param slot RPC 返回的 context.slot
     * @param data 原始账户字节
     * @return 是否成功接受更新
     */
    public boolean update(PublicKey address, PublicKey owner, long slot, byte[] data) {
        return update(address, owner, slot, 0, data);
    }

    /**
     * 标记链上账户已关闭，保留版本屏障防止旧订阅事件复活缓存。
     * @param address 被关闭的账户地址
     * @param slot 关闭事件 slot
     * @param writeVersion 关闭事件写入版本
     * @return 是否接受关闭事件；只修改本地缓存
     */
    public boolean markClosed(PublicKey address, long slot, long writeVersion) {
        return updateEntry(address, slot, writeVersion, () -> new Entry<>(SYSTEM_OWNER, slot, writeVersion, null));
    }

    /**
     * 获取当前未关闭账户的解析结果。
     * @param address 真实账户地址
     * @return 当前快照；不存在或已关闭时为空
     */
    public Optional<Entry<T>> get(PublicKey address) {
        Entry<T> entry = entries.get(Objects.requireNonNull(address, "账户地址不能为空"));
        return entry == null || entry.data() == null ? Optional.empty() : Optional.of(entry);
    }

    /** 清空本地缓存，适用于订阅重建或分叉回滚后的重新同步；不关闭任何链上账户。 */
    public void clear() { entries.clear(); }

    /** 在地址锁内检查版本，再进行解析或关闭标记更新。 */
    private boolean updateEntry(PublicKey address, long slot, long version,
                                java.util.function.Supplier<Entry<T>> supplier) {
        if (slot < 0 || version < 0) throw new IllegalArgumentException("slot 和写入版本不能为负数");
        PublicKey key = new PublicKey(Objects.requireNonNull(address, "账户地址不能为空").toByteArray());
        AtomicBoolean accepted = new AtomicBoolean();
        entries.compute(key, (ignored, previous) -> {
            if (previous != null && (slot < previous.slot()
                    || (slot == previous.slot() && version <= previous.writeVersion()))) return previous;
            Entry<T> next = supplier.get();
            accepted.set(true);
            return next;
        });
        return accepted.get();
    }
}
