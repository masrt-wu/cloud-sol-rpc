package org.p2p.solanaj.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.logging.Logger;

import org.bitcoinj.core.Base58;

public class AccountTest {

    @Test
    public void accountFromSecretKey() {
        byte[] secretKey = Base58
                .decode(TestCredentials.require("TEST_ACCOUNT_PRIVATE_KEY_BASE58"));
        assertEquals("QqCCvshxtqMAL2CVALqiJB7uEeE5mjSPsseQdDzsRUo", new Account(secretKey).getPublicKey().toString());

        assertEquals(64, new Account(secretKey).getSecretKey().length);
    }

    @Test
    public void generateNewAccount() {
        Account account = new Account();
        assertEquals(64, account.getSecretKey().length);
    }

    @Test
    public void fromBip44Mnemonic() {
        Account acc = Account.fromBip44Mnemonic(Arrays.asList(TestCredentials.require("TEST_MNEMONIC_BIP44").trim().split("\\s+")), "");

        assertEquals("G75kGJiizyFNdnvvHxkrBrcwLomGJT2CigdXnsYzrFHv", acc.getPublicKey().toString());
    }

    @Test
    public void fromBip44MnemonicChange() {
        Account acc = Account.fromBip44MnemonicWithChange(Arrays.asList(TestCredentials.require("TEST_MNEMONIC_BIP44").trim().split("\\s+")), "");

        assertEquals("AaXs7cLGcSVAsEt8QxstVrqhLhYN2iGhFNRemwYnHitV", acc.getPublicKey().toString());
    }

    @Test
    public void fromMnemonic() {
        Account acc = Account.fromMnemonic(Arrays.asList(TestCredentials.require("TEST_MNEMONIC_LEGACY").trim().split("\\s+")), "");

        assertEquals("BQWWFhzBdw2vKKBUX17NHeFbCoFQHfRARpdztPE2tDJ", acc.getPublicKey().toString());
    }

    @Test
    public void fromJson() {
        String json = TestCredentials.require("TEST_ACCOUNT_SECRET_KEY_JSON");
        Account acc = Account.fromJson(json);

        assertEquals("2cXAj2TagK3t6rb2CGRwyhF6sTFJgLyzyDGSWBcGd8Go", acc.getPublicKey().toString());
    }

    @Test
    public void fromBip39MnemonicTest() {
        Account account = Account.fromBip39Mnemonic(Arrays.asList(TestCredentials.require("TEST_MNEMONIC_BIP39").trim().split("\\s+")),
                ""
        );

        Logger.getAnonymousLogger().info("Derived pubkey = " + account.getPublicKey().toBase58());
        assertEquals("BeepMww3KwiDeEhEeZmqk4TegvJYNuDERPWm142X6Mx3", account.getPublicKey().toBase58());
    }

    @Test
    public void testAccountEquality() {
        byte[] secretKey = Base58.decode(TestCredentials.require("TEST_ACCOUNT_PRIVATE_KEY_BASE58"));
        Account account1 = new Account(secretKey);
        Account account2 = new Account(secretKey);
        Account account3 = new Account();

        assertEquals(account1.getPublicKey(), account2.getPublicKey());
        assertNotEquals(account1.getPublicKey(), account3.getPublicKey());
    }

    @Test
    public void testInvalidSecretKeyLength() {
        byte[] invalidSecretKey = new byte[63]; // Invalid length
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> new Account(invalidSecretKey));
    }

    @Test
    public void testFromBip44MnemonicWithPassphrase() {
        Account acc = Account.fromBip44Mnemonic(Arrays.asList(TestCredentials.require("TEST_MNEMONIC_BIP44").trim().split("\\s+")),
            TestCredentials.require("TEST_MNEMONIC_PASSPHRASE")
        );

        assertNotNull(acc);
        assertNotEquals("G75kGJiizyFNdnvvHxkrBrcwLomGJT2CigdXnsYzrFHv", acc.getPublicKey().toString());
    }

}
