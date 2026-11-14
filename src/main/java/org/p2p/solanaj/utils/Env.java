package org.p2p.solanaj.utils;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads local configuration from the working directory's .env; process variables take priority. */
public final class Env {
    private Env() { }

    private static final class Local {
        private static final Map<String, String> VALUES = read(Path.of(".env"));
    }

    public static String get(String key) {
        String value = System.getenv(key);
        return value != null ? value : Local.VALUES.get(key);
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " in .env or the process environment");
        }
        return value;
    }

    /** Loads exactly one configured 64-byte Solana keypair, without logging its contents. */
    public static Account account() {
        return account(get("SOLANA_PRIVATE_KEY_BASE58"), get("SOLANA_SECRET_KEY_JSON"));
    }

    static Account account(String base58, String json) {
        boolean hasBase58 = base58 != null && !base58.isBlank();
        boolean hasJson = json != null && !json.isBlank();
        if (hasBase58 == hasJson) {
            throw new IllegalStateException(
                    "Configure exactly one of SOLANA_PRIVATE_KEY_BASE58 or SOLANA_SECRET_KEY_JSON");
        }
        byte[] bytes;
        try {
            if (hasBase58) {
                bytes = Base58.decode(base58.trim());
            } else {
                String value = json.trim();
                if (!value.startsWith("[") || !value.endsWith("]")) {
                    throw new IllegalArgumentException();
                }
                String[] parts = value.substring(1, value.length() - 1).split(",", -1);
                if (parts.length != 64) throw new IllegalArgumentException();
                bytes = new byte[64];
                for (int i = 0; i < parts.length; i++) {
                    int number = Integer.parseInt(parts[i].trim());
                    if (number < 0 || number > 255) throw new IllegalArgumentException();
                    bytes[i] = (byte) number;
                }
            }
            if (bytes.length != 64) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            // Decoder exceptions may contain the supplied secret; deliberately omit the cause.
            throw new IllegalStateException("Invalid Solana keypair: expected 64 bytes in Base58 or JSON format");
        }
        return new Account(bytes);
    }

    static Map<String, String> read(Path path) {
        if (!Files.exists(path)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        try {
            int lineNumber = 0;
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = raw.replaceFirst("^\uFEFF", "").trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int equals = line.indexOf('=');
                if (equals < 1) throw invalidLine(lineNumber);
                String key = line.substring(0, equals).trim();
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) throw invalidLine(lineNumber);
                String value = line.substring(equals + 1).trim();
                if (value.startsWith("\"") || value.startsWith("'")) {
                    char quote = value.charAt(0);
                    int end = value.indexOf(quote, 1);
                    if (end < 0) throw invalidLine(lineNumber);
                    String tail = value.substring(end + 1).trim();
                    if (!tail.isEmpty() && !tail.startsWith("#")) throw invalidLine(lineNumber);
                    value = value.substring(1, end);
                } else {
                    value = value.replaceFirst("\\s+#.*$", "").trim();
                }
                values.put(key, value);
            }
            return Map.copyOf(values);
        } catch (IOException unreadable) {
            throw new IllegalStateException("Unable to read .env configuration");
        }
    }

    private static IllegalStateException invalidLine(int line) {
        return new IllegalStateException("Invalid .env syntax at line " + line);
    }
}
