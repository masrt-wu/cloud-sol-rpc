package org.p2p.solanaj.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.p2p.solanaj.core.Account;

import static org.junit.jupiter.api.Assertions.*;

class EnvTest {
    @TempDir Path directory;

    @Test
    void readsQuotedJsonExportCommentsAndBomWithoutEvaluatingValues() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "\uFEFF# local settings\n"
                + "export BASE58 = 'test-value' # comment\n"
                + "JSON=\"[1,2,3]\"\n"
                + "URL=https://localhost/path?token=a=b#fragment\n"
                + "EMPTY=\n"
                + "AMOUNT=42 # raw units\n"
                + "LITERAL=${BASE58}\n");
        Map<String, String> values = Env.read(file);
        assertEquals("test-value", values.get("BASE58"));
        assertEquals("[1,2,3]", values.get("JSON"));
        assertEquals("https://localhost/path?token=a=b#fragment", values.get("URL"));
        assertEquals("", values.get("EMPTY"));
        assertEquals("42", values.get("AMOUNT"));
        assertEquals("${BASE58}", values.get("LITERAL"));
    }

    @Test
    void missingFileHasNoConfiguration() {
        assertTrue(Env.read(directory.resolve("absent.env")).isEmpty());
    }

    @Test
    void invalidSyntaxReportsOnlyLineNumber() throws Exception {
        Path file = directory.resolve(".env");
        for (String invalid : new String[]{"missing-equals", "1INVALID=value", "KEY='sensitive", "KEY='sensitive' trailing"}) {
            Files.writeString(file, "# comment\n" + invalid);
            var failure = assertThrows(IllegalStateException.class, () -> Env.read(file));
            assertEquals("Invalid .env syntax at line 2", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    @Test
    void loadsBothSupportedFormatsWithoutUsingLocalWallet() {
        Account generated = new Account();
        byte[] secret = generated.getSecretKey();
        String json = IntStream.range(0, secret.length)
                .mapToObj(i -> Integer.toString(secret[i] & 0xff))
                .collect(Collectors.joining(",", "[", "]"));
        assertEquals(generated.getPublicKey(), Env.account(generated.getPrivateKeyBase58(), "").getPublicKey());
        assertEquals(generated.getPublicKey(), Env.account(null, json).getPublicKey());
    }

    @Test
    void rejectsMissingAmbiguousAndMalformedKeysWithoutEchoingSecrets() {
        assertThrows(IllegalStateException.class, () -> Env.account(null, ""));
        assertThrows(IllegalStateException.class, () -> Env.account("a", "[1]"));
        for (String invalid : new String[]{"sensitive!", "[1,2]", "[" + "256,".repeat(63) + "256]"}) {
            var failure = assertThrows(IllegalStateException.class, () ->
                    Env.account(invalid.startsWith("[") ? null : invalid,
                            invalid.startsWith("[") ? invalid : null));
            assertFalse(failure.getMessage().contains(invalid));
            assertNull(failure.getCause());
        }
    }

    @Test
    void processVariablesTakePriority() {
        System.getenv().forEach((key, value) -> assertEquals(value, Env.get(key)));
    }
}
