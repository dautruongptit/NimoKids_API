package com.nimokids.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.QuestionVisual;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.generation.DynamicQuestion;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DynamicQuestionServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnswerItemRepository repository = mock(AnswerItemRepository.class);

    private static AnswerItem item(String code, Map<String, Object> tags) {
        AnswerItem item = AnswerItem.builder().code(code).name(code).build();
        item.setId(UUID.randomUUID());
        item.getMetadata().putAll(tags);
        return item;
    }

    private static GameQuestion howMany(Object generator) {
        GameQuestion question = GameQuestion.builder()
                .questionText("How many are there?").metadata(Map.of("generator", generator)).build();
        question.setId(UUID.randomUUID());
        return question;
    }

    private static Map<String, Object> countObjects(int min, int max) {
        return Map.of("type", "COUNT_OBJECTS", "min", min, "max", max,
                "object_match", Map.of("countable", true),
                "number_match", Map.of("category", "numbers", "counting", true));
    }

    /** Repository stub: the object query returns {@code object}; the number query returns the item whose value matches. */
    private void stubData(AnswerItem object) {
        when(repository.findRandomActive(anyString(), anyInt())).thenAnswer(invocation -> {
            JsonNode match = JSON.readTree(invocation.<String>getArgument(0));
            if (match.has("value")) {
                int value = match.get("value").asInt();
                return List.of(item("NUMBERS_" + value, Map.of("category", "numbers", "counting", true, "value", value)));
            }
            return List.of(object);
        });
    }

    private DynamicQuestionServiceImpl service(Random random) {
        return new DynamicQuestionServiceImpl(repository, JSON, random);
    }

    @Test
    void anOrdinaryQuestionHasNoDynamicPart() {
        GameQuestion question = GameQuestion.builder().questionText("What is this?").metadata(Map.of()).build();

        assertThat(service(new Random(1)).prepare(question)).isEmpty();
    }

    @Test
    void everyCountFromMinToMaxIsGeneratedWithAPictureOfExactlyThatManyObjects() {
        stubData(item("FRUITS_APPLE", Map.of("emoji", "🍎", "countable", true)));
        DynamicQuestionServiceImpl service = service(new Random(7));
        Set<Integer> seen = new HashSet<>();

        for (int i = 0; i < 300; i++) {
            DynamicQuestion dynamic = service.prepare(howMany(countObjects(1, 5))).orElseThrow();
            int count = dynamic.visual().count();
            seen.add(count);
            assertThat(count).isBetween(1, 5);
            assertThat(dynamic.visual().type()).isEqualTo(QuestionVisual.COUNTING_OBJECTS);
            assertThat(dynamic.visual().objectImage()).isEqualTo("🍎");
            // the correct answer is the number item whose value equals the number of drawn objects
            assertThat(dynamic.correctItem().getMetadata().get("value")).isEqualTo(count);
            assertThat(dynamic.correctMatch()).isEqualTo(Map.of("value", count));
            assertThat(dynamic.audit()).containsEntry("count", count).containsEntry("objectCode", "FRUITS_APPLE");
        }
        assertThat(seen).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    void anImageUrlIsPreferredToTheEmoji() {
        AnswerItem ball = item("TOYS_BALL", Map.of("emoji", "⚽", "countable", true));
        ball.setImage(MediaAsset.builder().name("ball").storageUrl("/media/toys/ball.webp").build());
        stubData(ball);

        DynamicQuestion dynamic = service(new Random(3)).prepare(howMany(countObjects(2, 2))).orElseThrow();

        assertThat(dynamic.visual().objectImage()).isEqualTo("/media/toys/ball.webp");
        assertThat(dynamic.visual().count()).isEqualTo(2);
    }

    @Test
    void anObjectWithNothingToDrawIsRejectedSoTheSessionTakesAnotherQuestion() {
        stubData(item("THING", Map.of("countable", true)));

        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(countObjects(1, 5))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("no image and no emoji");
    }

    @Test
    void missingDataIsRejectedInsteadOfInventingAnAnswer() {
        when(repository.findRandomActive(anyString(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(countObjects(1, 5))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("no active item");
    }

    @Test
    void aMalformedGeneratorIsRejected() {
        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(Map.of("type", "TELEPORT"))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("Unknown question generator");
        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(countObjects(0, 5))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("generator range");
        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(countObjects(3, 2))))
                .isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> service(new Random(1)).prepare(howMany(
                Map.of("type", "COUNT_OBJECTS", "number_match", Map.of("counting", true)))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("object_match");
    }
}
