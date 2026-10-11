package com.nimokids.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.QuestionVisual;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.DynamicQuestionService;
import com.nimokids.service.generation.DynamicQuestion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generator {@code COUNT_OBJECTS} (HOW_MANY): picks a count N and an object, finds the number item whose
 * {@code value} is N, and describes the picture ("draw N of this object").
 *
 * <pre>
 * "generator": { "type": "COUNT_OBJECTS", "min": 1, "max": 5,
 *                "object_match": {"countable": true}, "number_match": {"category": "numbers", "counting": true} }
 * </pre>
 *
 * Nothing is stored per (object, count) pair: the combination is drawn for every session.
 */
@Service
public class DynamicQuestionServiceImpl implements DynamicQuestionService {

    public static final String GENERATOR_KEY = "generator";
    public static final String COUNT_OBJECTS = "COUNT_OBJECTS";
    private static final int DEFAULT_MIN = 1;
    private static final int DEFAULT_MAX = 5;
    private static final int LIMIT_MAX = 10;

    private final AnswerItemRepository answerItemRepository;
    private final ObjectMapper objectMapper;
    private final RandomGenerator random;

    @Autowired
    public DynamicQuestionServiceImpl(AnswerItemRepository answerItemRepository, ObjectMapper objectMapper) {
        this(answerItemRepository, objectMapper, ThreadLocalRandom.current());
    }

    DynamicQuestionServiceImpl(AnswerItemRepository answerItemRepository, ObjectMapper objectMapper, RandomGenerator random) {
        this.answerItemRepository = answerItemRepository;
        this.objectMapper = objectMapper;
        this.random = random;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DynamicQuestion> prepare(GameQuestion question) {
        Map<String, Object> metadata = question.getMetadata();
        Object raw = metadata == null ? null : metadata.get(GENERATOR_KEY);
        if (raw == null) {
            return Optional.empty();
        }
        if (!(raw instanceof Map<?, ?> generator)) {
            throw new InvalidDistractorRulesException("metadata." + GENERATOR_KEY + " must be an object");
        }
        String type = String.valueOf(generator.get("type"));
        if (!COUNT_OBJECTS.equals(type)) {
            throw new InvalidDistractorRulesException("Unknown question generator: " + type);
        }
        return Optional.of(countObjects(question, generator));
    }

    private DynamicQuestion countObjects(GameQuestion question, Map<?, ?> generator) {
        int min = intOf(generator.get("min"), DEFAULT_MIN);
        int max = intOf(generator.get("max"), DEFAULT_MAX);
        if (min < 1 || max < min || max > LIMIT_MAX) {
            throw new InvalidDistractorRulesException("Question " + question.getId() + ": generator range " + min + ".." + max
                    + " must satisfy 1 <= min <= max <= " + LIMIT_MAX);
        }
        int count = random.nextInt(min, max + 1);

        AnswerItem object = first(matchOf(generator.get("object_match"), "object_match"), Map.of(),
                "a countable object", question);
        String objectImage = objectImageOf(object, question);

        AnswerItem number = first(matchOf(generator.get("number_match"), "number_match"), Map.of("value", count),
                "the number item with value " + count, question);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generator", COUNT_OBJECTS);
        audit.put("count", count);
        audit.put("objectItemId", object.getId());
        audit.put("objectCode", object.getCode());
        return new DynamicQuestion(number, Map.of("value", count),
                new QuestionVisual(QuestionVisual.COUNTING_OBJECTS, objectImage, count), audit);
    }

    private AnswerItem first(Map<String, Object> match, Map<String, Object> extra, String what, GameQuestion question) {
        Map<String, Object> all = new LinkedHashMap<>(match);
        all.putAll(extra);
        List<AnswerItem> found = answerItemRepository.findRandomActive(toJson(all), 1);
        if (found.isEmpty()) {
            throw new InvalidDistractorRulesException("Question " + question.getId() + ": no active item for " + what + " " + all);
        }
        return found.get(0);
    }

    private static String objectImageOf(AnswerItem object, GameQuestion question) {
        if (object.getImage() != null && object.getImage().getStorageUrl() != null) {
            return object.getImage().getStorageUrl();
        }
        Object emoji = object.getMetadata() == null ? null : object.getMetadata().get("emoji");
        if (emoji instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new InvalidDistractorRulesException("Question " + question.getId() + ": object '" + object.getName()
                + "' has no image and no emoji to draw");
    }

    private static Map<String, Object> matchOf(Object raw, String name) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new InvalidDistractorRulesException("generator." + name + " must be a non-empty object of tags");
        }
        Map<String, Object> match = new LinkedHashMap<>();
        map.forEach((key, value) -> match.put(String.valueOf(key), value));
        return match;
    }

    private static int intOf(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private String toJson(Map<String, Object> match) {
        try {
            return objectMapper.writeValueAsString(match);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize generator match", ex);
        }
    }
}
