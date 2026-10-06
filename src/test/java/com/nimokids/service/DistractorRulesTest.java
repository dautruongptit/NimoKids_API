package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.service.generation.DistractorRule;
import com.nimokids.service.generation.DistractorRules;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistractorRulesTest {

    private static Map<String, Object> meta(Object rules) {
        return Map.of(DistractorRules.KEY, rules);
    }

    private static Map<String, Object> rule(Object match, Object count) {
        return Map.of("match", match, "count", count);
    }

    @Test
    void aSinglePoolOfThreeIsAccepted() {
        List<DistractorRule> rules = DistractorRules.parse(
                meta(List.of(rule(Map.of("category", "animal", "can_fly", false), 3))), 3);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).count()).isEqualTo(3);
        assertThat(rules.get(0).match()).containsEntry("category", "animal").containsEntry("can_fly", false);
    }

    @Test
    void severalPoolsAreAcceptedWhenTheirCountsAddUpToThree() {
        List<DistractorRule> rules = DistractorRules.parse(meta(List.of(
                rule(Map.of("habitat", "forest"), 2),
                rule(Map.of("habitat", "water"), 1))), 3);

        assertThat(rules).extracting(DistractorRule::count).containsExactly(2, 1);
    }

    @Test
    void numbersAndBooleansAreValidScalarTags() {
        assertThat(DistractorRules.parse(meta(List.of(rule(Map.of("legs", 4, "wild", true), 3))), 3)).hasSize(1);
    }

    @Test
    void rulesThatDoNotAddUpToThreeAreRejected() {
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("a", "b"), 2))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("add up to 2");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("a", "b"), 3), rule(Map.of("c", "d"), 1))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("add up to 4");
    }

    @Test
    void missingOrEmptyRulesAreRejected() {
        assertThatThrownBy(() -> DistractorRules.parse(null, 3)).isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> DistractorRules.parse(Map.of(), 3)).isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of()), 3)).isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> DistractorRules.parse(meta("not-an-array"), 3)).isInstanceOf(InvalidDistractorRulesException.class);
    }

    @Test
    void aMatchMustBeANonEmptyFlatObjectOfScalarTags() {
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of(), 3))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule("animal", 3))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("tags", List.of("a")), 3))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("flat");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("nested", Map.of("a", 1)), 3))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("flat");
    }

    @Test
    void thereIsNoSyntaxForNegationOrOtherOperators() {
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(
                Map.of("match", Map.of("a", "b"), "count", 3, "not", Map.of("can_fly", true)))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("unknown field");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(
                Map.of("exclude", Map.of("a", "b"), "count", 3))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class);
    }

    @Test
    void theCountMustBeAPositiveWholeNumber() {
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("a", "b"), 0))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("at least 1");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("a", "b"), 1.5))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("integer");
        assertThatThrownBy(() -> DistractorRules.parse(meta(List.of(rule(Map.of("a", "b"), "3"))), 3))
                .isInstanceOf(InvalidDistractorRulesException.class);
    }

    @Test
    void theErrorUsesTheStableErrorCode() {
        assertThatThrownBy(() -> DistractorRules.parse(null, 3))
                .isInstanceOfSatisfying(InvalidDistractorRulesException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DISTRACTOR_RULES));
    }

    @Test
    void theCorrectMatchIsRequiredAndMustBeFlatScalarTags() {
        assertThat(DistractorRules.parseCorrectMatch(Map.of(DistractorRules.CORRECT_MATCH_KEY, Map.of("habitat", "water"))))
                .containsEntry("habitat", "water");
        assertThatThrownBy(() -> DistractorRules.parseCorrectMatch(Map.of()))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("correct_match is required");
        assertThatThrownBy(() -> DistractorRules.parseCorrectMatch(Map.of(DistractorRules.CORRECT_MATCH_KEY, Map.of())))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> DistractorRules.parseCorrectMatch(
                Map.of(DistractorRules.CORRECT_MATCH_KEY, Map.of("habitat", List.of("water")))))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("flat");
    }
}
