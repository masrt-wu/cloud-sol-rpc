package org.p2p.solanaj.programs.parse;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.router.pda.MeteoraDlmmLbPairParser;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 只使用内存中的账户字节验证 LbPair 对象转换，不访问 RPC 或钱包。 */
class MeteoraDlmmLbPairAdapterTest {
    /** 覆盖旧解析器的全部目标字段，同时验证无符号和有符号数值不会被截断。 */
    @Test void convertsEveryLbPairFieldWithoutReparsingBytes() throws Exception {
        byte[] bytes = sampleLbPair();
        DexAccountData parsed = DexParsers.parsePool(MeteoraDlmmParser.PROGRAM_ID, bytes);
        MeteoraDlmmLbPairParser.LbPair converted = MeteoraDlmmLbPairParser.fromDexAccountData(parsed);
        MeteoraDlmmLbPairParser.LbPair direct = MeteoraDlmmLbPairParser.parse(bytes);

        for (RecordComponent component : MeteoraDlmmLbPairParser.LbPair.class.getRecordComponents()) {
            Object expected = component.getAccessor().invoke(direct);
            Object actual = component.getAccessor().invoke(converted);
            assertTrue(Objects.deepEquals(expected, actual), "字段不一致：" + component.getName());
        }
        assertEquals(4_294_967_295L, converted.parameters().variableFeeControl());
        assertEquals(4_294_967_294L, converted.variableParameters().volatilityAccumulator());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), converted.protocolFee().amountX());
        assertEquals(BigInteger.ONE.shiftLeft(127).add(BigInteger.valueOf(3)), converted.rewardInfos().get(0).rewardRate());
        assertEquals(-2L, converted.activeBinArrayIndex());
        assertEquals(DexPda.TOKEN_2022_PROGRAM, converted.tokenProgramX());

        // 目标记录的数组允许调用方改动，但不能反向修改通用解析结果。
        converted.binStepSeed()[0] = 0;
        converted.binArrayBitmap()[8] = BigInteger.ZERO;
        assertEquals(33, Byte.toUnsignedInt(parsed.bytes("bin_step_seed")[0]));
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
                parsed.integer("bin_array_bitmap[8]"));
    }

    /** 非 LbPair 类型不能静默被转换为 Meteora 对象。 */
    @Test void rejectsWrongAccountType() {
        DexAccountData other = new DexAccountData("Whirlpool", 0, Map.of(), Set.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> MeteoraDlmmLbPairParser.fromDexAccountData(other));
        assertThrows(IllegalArgumentException.class, () -> MeteoraDlmmLbPairParser.fromDexAccountData(null));
    }

    /** 按官方固定偏移生成一份测试账户，不依赖两个生产解析器生成输入。 */
    private static byte[] sampleLbPair() throws Exception {
        byte[] bytes = new byte[904];
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest("account:LbPair".getBytes(StandardCharsets.US_ASCII));
        System.arraycopy(hash, 0, bytes, 0, 8);
        put(bytes, 8, BigInteger.valueOf(599), 2);
        put(bytes, 16, BigInteger.valueOf(4_294_967_295L), 4);
        put(bytes, 24, BigInteger.valueOf(-1234), 4);
        put(bytes, 28, BigInteger.valueOf(1234), 4);
        put(bytes, 40, BigInteger.valueOf(4_294_967_294L), 4);
        put(bytes, 44, BigInteger.valueOf(987), 4);
        put(bytes, 48, BigInteger.valueOf(-567), 4);
        put(bytes, 56, BigInteger.valueOf(-123456), 8);
        bytes[72] = (byte) 255;
        bytes[73] = 33; bytes[74] = 1;
        bytes[75] = 2;
        put(bytes, 76, BigInteger.valueOf(-71), 4);
        put(bytes, 80, BigInteger.valueOf(64), 2);
        bytes[82] = 1; bytes[83] = 1; bytes[84] = 4; bytes[85] = 5;
        bytes[86] = 1; bytes[87] = 1;
        putKey(bytes, 88, 1); putKey(bytes, 120, 2);
        putKey(bytes, 152, 3); putKey(bytes, 184, 4);
        BigInteger u64Max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        put(bytes, 216, u64Max, 8);
        put(bytes, 224, BigInteger.valueOf(12345), 8);
        putKey(bytes, 264, 5); putKey(bytes, 296, 6); putKey(bytes, 328, 7);
        put(bytes, 360, BigInteger.valueOf(1000), 8);
        put(bytes, 368, BigInteger.valueOf(2000), 8);
        put(bytes, 376, BigInteger.ONE.shiftLeft(127).add(BigInteger.valueOf(3)), 16);
        put(bytes, 392, BigInteger.valueOf(3000), 8);
        put(bytes, 400, BigInteger.valueOf(4000), 8);
        putKey(bytes, 408, 8); putKey(bytes, 440, 9); putKey(bytes, 472, 10);
        putKey(bytes, 552, 11);
        put(bytes, 648, u64Max, 8);
        put(bytes, 712, BigInteger.valueOf(-123456789), 8);
        putKey(bytes, 752, 12); putKey(bytes, 784, 13);
        put(bytes, 816, u64Max, 8);
        put(bytes, 824, BigInteger.valueOf(555), 8);
        putKey(bytes, 848, 14);
        bytes[880] = 1; bytes[881] = 0; bytes[882] = 3;
        bytes[883] = 55; bytes[903] = 66;
        return bytes;
    }

    /** 写入固定长度的小端整数，测试含负数时也使用链上的二补码。 */
    private static void put(byte[] data, int offset, BigInteger value, int length) {
        for (int i = 0; i < length; i++) data[offset + i] = value.shiftRight(i * 8).byteValue();
    }

    /** 写入确定性的公开测试公钥，不包含任何私钥。 */
    private static void putKey(byte[] data, int offset, int marker) {
        byte[] key = new byte[32]; key[0] = (byte) marker;
        System.arraycopy(new PublicKey(key).toByteArray(), 0, data, offset, 32);
    }
}
