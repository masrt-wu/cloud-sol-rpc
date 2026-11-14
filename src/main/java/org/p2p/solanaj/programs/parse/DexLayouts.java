package org.p2p.solanaj.programs.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.p2p.solanaj.core.PublicKey;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 从随包发布的官方布局快照编译读取器；仅首次使用加载资源，不在线下载布局。 */
final class DexLayouts {
    /** 每个协议编译一次布局；后续账户更新直接使用编译后的读取器。 */
    private static final Map<String, DexLayouts> CACHE = new ConcurrentHashMap<>();
    /** 防止损坏账户中的向量长度触发过量分配。 */
    private static final int MAX_ITEMS = 100_000;
    private final PublicKey programId;
    private final Map<String, JsonNode> definitions = new HashMap<>();
    private final Map<String, Reader> readers = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();

    /** 一个类型的读取逻辑及其固定大小；负数表示可变长。 */
    private record Reader(int size, Decoder decoder) { Object read(Cursor cursor) { return decoder.read(cursor); } }
    /** 一个账户的判别符、字段和兼容规则。 */
    private record Account(String name, byte[] discriminator, List<Field> fields, int optionalFrom) { }
    /** 已编译字段。 */
    private record Field(String name, Reader reader) { }
    /** 读取函数。 */
    private interface Decoder { Object read(Cursor cursor); }

