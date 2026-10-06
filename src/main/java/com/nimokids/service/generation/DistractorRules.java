package com.nimokids.service.generation;

import com.nimokids.exception.InvalidDistractorRulesException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and validates {@code game_questions.metadata.distractor_rules}:
 *
 * <pre>
 * { "distractor_rules": [ { "match": { "category": "animal", "can_fly": false }, "count": 3 } ] }
 * </pre>
 *
 * Several pools are allowed (e.g. 2 close + 1 easy distractor); the counts must add up to the number of wrong
 * answers. A match is a non-empty flat object of scalar tags: there is no syntax for negation on purpose.
 */
public final class DistractorRules {

    public static final String KEY = "distractor_rules";
    public static final String CORRECT_MATCH_KEY = "correct_match";
    private static final Set<String> RULE_FIELDS = Set.of("match", "count");

    private DistractorRules() {
    }

    public static List<DistractorRule> parse(Map<String, Object> questionMetadata, int expectedTotal) {
        Object raw = questionMetadata == null ? null : questionMetadata.get(KEY);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new InvalidDistractorRulesException("metadata." + KEY + " must be a non-empty array");
        }

        List<DistractorRule> rules = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < list.size(); i++) {
            String at = KEY + "[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> ruleMap)) {
                throw new InvalidDistractorRulesException(at + " must be an object");
            }
            for (Object field : ruleMap.keySet()) {
                if (!RULE_FIELDS.contains(String.valueOf(field))) {
                    throw new InvalidDistractorRulesException(at + " has an unknown field: " + field);
                }
            }
            Map<String, Object> match = parseMatch(ruleMap.get("match"), at);
            int count = parseCount(ruleMap.get("count"), at);
            rules.add(new DistractorRule(match, count));
            total += count;
        }
        if (total != expectedTotal) {
            throw new InvalidDistractorRulesException(
                    KEY + " counts add up to " + total + " but exactly " + expectedTotal + " wrong answers are needed");
        }
        return rules;
    }

    /**
     * The condition the question asks for, e.g. {"habitat":"water"} for "Which animal lives in water?". Every wrong
     * answer is checked against it, so a pool that is too broad can never put a second right answer on screen.
     */
    public static Map<String, Object> parseCorrectMatch(Map<String, Object> questionMetadata) {
        Object raw = questionMetadata == null ? null : questionMetadata.get(CORRECT_MATCH_KEY);
        if (raw == null) {
            throw new InvalidDistractorRulesException("metadata." + CORRECT_MATCH_KEY
                    + " is required: it states what makes an answer correct, so wrong answers can be checked against it");
        }
        return parseMatch(raw, "metadata." + CORRECT_MATCH_KEY, "");
    }

    private static Map<String, Object> parseMatch(Object raw, String at) {
        return parseMatch(raw, at, ".match");
    }

    private static Map<String, Object> parseMatch(Object raw, String at, String suffix) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new InvalidDistractorRulesException(at + suffix + " must be a non-empty object of tags");
        }
        Map<String, Object> match = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new InvalidDistractorRulesException(at + suffix + " has a blank tag name");
            }
            Object value = entry.getValue();
            boolean scalar = value instanceof String || value instanceof Boolean || value instanceof Number;
            if (!scalar) {
                throw new InvalidDistractorRulesException(
                        at + suffix + "." + key + " must be a string, number or boolean (flat explicit tags only)");
            }
            match.put(key, value);
        }
        return match;
    }

    private static int parseCount(Object raw, String at) {
        if (!(raw instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new InvalidDistractorRulesException(at + ".count must be an integer");
        }
        int count = number.intValue();
        if (count < 1) {
            throw new InvalidDistractorRulesException(at + ".count must be at least 1");
        }
        return count;
    }
}
