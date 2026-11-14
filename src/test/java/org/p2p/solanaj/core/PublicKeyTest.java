package org.p2p.solanaj.core;

import cn.hutool.core.codec.Base58;
import cn.hutool.core.codec.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.p2p.solanaj.core.PublicKey.ProgramDerivedAddress;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.PumpDIumProgram;
import org.p2p.solanaj.programs.TokenProgram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class PublicKeyTest {

    @Test
    public void testInvalidKeys() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PublicKey(new byte[] { 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0 });
            new PublicKey("300000000000000000000000000000000000000000000000000000000000000000000");
            new PublicKey("300000000000000000000000000000000000000000000000000000000000000");
        });
    }

    @Test
    public void validKeys() {
        PublicKey key = new PublicKey(new byte[] { 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, });
        assertEquals("CiDwVBFgWV9E5MvXWoLgnEgn2hK7rJikbvfWavzAQz3", key.toString());

        PublicKey key1 = new PublicKey("CiDwVBFgWV9E5MvXWoLgnEgn2hK7rJikbvfWavzAQz3");
        assertEquals("CiDwVBFgWV9E5MvXWoLgnEgn2hK7rJikbvfWavzAQz3", key1.toBase58());

        PublicKey key2 = new PublicKey("11111111111111111111111111111111");
        assertEquals("11111111111111111111111111111111", key2.toBase58());

        byte[] byteKey = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 1, };
        PublicKey key3 = new PublicKey(byteKey);
        assertArrayEquals(byteKey, new PublicKey(key3.toBase58()).toByteArray());
    }

    @Test
    public void equals() {
        PublicKey key = new PublicKey("11111111111111111111111111111111");
        assertTrue(key.equals(key));

        assertFalse(key.equals(new PublicKey("11111111111111111111111111111112")));
    }

    @Test
    public void readPubkey() throws IOException {
        PublicKey key = new PublicKey("11111111111111111111111111111111");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(1);
        bos.write(key.toByteArray());

        byte[] bytes = bos.toByteArray();
        assertEquals(key.toString(), PublicKey.readPubkey(bytes, 1).toString());
    }

    @Test
    public void createProgramAddress() throws Exception {
        PublicKey programId = new PublicKey("BPFLoader1111111111111111111111111111111111");

        PublicKey programAddress = PublicKey.createProgramAddress(
                Arrays.asList(new PublicKey("SeedPubey1111111111111111111111111111111111").toByteArray()), programId);
        assertTrue(programAddress.equals(new PublicKey("GUs5qLUfsEHkcMB9T38vjr18ypEhRuNWiePW2LoK4E3K")));

        programAddress = PublicKey.createProgramAddress(Arrays.asList("".getBytes(), new byte[] { 1 }), programId);
        assertTrue(programAddress.equals(new PublicKey("3gF2KMe9KiC6FNVBmfg9i267aMPvK37FewCip4eGBFcT")));

        // programAddress = PublicKey.createProgramAddress(Arrays.asList("☉".getBytes()), programId);
        // assertTrue(programAddress.equals(new PublicKey("7ytmC1nT1xY4RfxCV2ZgyA7UakC93do5ZdyhdF3EtPj7")));

        programAddress = PublicKey.createProgramAddress(Arrays.asList("Talking".getBytes(), "Squirrels".getBytes()),
                programId);
        assertTrue(programAddress.equals(new PublicKey("HwRVBufQ4haG5XSgpspwKtNd3PC9GM9m1196uJW36vds")));

        PublicKey programAddress2 = PublicKey.createProgramAddress(Arrays.asList("Talking".getBytes()), programId);
        assertFalse(programAddress.equals(programAddress2));
    }

    @Test
    public void findProgramAddress() throws Exception {
        PublicKey programId = new PublicKey("BPFLoader1111111111111111111111111111111111");

        ProgramDerivedAddress programAddress = PublicKey.findProgramAddress(Arrays.asList("".getBytes()), programId);
        assertTrue(programAddress.getAddress().equals(PublicKey.createProgramAddress(
                Arrays.asList("".getBytes(), new byte[] { (byte) programAddress.getNonce() }), programId)));

    }



    @Test
    public void findProgramAddress1() throws Exception {
        PublicKey programId = new PublicKey("6Cust2JhvweKLh4CVo1dt21s2PJ86uNGkziudpkNPaCj");
        PublicKey programId2 = new PublicKey("BPFLoader1111111111111111111111111111111111");

        ProgramDerivedAddress programAddress = PublicKey.findProgramAddress(
                Arrays.asList(new PublicKey("8VBafTNv1F8k5Bg7DTVwhitw3MGAMTmekHsgLuMJxLC8").toByteArray()), programId);
        assertTrue(programAddress.getAddress().equals(new PublicKey("FGnnqkzkXUGKD7wtgJCqTemU3WZ6yYqkYJ8xoQoXVvUG")));

        ProgramDerivedAddress programAddress2 = PublicKey
                .findProgramAddress(
                        Arrays.asList(new PublicKey("SeedPubey1111111111111111111111111111111111").toByteArray(),
                                new PublicKey("3gF2KMe9KiC6FNVBmfg9i267aMPvK37FewCip4eGBFcT").toByteArray(),
                                new PublicKey("HwRVBufQ4haG5XSgpspwKtNd3PC9GM9m1196uJW36vds").toByteArray()),
                        programId2);
        assertTrue(programAddress2.getAddress().equals(new PublicKey("GXLbx3CbJuTTtJDZeS1PGzwJJ5jGYVEqcXum7472kpUp")));
        assertEquals(programAddress2.getNonce(), 254);
    }

    @Test
    public void testToString() {
        PublicKey key = new PublicKey("CiDwVBFgWV9E5MvXWoLgnEgn2hK7rJikbvfWavzAQz3");
        assertEquals("CiDwVBFgWV9E5MvXWoLgnEgn2hK7rJikbvfWavzAQz3", key.toString());
    }

    @Test
    public void testHashCode() {
        PublicKey key1 = new PublicKey("11111111111111111111111111111111");
        PublicKey key2 = new PublicKey("11111111111111111111111111111111");
        PublicKey key3 = new PublicKey("22222222222222222222222222222222");

        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }

    @Test
    public void testInvalidBase58Key() {
        assertThrows(IllegalArgumentException.class, () -> new PublicKey("InvalidBase58Key"));
    }

    @Test
    public void testFindProgramAddressWithLargeNonce() throws Exception {
        PublicKey programId = new PublicKey("BPFLoader1111111111111111111111111111111111");
        ProgramDerivedAddress pda = PublicKey.findProgramAddress(
            Arrays.asList("LargeNonceTest".getBytes()),
            programId
        );
        assertTrue(pda.getNonce() >= 0 && pda.getNonce() <= 255);
    }


    /**
     * 生成 metadata 状态地址
     */
    @Test
    public void findProgramAddressMet(){
        PublicKey programId = new PublicKey("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s");
        ProgramDerivedAddress pda = PublicKey.findProgramAddress(
                Arrays.asList("metadata".getBytes(),
                        programId.toByteArray(),
                        new PublicKey("34wEF58byddLshYqNA4x6XT6WQGYzyqfhNS8WMVhpump").toByteArray()),
                programId
        );
        //62qc2CNXwrYqQScmEdiZFFAnJR262PxWEuNQtxfafNgV
        //BTEqrDAMGA3UDWwGGSPe75HYCVnjFPM4fDs35UhtA2cp

        System.out.println(pda.getAddress().toBase58());

        // 这个是 BondingCurve 生成规律
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey("7hERXVAqi6MSUxnsTLaV7tRANn95QnjESZL4QHRapump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        //11111111111111111111111111111111

        //global_volume_accumulator

        //Hq2wp8uJ9jCPsYgNHex8RtqdvMPfVGoYwjvF1ATiwn2Y Global Volume Accumulator 地址
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("global_volume_accumulator".getBytes()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        //userVolumeAccumulator
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("user_volume_accumulator".getBytes(),
                        new PublicKey("G6MGREnQXPM2VH4bRcp4SrxT6ADYV3fjvgtszLrnLbKe").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());



        programId = new PublicKey("pfeeUxB6jkeY1Hxd7CsFCAjcbHA9rWtchMGdZ6VojVZ");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("fee_config".getBytes(),
                        new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());


        //3s5zRjSALMvbRMW2y3wotrpFy5qutmZP9ufez62NY7Js Associated User
        programId = new PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
        pda = PublicKey.findProgramAddress(
                Arrays.asList(new PublicKey("G6MGREnQXPM2VH4bRcp4SrxT6ADYV3fjvgtszLrnLbKe").toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey("34wEF58byddLshYqNA4x6XT6WQGYzyqfhNS8WMVhpump").toByteArray()
                ),programId);
        System.out.println(pda.getAddress().toBase58());



        //Associated Bonding Curve
        //7pakZbEBwruT7dPY1xcVmiXnnvpFCDygggSRgV6X1WQX
        //programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList(new PublicKey("Cst5M1yshaVMJ6zHvD7NLW9PBnVSm4BrAVnoGJSrkynF").toByteArray(),
                        new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb").toByteArray(),
                        new PublicKey("FRBeiRn5Vuuh8y7ceRT74R3eQ5cCvCg1Adz53d15pump").toByteArray()
                ),

                programId
        );
        System.out.println(pda.getAddress().toBase58());

        //Cst5M1yshaVMJ6zHvD7NLW9PBnVSm4BrAVnoGJSrkynF
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey("FRBeiRn5Vuuh8y7ceRT74R3eQ5cCvCg1Adz53d15pump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());


        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        //BTEqrDAMGA3UDWwGGSPe75HYCVnjFPM4fDs35UhtA2cp Creator Vault 生成规律 创建人
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("creator-vault".getBytes(),
                        //new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P").toByteArray(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("creator_vault".getBytes(),
                        new PublicKey("2froRECNBhQsJJdnyBkyppMvJkmjiSZrxHNNv62ZB9Ga").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        programId = new PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
        pda = PublicKey.findProgramAddress(
                Arrays.asList(
                        pda.getAddress().toByteArray(),
                        TokenProgram.TOKEN_2022.toByteArray(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()
                ),programId);
        System.out.println(pda.getAddress().toBase58());
        //4Rq6X7Kc7vDLVuTx94HBiPrC2L7f3w3uD7m3NsHqd6RE
        byte[] bytes = new byte[]{99, 114, 101, 97, 116, 111, 114, 45, 118, 97, 117, 108, 116};
        System.out.println(new String(bytes));

        //Remaining Account 参数
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve-v2".getBytes(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());


        //userVolumeAccumulator
        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("user_volume_accumulator".getBytes(),
                        new PublicKey("NSFWgm8M6KfWGosp5YjruY9csHy1eEyK1hyhMTid9Wf").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());


        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList("bonding-curve".getBytes(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());

        programId = new PublicKey("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        pda = PublicKey.findProgramAddress(
                Arrays.asList(
                        "creator-vault".getBytes(),
                        new PublicKey("AuL15wUz2b6rDQ3mSuD3nF1EnEavbUHamGwqSm3Mpump").toByteArray()),
                programId
        );
        System.out.println(pda.getAddress().toBase58());



        PublicKey.ProgramDerivedAddress remainingAccount = PublicKey.findProgramAddress(
                Arrays.asList(
                        "bonding-curve-v2".getBytes(),
                        new PublicKey("5RqFuMojxAbCRPJZF5o5vaGjoDFBkZmmiJcm37YNYG6i").toByteArray()
                ),

                PumpDIumProgram.Program_PUMP
        );
        System.out.println(remainingAccount.getAddress().toBase58());
    }

}
