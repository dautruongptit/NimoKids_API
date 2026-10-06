package com.nimokids.service.generation;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Java-side check of "does this item have these tags", used to verify generated options. It is the safety net behind
 * the SQL pools: a wrong answer must never be able to satisfy the question's own {@code correct_match}.
 *
 * <p>An item tag may be a scalar ({@code "habitat":"water"}) or a list of scalars ({@code "habitat":["farm","water"]},
 * for animals that live in several places).
 */
public final class TagMatcher {

    private TagMatcher() {
    }

    /**
     * True when the item carries EVERY tag of {@code match} (a list tag carries each of its values). It proves the
     * correct answer is correct, and rejects a wrong answer that also satisfies the question.
     */
    public static boolean satisfies(Map<String, Object> itemTags, Map<String, Object> match) {
        if (itemTags == null) {
            return false;
        }
        for (Map.Entry<String, Object> wanted : match.entrySet()) {
            if (!carries(itemTags.get(wanted.getKey()), wanted.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean carries(Object itemValue, Object wanted) {
        if (itemValue == null) {
            return false;
        }
        if (itemValue instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> sameScalar(value, wanted));
        }
        return sameScalar(itemValue, wanted);
    }

    private static boolean sameScalar(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return a != null && a.equals(b);
    }
}
