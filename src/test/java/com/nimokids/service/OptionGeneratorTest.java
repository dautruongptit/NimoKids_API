package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.exception.InsufficientDistractorsException;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.impl.OptionGeneratorImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OptionGeneratorTest {

    private final AnswerItemRepository repository = mock(AnswerItemRepository.class);
    private final OptionGenerator generator = new OptionGeneratorImpl(repository, new ObjectMapper());

    private static AnswerItem item(String name) {
        AnswerItem item = AnswerItem.builder().code(name.toUpperCase()).name(name).build();
        item.setId(UUID.randomUUID());
        return item;
    }

    private static GameQuestion question(AnswerItem correct, Object rules) {
        correct.getMetadata().put("can_fly", true);
        GameQuestion question = GameQuestion.builder()
                .questionText("Which animal can fly?").correctAnswerItem(correct)
                .metadata(Map.of("distractor_rules", rules, "correct_match", Map.of("can_fly", true))).build();
        question.setId(UUID.randomUUID());
        return question;
    }

    private static Object singleRule() {
        return List.of(Map.of("match", Map.of("category", "animal", "can_fly", false), "count", 3));
    }

    @Test
    void buildsFourDistinctShuffledOptionsWithExactlyOneCorrect() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt()))
                .thenReturn(List.of(item("Dog"), item("Cat"), item("Cow")));

        List<SnapshotOption> options = generator.generate(question(bird, singleRule()));

        assertThat(options).hasSize(4);
        assertThat(options).extracting(SnapshotOption::displayOrder).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(options).extracting(SnapshotOption::text).containsExactlyInAnyOrder("Bird", "Dog", "Cat", "Cow");
        assertThat(options.stream().filter(SnapshotOption::isCorrect)).hasSize(1);
        SnapshotOption correct = options.stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow();
        assertThat(correct.text()).isEqualTo("Bird");
        assertThat(correct.answerItemId()).isEqualTo(bird.getId());
    }

    @Test
    void optionIdsAreFreshAndDifferentFromTheAnswerItemIds() {
        AnswerItem bird = item("Bird");
        List<AnswerItem> wrong = List.of(item("Dog"), item("Cat"), item("Cow"));
        when(repository.findRandomMatching(anyString(), any(), anyInt())).thenReturn(wrong);

        List<SnapshotOption> options = generator.generate(question(bird, singleRule()));

        Set<UUID> itemIds = new HashSet<>(List.of(bird.getId()));
        wrong.forEach(w -> itemIds.add(w.getId()));
        assertThat(options.stream().map(SnapshotOption::optionId)).doesNotContainAnyElementsOf(itemIds).doesNotHaveDuplicates();
        assertThat(options.stream().map(SnapshotOption::answerItemId)).containsExactlyInAnyOrderElementsOf(itemIds);
    }

    @Test
    void theOrderIsActuallyShuffledAcrossManyRuns() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt()))
                .thenReturn(List.of(item("Dog"), item("Cat"), item("Cow")));

        Set<Integer> positionsOfTheCorrectOption = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            positionsOfTheCorrectOption.add(generator.generate(question(bird, singleRule())).stream()
                    .filter(SnapshotOption::isCorrect).findFirst().orElseThrow().displayOrder());
        }

        assertThat(positionsOfTheCorrectOption).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    void wrongAnswersAreSearchedByExplicitTagMatchingAndNeverByNegation() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt()))
                .thenReturn(List.of(item("Dog"), item("Cat"), item("Cow")));

        generator.generate(question(bird, singleRule()));

        ArgumentCaptor<String> match = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findRandomMatching(match.capture(), excluded.capture(), eq(24));
        assertThat(match.getValue()).contains("\"category\":\"animal\"").contains("\"can_fly\":false");
        assertThat(match.getValue()).doesNotContainIgnoringCase("not").doesNotContain("!").doesNotContain("true");
        assertThat(excluded.getValue()).containsExactly(bird.getId());   // only the correct item itself is excluded
    }

    @Test
    void severalPoolsAreQueriedSeparatelyAndEarlierPicksAreExcludedFromLaterPools() {
        AnswerItem cow = item("Cow");
        AnswerItem lion = item("Lion");
        AnswerItem monkey = item("Monkey");
        AnswerItem fish = item("Fish");
        Object rules = List.of(
                Map.of("match", Map.of("habitat", "forest"), "count", 2),
                Map.of("match", Map.of("habitat", "water"), "count", 1));
        when(repository.findRandomMatching(argThat(json -> json != null && json.contains("forest")), any(), anyInt()))
                .thenReturn(List.of(lion, monkey));
        when(repository.findRandomMatching(argThat(json -> json != null && json.contains("water")), any(), anyInt()))
                .thenReturn(List.of(fish));

        List<SnapshotOption> options = generator.generate(question(cow, rules));

        assertThat(options).extracting(SnapshotOption::text).containsExactlyInAnyOrder("Cow", "Lion", "Monkey", "Fish");
        ArgumentCaptor<Collection<UUID>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(repository, times(2)).findRandomMatching(anyString(), excluded.capture(), anyInt());
        assertThat(excluded.getAllValues().get(1)).contains(cow.getId(), lion.getId(), monkey.getId());
    }

    @Test
    void candidatesWithTheSameNameAreSkippedSoTheChildNeverSeesTwoIdenticalWords() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt())).thenReturn(
                List.of(item("Dog"), item("dog"), item("Bird"), item("Cat"), item("Cow")));

        List<SnapshotOption> options = generator.generate(question(bird, singleRule()));

        assertThat(options).extracting(SnapshotOption::text).containsExactlyInAnyOrder("Bird", "Dog", "Cat", "Cow");
    }

    @Test
    void aPoolThatCannotSupplyEnoughItemsFailsInsteadOfPaddingWithSomethingElse() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt())).thenReturn(List.of(item("Dog"), item("Cat")));

        assertThatThrownBy(() -> generator.generate(question(bird, singleRule())))
                .isInstanceOf(InsufficientDistractorsException.class)
                .hasMessageContaining("needs 3");
        verify(repository, times(1)).findRandomMatching(anyString(), any(), anyInt());
    }

    @Test
    void invalidRulesAreRejectedBeforeAnyDatabaseQuery() {
        AnswerItem bird = item("Bird");
        GameQuestion noRules = GameQuestion.builder().questionText("?").correctAnswerItem(bird).build();
        noRules.setId(UUID.randomUUID());

        assertThatThrownBy(() -> generator.generate(noRules)).isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> generator.generate(question(bird, List.of(Map.of("match", Map.of("a", "b"), "count", 2)))))
                .isInstanceOf(InvalidDistractorRulesException.class);
        verify(repository, never()).findRandomMatching(anyString(), any(), anyInt());
    }

    private static AnswerItem tagged(String name, Object canFly) {
        AnswerItem item = item(name);
        item.getMetadata().put("can_fly", canFly);
        return item;
    }

    @Test
    void aWrongAnswerThatCouldAlsoBeRightIsNeverShown() {
        AnswerItem bird = item("Bird");
        // The pool is too broad: Eagle and Bee also fly, and Duck lists both values.
        when(repository.findRandomMatching(anyString(), any(), anyInt())).thenReturn(List.of(
                tagged("Eagle", true), tagged("Bee", true), tagged("Duck", List.of(false, true)),
                tagged("Dog", false), tagged("Cat", false), tagged("Cow", false)));

        List<SnapshotOption> options = generator.generate(question(bird, singleRule()));

        assertThat(options).extracting(SnapshotOption::text).containsExactlyInAnyOrder("Bird", "Dog", "Cat", "Cow");
    }

    @Test
    void ifTooFewSafeWrongAnswersRemainTheQuestionFailsInsteadOfShowingAnAmbiguousOne() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt())).thenReturn(List.of(
                tagged("Eagle", true), tagged("Bee", true), tagged("Dog", false), tagged("Cat", false)));

        assertThatThrownBy(() -> generator.generate(question(bird, singleRule())))
                .isInstanceOf(InsufficientDistractorsException.class);
    }

    @Test
    void theCorrectAnswerMustSatisfyTheCorrectMatch() {
        AnswerItem dog = item("Dog");
        GameQuestion question = GameQuestion.builder().questionText("Which animal can fly?").correctAnswerItem(dog)
                .metadata(Map.of("distractor_rules", singleRule(), "correct_match", Map.of("can_fly", true))).build();
        question.setId(UUID.randomUUID());

        assertThatThrownBy(() -> generator.generate(question))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("does not satisfy correct_match");
        verify(repository, never()).findRandomMatching(anyString(), any(), anyInt());
    }

    @Test
    void aQuestionWithoutCorrectMatchIsRejected() {
        AnswerItem bird = item("Bird");
        GameQuestion question = GameQuestion.builder().questionText("?").correctAnswerItem(bird)
                .metadata(Map.of("distractor_rules", singleRule())).build();
        question.setId(UUID.randomUUID());

        assertThatThrownBy(() -> generator.generate(question))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("correct_match is required");
    }

    @Test
    void mediaUrlsOfTheItemsAreCopiedIntoTheSnapshot() {
        AnswerItem bird = item("Bird");
        bird.setImage(MediaAsset.builder().name("bird").storageUrl("https://cdn/bird.png").build());
        bird.setVoice(MediaAsset.builder().name("bird-v").storageUrl("https://cdn/bird.mp3").build());
        when(repository.findRandomMatching(anyString(), any(), anyInt()))
                .thenReturn(List.of(item("Dog"), item("Cat"), item("Cow")));

        List<SnapshotOption> options = generator.generate(question(bird, singleRule()));

        SnapshotOption correct = options.stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow();
        assertThat(correct.imageUrl()).isEqualTo("https://cdn/bird.png");
        assertThat(correct.voiceUrl()).isEqualTo("https://cdn/bird.mp3");
        assertThat(options.stream().filter(o -> !o.isCorrect()).map(SnapshotOption::imageUrl)).containsOnlyNulls();
    }

    @Test
    void severalGenerationsOfTheSameQuestionProduceIndependentSnapshots() {
        AnswerItem bird = item("Bird");
        when(repository.findRandomMatching(anyString(), any(), anyInt()))
                .thenReturn(List.of(item("Dog"), item("Cat"), item("Cow")));
        GameQuestion question = question(bird, singleRule());

        List<UUID> first = generator.generate(question).stream().map(SnapshotOption::optionId).collect(Collectors.toList());
        List<UUID> second = new ArrayList<>(generator.generate(question).stream().map(SnapshotOption::optionId).toList());

        assertThat(second).doesNotContainAnyElementsOf(first);
    }
}
