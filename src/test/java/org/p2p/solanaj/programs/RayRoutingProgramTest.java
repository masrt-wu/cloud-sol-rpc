package org.p2p.solanaj.programs;

import org.bitcoinj.core.Base58;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.ray.RayRouteCpmmClmmPoolInfo;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RayRoutingProgramTest {

    @Test
    void reproducesObservedStandardCpmmToClmmSell() {
        TransactionInstruction instruction = RayRoutingProgram.makeCpmmToClmmSellInstruction(
                sampleInfo(),
                20_651_241_023L,
                9_563_239L
        );

        assertEquals(RayRoutingProgram.PROGRAM_ID, instruction.getProgramId());
        assertArrayEquals(Base58.decode("18ttwGeYV4549twELxBwKu1"), instruction.getData());
        assertEquals(List.of(
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "11111111111111111111111111111111",
                "ProCXqRcXJjoUd1RNoo28bSizAA6EEqt9wURZYPDc5u",
                "GwWRXGSxn6RDWrcMUkvdyT8R4T91sbPdQXAHasvfQc1T",
                "ETRpp8jWpMzySp5M9mSJCsPwu9nGEUMKnjRAb1XNmRvy",
                "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C",
                "3MEmRLpt1K9eNQwh4vUP43vMazuYrdsYYqngnRmvhUVz",
                "AeDgbAv64t53GiBPemV2geVB1pZkdBLPumjn25HWXpUo",
                "XsoCS1TfEyfFhfvj8EtZ528L3CaKBDBRqRapnBbDF2W",
                "GpMZbSM2GgvTKHJirzeGfMFoaZ8UR2X7F4v8vHTvxFbL",
                "CRRS5ieQmBrZjWhcj99JuGrT5tyuWDaGAXLXLFjbAtjQ",
                "Bkyf63H6KJBwtVMaGVofZKNg88u5U3SYh3YocDfex2c5",
                "AE6RzjHMbWhVU4zYDSQgi4PC3woZho7fb7AdFvpJRiTs",
                "BJxopPfjYADu2962FvKjBRCadnGnNuTVUed2XYpLuUHY",
                "8eohdmExzvw7uytuZuBuFwVv8WZ1qqmjQcmBLXV8Qoqk",
                "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
                "ETRpp8jWpMzySp5M9mSJCsPwu9nGEUMKnjRAb1XNmRvy",
                "XsoCS1TfEyfFhfvj8EtZ528L3CaKBDBRqRapnBbDF2W",
                "So11111111111111111111111111111111111111112",
                "E64NGkDLLCdQ2yFNPcavaKptrEgmiQaNykUuLC1Qgwyp",
                "BS9uyGV6XmNnPkM4f3xgxCdQEaFv7RSKs6fwrpvYHxfL",
                "AixNGcZ8AUTqkt4HjMhxniQgHoE5APq7WEnig8ksWwGS",
                "DN9GrX1nSLWL97FiQHZbcVhkPahRgX8tqZsCq3pAHcip",
                "A8GKzV9BV8u3wLdj9e3gFiVSXYpxB6man6RX3b76NGZU",
                "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
                "28L57tcduzxDx8rY5bKGtuXWYuVrchD4NKQYypECHFyG",
                "6vt9CzvWfAu8piEXHKnmcvWP4i677JMHLPjrtU5GMtqv"
        ), instruction.getKeys().stream().map(meta -> meta.getPublicKey().toBase58()).toList());

        Set<Integer> readonlyIndexes = Set.of(0, 1, 2, 3, 7, 17, 26);
        List<AccountMeta> keys = instruction.getKeys();
        assertEquals(29, keys.size());
        for (int i = 0; i < keys.size(); i++) {
            assertEquals(i == 4, keys.get(i).isSigner(), "unexpected signer flag at index " + i);
            assertEquals(!readonlyIndexes.contains(i), keys.get(i).isWritable(), "unexpected writable flag at index " + i);
        }
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () ->
                RayRoutingProgram.makeCpmmToClmmSellInstruction(sampleInfo(), 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                RayRoutingProgram.makeCpmmToClmmSellInstruction(sampleInfo(), 1, -1));
    }

    private static RayRouteCpmmClmmPoolInfo sampleInfo() {
        RayRouteCpmmClmmPoolInfo info = new RayRouteCpmmClmmPoolInfo();
        info.setPayer("ProCXqRcXJjoUd1RNoo28bSizAA6EEqt9wURZYPDc5u");
        info.setSourceTokenAccount("GwWRXGSxn6RDWrcMUkvdyT8R4T91sbPdQXAHasvfQc1T");
        info.setRouteTokenAccount("3MEmRLpt1K9eNQwh4vUP43vMazuYrdsYYqngnRmvhUVz");
        info.setDestinationTokenAccount("ETRpp8jWpMzySp5M9mSJCsPwu9nGEUMKnjRAb1XNmRvy");
        info.setInputMint("AeDgbAv64t53GiBPemV2geVB1pZkdBLPumjn25HWXpUo");
        info.setRouteMint("XsoCS1TfEyfFhfvj8EtZ528L3CaKBDBRqRapnBbDF2W");
        info.setOutputMint("So11111111111111111111111111111111111111112");
        info.setCpmmProgram("CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C");
        info.setCpmmAuthority("GpMZbSM2GgvTKHJirzeGfMFoaZ8UR2X7F4v8vHTvxFbL");
        info.setCpmmConfig("CRRS5ieQmBrZjWhcj99JuGrT5tyuWDaGAXLXLFjbAtjQ");
        info.setCpmmPool("Bkyf63H6KJBwtVMaGVofZKNg88u5U3SYh3YocDfex2c5");
        info.setCpmmInputVault("AE6RzjHMbWhVU4zYDSQgi4PC3woZho7fb7AdFvpJRiTs");
        info.setCpmmOutputVault("BJxopPfjYADu2962FvKjBRCadnGnNuTVUed2XYpLuUHY");
        info.setCpmmObservation("8eohdmExzvw7uytuZuBuFwVv8WZ1qqmjQcmBLXV8Qoqk");
        info.setClmmProgram("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");
        info.setClmmAmmConfig("E64NGkDLLCdQ2yFNPcavaKptrEgmiQaNykUuLC1Qgwyp");
        info.setClmmPool("BS9uyGV6XmNnPkM4f3xgxCdQEaFv7RSKs6fwrpvYHxfL");
        info.setClmmInputVault("AixNGcZ8AUTqkt4HjMhxniQgHoE5APq7WEnig8ksWwGS");
        info.setClmmOutputVault("DN9GrX1nSLWL97FiQHZbcVhkPahRgX8tqZsCq3pAHcip");
        info.setClmmObservation("A8GKzV9BV8u3wLdj9e3gFiVSXYpxB6man6RX3b76NGZU");
        info.setMemoProgram("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
        info.setClmmRemainingAccounts(List.of(
                "28L57tcduzxDx8rY5bKGtuXWYuVrchD4NKQYypECHFyG",
                "6vt9CzvWfAu8piEXHKnmcvWP4i677JMHLPjrtU5GMtqv"
        ));
        return info;
    }
}
