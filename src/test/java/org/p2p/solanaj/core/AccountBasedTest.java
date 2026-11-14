package org.p2p.solanaj.core;

import org.p2p.solanaj.utils.Env;
import org.junit.jupiter.api.BeforeAll;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class AccountBasedTest {

    public static Account testAccount;
    public static PublicKey solDestination;
    public static PublicKey usdcSource;
    public static PublicKey usdcDestination;
    public static final Logger LOGGER = Logger.getLogger(AccountBasedTest.class.getName());

    @BeforeAll
    public static void setup() {
        testAccount = Env.account();

        // Read test.solana.pubkey and test.solana.pubkey.source.usdc
        try (InputStream input = new FileInputStream("solanaj.properties")) {
            Properties properties = new Properties();
            properties.load(input);

            solDestination = new PublicKey(properties.getProperty("test.solana.pubkey"));
            usdcSource = new PublicKey(properties.getProperty("test.solana.pubkey.source.usdc"));
            usdcDestination = new PublicKey(properties.getProperty("test.solana.pubkey.destination.usdc"));
        } catch (IOException ex) {
            LOGGER.warning("Unable to read solanaj.properties - tests may fail");
        }
    }

}
