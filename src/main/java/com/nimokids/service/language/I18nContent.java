package com.nimokids.service.language;

import java.util.Map;

/**
 * Reads the {@code i18n} jsonb of question_templates and answer_items:
 * {@code {"vi": {"text": "...", "audio": "..."}, "en": {"text": "...", "audio": "..."}}}.
 * A missing language, a missing field or a blank value all read as {@code null}.
 */
public final class I18nContent {

    public static final String EN = "en";
    public static final String VI = "vi";

    private I18nContent() {
    }

    public static String text(Map<String, Map<String, String>> i18n, String language) {
        return field(i18n, language, "text");
    }

    public static String audio(Map<String, Map<String, String>> i18n, String language) {
        return field(i18n, language, "audio");
    }

    private static String field(Map<String, Map<String, String>> i18n, String language, String field) {
        if (i18n == null) {
            return null;
        }
        Map<String, String> entry = i18n.get(language);
        if (entry == null) {
            return null;
        }
        String value = entry.get(field);
        return value == null || value.isBlank() ? null : value;
    }
}
