package com.nimokids.service.generation;

import java.util.Map;

/**
 * One pool of wrong answers: pick {@code count} items whose tags contain every entry of {@code match}.
 *
 * @param match flat map of explicit tags, e.g. {"category":"animal","can_fly":false}
 */
public record DistractorRule(Map<String, Object> match, int count) {
}