    private DexLayouts(String protocol) {
        String resource = "/org/p2p/solanaj/programs/parse/layouts/" + protocol + ".json";
        try (InputStream stream = DexLayouts.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("找不到账户布局资源：" + resource);
            JsonNode schema = new ObjectMapper().readTree(stream);
            programId = new PublicKey(schema.get("programId").asText());
            schema.get("types").forEach(type -> definitions.put(type.get("name").asText(), type.get("type")));
            for (JsonNode account : schema.get("accounts")) {
                String name = account.get("name").asText();
                List<Field> fields = fields(definitions.get(name).get("fields"));
                byte[] discriminator;
                if (account.has("discriminator")) {
                    JsonNode bytes = account.get("discriminator");
                    discriminator = new byte[bytes.size()];
                    for (int i = 0; i < bytes.size(); i++) discriminator[i] = (byte) bytes.get(i).asInt();
                } else discriminator = Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                        .digest(("account:" + account.get("anchorName").asText()).getBytes(StandardCharsets.UTF_8)), 8);
                int optionalFrom = fields.size();
                if (account.has("optionalTrailingAfter")) {
                    String after = account.get("optionalTrailingAfter").asText();
                    optionalFrom = -1;
                    for (int i = 0; i < fields.size(); i++) if (fields.get(i).name().equals(after)) optionalFrom = i + 1;
                    if (optionalFrom < 0) throw new IllegalStateException("旧账户兼容字段不存在：" + after);
                }
                accounts.put(name, new Account(name, discriminator, fields, optionalFrom));
            }
            if (schema.has("internalTypes")) for (JsonNode internal : schema.get("internalTypes")) {
                String name = internal.asText();
                List<Field> fields = fields(definitions.get(name).get("fields"));
                readers.put(name, new Reader(totalSize(fields.stream().map(Field::reader).toList()),
                        cursor -> readStruct(fields, cursor)));
            }
        } catch (Exception e) { throw new IllegalStateException("账户布局初始化失败：" + protocol, e); }
    }

    /** 根据协议和类型解析原始账户数据，可选校验链上 owner。 */
    static DexAccountData parse(String protocol, String accountName, PublicKey actualOwner, byte[] data,
                                String... staticNames) {
        DexLayouts layout = CACHE.computeIfAbsent(protocol, DexLayouts::new);
        if (actualOwner != null && !layout.programId.equals(actualOwner))
            throw new IllegalArgumentException("账户所属程序不匹配；预期 " + layout.programId + "，实际 " + actualOwner);
        Account account = layout.accounts.get(accountName);
        if (account == null) throw new IllegalArgumentException("未知账户类型：" + accountName);
        Cursor cursor = new Cursor(data);
        byte[] discriminator = cursor.bytes(account.discriminator().length);
        if (!Arrays.equals(account.discriminator(), discriminator))
            throw new IllegalArgumentException(accountName + " 账户判别符不匹配");
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> defaulted = new HashSet<>();
        for (int i = 0; i < account.fields().size(); i++) {
            Field field = account.fields().get(i);
            cursor.path = accountName + "." + field.name();
            if (cursor.remaining() == 0 && i >= account.optionalFrom()) {
                if (field.reader().size() < 0) throw new IllegalArgumentException("不支持可变长尾部默认字段");
                values.put(field.name(), field.reader().read(new Cursor(new byte[field.reader().size()])));
                defaulted.add(field.name());
            } else values.put(field.name(), field.reader().read(cursor));
        }
        return new DexAccountData(accountName, cursor.position, values, Set.of(staticNames), defaulted);
    }

    /** 读取不带账户判别符的内部参数结构，如 DAMM v2 费用调度器。 */
    static DexAccountData parseType(String protocol, String typeName, byte[] data) {
        DexLayouts layout = CACHE.computeIfAbsent(protocol, DexLayouts::new);
        Reader reader = layout.readers.get(typeName);
        if (reader == null) throw new IllegalArgumentException("未编译的内部结构：" + typeName);
        Cursor cursor = new Cursor(data);
        Object value = reader.read(cursor);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("目标类型不是结构体");
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, item) -> values.put(key.toString(), item));
        return new DexAccountData(typeName, cursor.position, values, Set.of(), Set.of());
    }

    /** 将官方字段列表预编译成读取器。 */
    private List<Field> fields(JsonNode fields) {
        List<Field> result = new ArrayList<>();
        for (JsonNode field : fields) result.add(new Field(field.get("name").asText(), compile(field.get("type"))));
        return List.copyOf(result);
    }

    /** 编译 Borsh 和显式填充的零拷贝布局类型，不使用运行时反射。 */
    private Reader compile(JsonNode type) {
        if (type.isTextual()) {
            String primitive = type.asText();
            if (primitive.matches("[ui](8|16|32|64|128|256)")) {
                int size = Integer.parseInt(primitive.substring(1)) / 8;
                boolean signed = primitive.charAt(0) == 'i';
                return new Reader(size, cursor -> cursor.integer(size, signed));
            }
            return switch (primitive) {
                case "pubkey", "publicKey" -> new Reader(32, cursor -> new PublicKey(cursor.bytes(32)));
                case "bool" -> new Reader(1, cursor -> {
                    int value = cursor.u8();
                    if (value > 1) throw new IllegalArgumentException("布尔字段编码无效：" + cursor.path);
                    return value == 1;
                });
                case "bytes" -> new Reader(-1, cursor -> cursor.bytes(cursor.count(1)));
                case "string" -> new Reader(-1, cursor -> new String(cursor.bytes(cursor.count(1)), StandardCharsets.UTF_8));
                default -> throw new IllegalArgumentException("尚不支持的布局类型：" + primitive);
            };
        }
        if (type.has("defined")) {
            JsonNode definition = type.get("defined");
            String name = definition.isTextual() ? definition.asText() : definition.get("name").asText();
            Reader cached = readers.get(name);
            if (cached != null) return cached;
            JsonNode body = definitions.get(name);
            if (body == null) throw new IllegalStateException("缺少官方类型定义：" + name);
            Reader reader;
            if ("struct".equals(body.get("kind").asText())) {
                List<Field> fields = fields(body.get("fields"));
                int size = totalSize(fields.stream().map(Field::reader).toList());
                reader = new Reader(size, cursor -> readStruct(fields, cursor));
            } else if ("enum".equals(body.get("kind").asText())) {
                JsonNode variants = body.get("variants");
                List<List<Reader>> arguments = new ArrayList<>();
                List<List<String>> argumentNames = new ArrayList<>();
                for (JsonNode variant : variants) {
                    List<Reader> parameters = new ArrayList<>();
                    List<String> names = new ArrayList<>();
                    if (variant.has("fields")) for (JsonNode field : variant.get("fields")) {
                        boolean named = field.has("name") && field.has("type");
                        parameters.add(compile(named ? field.get("type") : field));
                        names.add(named ? field.get("name").asText() : "");
                    }
                    arguments.add(parameters); argumentNames.add(names);
                }
                reader = new Reader(-1, cursor -> {
                    int index = cursor.u8();
                    if (index >= variants.size()) throw new IllegalArgumentException("枚举分支无效：" + cursor.path);
                    List<Object> parameters = new ArrayList<>();
                    Map<String, Object> named = new LinkedHashMap<>();
                    for (int i = 0; i < arguments.get(index).size(); i++) {
                        Object value = arguments.get(index).get(i).read(cursor);
                        if (argumentNames.get(index).get(i).isEmpty()) parameters.add(value);
                        else named.put(argumentNames.get(index).get(i), value);
                    }
                    if (!named.isEmpty()) parameters.add(named);
                    return new DexAccountData.EnumValue(variants.get(index).get("name").asText(), index, parameters);
                });
            } else throw new IllegalArgumentException("尚不支持的复合类型：" + name);
            readers.put(name, reader);
            return reader;
        }
        if (type.has("array")) {
            JsonNode array = type.get("array");
            int count = array.get(1).asInt(-1);
            if (count < 0 || count > MAX_ITEMS) throw new IllegalArgumentException("固定数组长度无效");
            Reader item = compile(array.get(0));
            int size = item.size() < 0 ? -1 : Math.multiplyExact(count, item.size());
            if (array.get(0).isTextual() && "u8".equals(array.get(0).asText()))
                return new Reader(count, cursor -> cursor.bytes(count));
            return new Reader(size, cursor -> readItems(cursor, item, count));
        }
        if (type.has("vec")) {
            Reader item = compile(type.get("vec"));
            return new Reader(-1, cursor -> readItems(cursor, item, cursor.count(Math.max(1, item.size()))));
        }
        if (type.has("option")) {
            Reader item = compile(type.get("option"));
            return new Reader(-1, cursor -> {
                int present = cursor.u8();
                if (present > 1) throw new IllegalArgumentException("可选字段标记无效：" + cursor.path);
                return present == 0 ? null : item.read(cursor);
            });
        }
        throw new IllegalArgumentException("尚不支持的类型表达式：" + type);
    }

    /** 读取结构体；所有偏移由官方字段类型和显式填充计算。 */
    private static Map<String, Object> readStruct(List<Field> fields, Cursor cursor) {
        Map<String, Object> result = new LinkedHashMap<>();
        String parent = cursor.path;
        for (Field field : fields) {
            cursor.path = parent + "." + field.name();
            result.put(field.name(), field.reader().read(cursor));
        }
        cursor.path = parent;
        return result;
    }

    /** 读取数组，并先校验固定大小的最低字节需求。 */
    private static List<Object> readItems(Cursor cursor, Reader item, int count) {
        if (item.size() >= 0) cursor.require(Math.multiplyExact(item.size(), count));
        List<Object> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(item.read(cursor));
        return values;
    }

    /** 计算固定结构总大小；可变长结构返回负数。 */
    private static int totalSize(List<Reader> readers) {
        int size = 0;
        for (Reader reader : readers) {
            if (reader.size() < 0) return -1;
            size = Math.addExact(size, reader.size());
        }
        return size;
    }

    /** 带边界检查的顺序小端读取器。 */
    private static final class Cursor {
        private final byte[] data;
        private int position;
        private String path = "账户";
        private Cursor(byte[] data) {
            if (data == null) throw new IllegalArgumentException("账户数据不能为空");
            this.data = data;
        }
        private int remaining() { return data.length - position; }
        private void require(int length) {
            if (length < 0 || length > remaining())
                throw new IllegalArgumentException("账户数据被截断：" + path + "，还需 " + length + " 字节，剩余 " + remaining());
        }
        private byte[] bytes(int length) {
            require(length);
            byte[] value = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return value;
        }
        private int u8() { require(1); return Byte.toUnsignedInt(data[position++]); }
        private BigInteger integer(int length, boolean signed) {
            require(length);
            byte[] bigEndian = new byte[length];
            for (int i = 0; i < length; i++) bigEndian[length - i - 1] = data[position + i];
            position += length;
            return signed ? new BigInteger(bigEndian) : new BigInteger(1, bigEndian);
        }
        private int count(int minimumItemSize) {
            BigInteger value = integer(4, false);
            if (value.compareTo(BigInteger.valueOf(MAX_ITEMS)) > 0)
                throw new IllegalArgumentException("数组长度超出安全上限：" + path);
            int count = value.intValue();
            if ((long) count * minimumItemSize > remaining())
                throw new IllegalArgumentException("数组内容被截断：" + path);
            return count;
        }
    }
}
