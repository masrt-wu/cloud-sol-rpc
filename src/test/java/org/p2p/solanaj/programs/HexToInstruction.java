package org.p2p.solanaj.programs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HexToInstruction {

    public static void main(String[] args) {
        // 十六进制字符串
        String hexString = "098058840c000000006916405c02000000";

        // 转换为字节数组
        byte[] bytes = hexStringToByteArray(hexString);

        // 解析字节数组
        parseInstruction(bytes);
    }

    // 将十六进制字符串转换为字节数组
    public static byte[] hexStringToByteArray(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // 解析指令
    public static void parseInstruction(byte[] bytes) {
        // 创建 ByteBuffer，设置为小端序
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 解析指令编号 (1字节)
        byte instruction = buffer.get();

        // 解析 amountIn (8字节)
        long amountIn = buffer.getLong();

        // 解析 minAmountOut (8字节)
        long minAmountOut = buffer.getLong();

        // 打印解析结果
        //System.out.println("Instruction: " + instruction);
        //System.out.println("Amount In: " + amountIn);
        //System.out.println("Min Amount Out: " + minAmountOut);
    }
}

