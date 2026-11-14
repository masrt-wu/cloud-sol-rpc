package org.p2p.solanaj.programs.parse;

import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 一次账户更新的解析结果；支持点分字段路径及数组下标，不访问 RPC。
 * u64、u128、i128 等整数统一使用 BigInteger，避免 Java long 符号溢出。
 * 字段名既可使用官方 snake_case，也可使用对应 camelCase。
 */

public final class DexAccountData {
    /** 当前账户类型名称。 */
    private final String accountType;
    /** 当前解析消耗的字节数，不包含未来版本未知的尾部字段。 */
    private final int consumedBytes;
    /** 解析后的内部字段；对外访问时复制可变值。 */
    private final Map<String, Object> values;
    /** 可缓存的发现信息字段；缓存不是永远不变的承诺。 */
    private final Set<String> staticNames;
    /** 因旧版账户没有尾部字段而采用零值的字段。 */
    private final Set<String> defaultedFields;

    DexAccountData(String accountType, int consumedBytes, Map<String, Object> values,
                   Set<String> staticNames, Set<String> defaultedFields) {
        this.accountType = accountType;
        this.consumedBytes = consumedBytes;
        this.values = copyMap(values);
        this.staticNames = Set.copyOf(staticNames);
        this.defaultedFields = Set.copyOf(defaultedFields);
    }

    /**
     * 协议枚举的解析结果；例如 DAMM v1 曲线类型、Orca 动态 tick。
     * @param variant 官方枚举分支名称
     * @param index 分支在协议定义中的索引
     * @param fields 分支参数；无参数分支为空列表
     */
    public record EnumValue(String variant, int index, List<Object> fields) {
        public EnumValue { fields = copyList(fields); }
        /** 返回参数副本，防止调用方修改内部字节数组或公钥。 */
        @Override public List<Object> fields() { return copyList(fields); }
    }

    /** @return 官方账户类型名称 */
    public String accountType() { return accountType; }

    /** @return 已识别布局实际消耗的字节数 */
    public int consumedBytes() { return consumedBytes; }

    /**
     * 读取字段，例如 pool_fees.dynamic_fee.volatility_accumulator 或 ticks[0]。
     * @param path 字段路径
     * @return 字段值的安全副本
     */
    public Object get(String path) { return copyValue(resolve(path)); }

