package org.p2p.solanaj.rpc.types;


import cn.hutool.core.codec.Base58;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import lombok.Data;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Data
public class SplTokenMetaData {

    public String name;
    public String symbol;
    public String uri;

    public String mint;
    public String bondingCurve;

    private String user;

    public String creator;

    public long virtualTokenReservesRaw;
    public long virtualSolReservesRaw;
    public long realTokenReservesRaw;
    public long tokenTotalSupplyRaw;
    public long timestamp;


    private boolean isMayhemMode;

    private boolean isCashbackEnabled;




    public static SplTokenMetaData parse(byte[] rawData)  {

        ByteBuffer buf = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN);
        //System.out.println(Arrays.toString(buf.array()));
        long discriminator = buf.getLong(); // 跳过前 8 字节

        int nameLen = buf.getInt();
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);

        int symbolLen = buf.getInt();
        byte[] symbolBytes = new byte[symbolLen];
        buf.get(symbolBytes);
        String symbol = new String(symbolBytes, StandardCharsets.UTF_8);

        int uriLen = buf.getInt();
        byte[] uriBytes = new byte[uriLen];
        buf.get(uriBytes);
        String uri = new String(uriBytes, StandardCharsets.UTF_8);
        // mint
        byte[] mintBytes = new byte[32];
        buf.get(mintBytes);
        String mint = Base58.encode(mintBytes);

        // bonding curve
        byte[] bondingBytes = new byte[32];
        buf.get(bondingBytes);
        String bondingCurve = Base58.encode(bondingBytes);

        byte[] userBytes = new byte[32];
        buf.get(userBytes);
        String user = Base58.encode(userBytes);

        // creator
        byte[] creatorBytes = new byte[32];
        buf.get(creatorBytes);
        String creator = Base58.encode(creatorBytes);

        long timestamp = buf.getLong();

        long virtualTokenReserves = buf.getLong();
        long virtualSolReserves = buf.getLong();
        long realTokenReserves = buf.getLong();
        long tokenTotalSupply = buf.getLong();
        byte[] tokenProgramByte = new byte[32];

        buf.get(tokenProgramByte);
        String tokenProgram = Base58.encode(tokenProgramByte);

        boolean isMayhemMode = buf.get() != 0;

        boolean isCashbackEnabled = buf.get() != 0;

        SplTokenMetaData info = new SplTokenMetaData();
        info.name = name;
        info.symbol = symbol;
        info.uri = uri;

        info.mint = mint;
        info.bondingCurve = bondingCurve;
        info.user = user;
        info.creator = creator;

        info.virtualTokenReservesRaw = virtualTokenReserves;
        info.virtualSolReservesRaw = virtualSolReserves;
        info.realTokenReservesRaw = realTokenReserves;
        info.tokenTotalSupplyRaw = tokenTotalSupply;

        info.timestamp = timestamp;
        info.isCashbackEnabled = isCashbackEnabled;
        info.isMayhemMode = isMayhemMode;

        return info;
    }
    static String readBorshString(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] data = new byte[len];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }


    public static void main(String[] args) {

        var base64 = Base64.decode("G3KpTd7rY3YTAAAAVGVjaG5vcHJlbmV1ciBBZ2VudAkAAABURUNIQUdFTlRDAAAAaHR0cHM6Ly9pcGZzLmlvL2lwZnMvUW1lZUxFWkhSeHBObzd0ZmdIRHdDOWE1ekhaQ0ttdXRHdDdINmE3Qjc5OHhVV83SNlg53ViGqouBU33y/o8ZGRgDMPcBXWSJT+LcytY/S3gG+5SIWAu/bDKYLbAg2YN/d1Gj0Ewhb52qLrmg0/XD7Bk1QE93tIbNSOs/sY5YkJVaat8ELtX+Xi2TXLefY8PsGTVAT3e0hs1I6z+xjliQlVpq3wQu1f5eLZNct59jsL22aQAAAAAAENhH488DAACsI/wGAAAAAHjF+1HRAgAAgMakfo0DAAbd9uHudY/eGEJdvORszdq2GvxNg7kNJ/69+SjYoYv8AAA=");


        SplTokenMetaData data = parse(base64);
        //System.out.println(data);


        base64 = Base64.decode("F7f4N2DYrGBOdBQrHMoDALoFygYHAAAATtwB34rLAgC6WaYKAAAAAACAxqR+jQMAAGdwleIHO9jU+oE425kHO/rDSgQxjmmX7ydyqjiIieXpAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==");
        //System.out.println(Arrays.toString(base64));
        base64 = Base64.decode("G3KpTd7rY3YPAAAAU25pYmJ1IFRoZSBDcmFiBgAAAFNOSUJCVTkAAABodHRwczovL21ldGEubXdtd213bXdtd213bXdtd213LnVrL21ldGFkYXRhLzllOGZiZjE5Lmpzb27i163ZURJzzJZwh8P8htvsuuILs29SuRKAerJiq/ubtZKedT4sPw4poU93kHRPKhOxPufG11cVg68ni8ZVtDI6YXhyPF7MNc+TmLhNqbaZYhepXS996MlloTkmzyQiCJxZAqfYAhGqBN58zyHtMy7xT7iZ62xrpbT/aKeRqRrQv/24x2oAAAAAABDYR+PPAwDEvQUGAAAAAAB4xftR0QIAAIDGpH6NAwAG3fbh7nWP3hhCXbzkbM3athr8TYO5DSf+vfko2KGL/AAABoH7ikK2/EQw0dq1EyfiXv9HTvfEVe2TYiQ2xSNRrQ/EvQUGAAAAAGQAAAAAAAAAAQ==");

        data = parse(base64);
        System.out.println(data);

    }
}
