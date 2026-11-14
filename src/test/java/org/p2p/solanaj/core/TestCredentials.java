package org.p2p.solanaj.core;

import org.junit.jupiter.api.Assumptions;
import org.p2p.solanaj.utils.Env;

/** Isolated historical fixtures; never falls back to the configured trading wallet. */
public final class TestCredentials {
    private TestCredentials() { }

    public static String require(String name) {
        String value = Env.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), "Configure fixture " + name);
        return value;
    }

    public static Account account() {
        return Account.fromBase58PrivateKey(require("TEST_ACCOUNT_PRIVATE_KEY_BASE58"));
    }
}