    /**
     * 判断字段路径是否存在；旧版尾部默认字段也视为已支持字段。
     * @param path 字段路径
     * @return 路径是否存在
     */
    public boolean has(String path) {
        try { resolve(path); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    /**
     * 读取完整精度整数，不使用有符号 long 保存 u64。
     * @param path 整数字段路径
     * @return 无损整数
     */
    public BigInteger integer(String path) {
        Object value = resolve(path);
        if (value instanceof BigInteger integer) return integer;
        throw wrongType(path, "整数");
    }

    /**
     * 读取可安全放入 int 的字段，超出范围时抛错。
     * @param path 整数字段路径
     * @return 精确 int 值
     */
    public int intValue(String path) { return integer(path).intValueExact(); }

    /**
     * 仅在调用方确认范围时将整数转为 long；超出范围时不会静默截断。
     * @param path 整数字段路径
     * @return 精确 long 值
     */
    public long longValueExact(String path) { return integer(path).longValueExact(); }

    /**
     * 读取公钥。
     * @param path 公钥字段路径
     * @return 公钥副本
     */
    public PublicKey publicKey(String path) {
        Object value = resolve(path);
        if (value instanceof PublicKey key) return new PublicKey(key.toByteArray());
        throw wrongType(path, "公钥");
    }

    /**
     * 读取布尔值。
     * @param path 布尔字段路径
     * @return 布尔值
     */
    public boolean flag(String path) {
        Object value = resolve(path);
        if (value instanceof Boolean flag) return flag;
        throw wrongType(path, "布尔值");
    }

    /**
     * 读取 u8 数组或原始参数数据。
     * @param path 字节数组字段路径
     * @return 字节数组副本
     */
    public byte[] bytes(String path) {
        Object value = resolve(path);
        if (value instanceof byte[] bytes) return bytes.clone();
        throw wrongType(path, "字节数组");
    }

    /**
     * 读取结构体数组，例如 bins、ticks、reward_infos、bitmap。
     * @param path 数组字段路径
     * @return 不可修改的数组视图，内部可变值已复制
     */
    @SuppressWarnings("unchecked")
    public List<Object> list(String path) {
        Object value = resolve(path);
        if (value instanceof List<?>) return copyList((List<Object>) value);
        throw wrongType(path, "数组");
    }

    /**
     * 读取嵌套结构体。
     * @param path 结构体字段路径
     * @return 不可修改的字段映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> struct(String path) {
        Object value = resolve(path);
        if (value instanceof Map<?, ?>) return copyMap((Map<String, Object>) value);
        throw wrongType(path, "结构体");
    }

    /** @return 全部已识别字段的副本 */
    public Map<String, Object> fields() { return copyMap(values); }

    /** @return 可缓存的发现信息；池账户更新时仍应检查这些信息是否改变 */
    public Map<String, Object> staticFields() {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String name : staticNames) if (has(name)) selected.put(name, resolve(name));
        return copyMap(selected);
    }

    /** @return 非发现信息且非填充的动态字段，不能用首次读取值长期报价 */
    public Map<String, Object> dynamicFields() {
        Set<String> normalized = new LinkedHashSet<>();
        staticNames.forEach(name -> normalized.add(normalize(name)));
        Map<String, Object> selected = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            String normalizedName = normalize(name);
            if (!normalized.contains(normalizedName) && !normalizedName.contains("padding")
                    && !normalizedName.startsWith("reserved")) selected.put(name, value);
        });
        return copyMap(selected);
    }

    /** @return 使用旧账户兼容零值的尾部字段集合 */
    public Set<String> defaultedFields() { return defaultedFields; }

    /** 解析字段路径，统一兼容官方下划线字段和 Java 驼峰字段。 */
    private Object resolve(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("字段路径不能为空");
        String[] parts = path.replace("[", ".").replace("]", "").split("\\.");
        Object current = values;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                if (map.containsKey(part)) current = map.get(part);
                else {
                    String wanted = normalize(part);
                    String key = map.keySet().stream().map(Object::toString)
                            .filter(candidate -> normalize(candidate).equals(wanted)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("不存在字段：" + path));
                    current = map.get(key);
                }
            } else if (current instanceof List<?> list) {
                int index;
                try { index = Integer.parseInt(part); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("数组下标无效：" + path, e); }
                if (index < 0 || index >= list.size()) throw new IllegalArgumentException("数组下标越界：" + path);
                current = list.get(index);
            } else throw new IllegalArgumentException("路径不是结构体或数组：" + path);
        }
        return current;
    }

    /** 归一化协议字段名。 */
    static String normalize(String name) { return name.replace("_", "").toLowerCase(Locale.ROOT); }

    /** 生成明确的字段类型错误。 */
    private IllegalArgumentException wrongType(String path, String type) {
        return new IllegalArgumentException(accountType + " 的字段 " + path + " 不是" + type);
    }

    /** 深复制映射中的可变值，并禁止修改映射结构。 */
    static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    /** 深复制数组中的可变值，并禁止修改数组结构。 */
    static List<Object> copyList(List<?> source) {
        List<Object> copy = new ArrayList<>(source.size());
        source.forEach(value -> copy.add(copyValue(value)));
        return Collections.unmodifiableList(copy);
    }

    /** 复制原始字节和公钥，数值对象可安全共享。 */
    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof PublicKey key) return new PublicKey(key.toByteArray());
        if (value instanceof Map<?, ?> map) return copyMap((Map<String, Object>) map);
        if (value instanceof List<?> list) return copyList(list);
        if (value instanceof EnumValue item) return new EnumValue(item.variant(), item.index(), item.fields());
        return value;
    }
}
