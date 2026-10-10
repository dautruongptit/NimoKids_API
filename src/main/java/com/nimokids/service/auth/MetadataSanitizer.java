package com.nimokids.service.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Keeps secrets out of security_events.metadata: drops secret-looking keys and token-looking values. */
public final class MetadataSanitizer {

    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(token|secret|password|passwd|cookie|authorization|code_verifier|credential).*");
    private static final Pattern JWT_LIKE = Pattern.compile("^[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}$");
    private static final Pattern LONG_OPAQUE = Pattern.compile("^[A-Za-z0-9_-]{40,}$");
    private static final int MAX_VALUE_LENGTH = 300;

    private MetadataSanitizer() {
    }

    public static Map<String, Object> clean(Map<String, Object> metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metadata == null) {
            return out;
        }
        metadata.forEach((key, value) -> {
            if (key == null || SECRET_KEY.matcher(key).matches() || value == null) {
                return;
            }
            if (value instanceof String text) {
                if (JWT_LIKE.matcher(text).matches() || LONG_OPAQUE.matcher(text).matches()) {
                    return;
                }
                out.put(key, text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text);
            } else if (value instanceof Number || value instanceof Boolean) {
                out.put(key, value);
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inner = (Map<String, Object>) nested;
                out.put(key, clean(inner));
            } else {
                out.put(key, String.valueOf(value));
            }
        });
        return out;
    }
}
