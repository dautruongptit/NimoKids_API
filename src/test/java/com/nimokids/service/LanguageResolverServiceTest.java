package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.QuestionTemplate;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.impl.LanguageResolverServiceImpl;
import com.nimokids.service.language.QuestionInstance;
import com.nimokids.service.language.ResolvedQuestion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LanguageResolverServiceTest {

    private final AnswerItemRepository items = mock(AnswerItemRepository.class);
    private final LanguageResolverService resolver = new LanguageResolverServiceImpl(items);

    private AnswerItem cat;
    private AnswerItem dog;
    private GameQuestion question;
    private QuestionInstance instance;

    @BeforeEach
    void setUp() {
        cat = item("Cat", Map.of(
                "en", Map.of("text", "Cat", "audio", "/audio/en/cat.mp3"),
                "vi", Map.of("text", "Con mèo", "audio", "/audio/vi/cat.mp3")));
        dog = item("Dog", Map.of("en", Map.of("text", "Dog"), "vi", Map.of("text", "Con chó")));

        QuestionTemplate template = QuestionTemplate.builder().code("WHAT_IS_THIS").i18n(Map.of(
                "en", Map.of("text", "What is this?", "audio", "/audio/en/what_is_this.mp3"),
                "vi", Map.of("text", "Đây là gì?", "audio", "/audio/vi/what_is_this.mp3"))).build();
        question = GameQuestion.builder().questionText("What is this? (stored)").template(template).build();
        question.setId(UUID.randomUUID());

        instance = new QuestionInstance(question, List.of(option(cat, 1, true), option(dog, 2, false)));
        when(items.findById(cat.getId())).thenReturn(java.util.Optional.of(cat));
        when(items.findById(dog.getId())).thenReturn(java.util.Optional.of(dog));
    }

    @Test
    void viUsesVietnameseForTheQuestionAndForTheAnswers() {
        ResolvedQuestion resolved = resolver.resolve(LanguageMode.VI, instance);

        assertThat(resolved.questionText()).isEqualTo("Đây là gì?");
        assertThat(resolved.questionAudioUrl()).isEqualTo("/audio/vi/what_is_this.mp3");
        assertThat(resolved.options()).extracting(SnapshotOption::text).containsExactly("Con mèo", "Con chó");
        assertThat(resolved.options().get(0).voiceUrl()).isEqualTo("/audio/vi/cat.mp3");
        assertThat(resolved.instructionLanguage()).isEqualTo("vi");
        assertThat(resolved.answerLanguage()).isEqualTo("vi");
    }

    @Test
    void enUsesEnglishForTheQuestionAndForTheAnswers() {
        ResolvedQuestion resolved = resolver.resolve(LanguageMode.EN, instance);

        assertThat(resolved.questionText()).isEqualTo("What is this?");
        assertThat(resolved.questionAudioUrl()).isEqualTo("/audio/en/what_is_this.mp3");
        assertThat(resolved.options()).extracting(SnapshotOption::text).containsExactly("Cat", "Dog");
        assertThat(resolved.options().get(0).voiceUrl()).isEqualTo("/audio/en/cat.mp3");
    }

    @Test
    void viEnAsksInVietnameseAndAnswersInEnglish() {
        ResolvedQuestion resolved = resolver.resolve(LanguageMode.VI_EN, instance);

        assertThat(resolved.questionText()).isEqualTo("Đây là gì?");
        assertThat(resolved.questionAudioUrl()).isEqualTo("/audio/vi/what_is_this.mp3");
        assertThat(resolved.options()).extracting(SnapshotOption::text).containsExactly("Cat", "Dog");
        assertThat(resolved.options().get(0).voiceUrl()).isEqualTo("/audio/en/cat.mp3");
        assertThat(resolved.instructionLanguage()).isEqualTo("vi");
        assertThat(resolved.answerLanguage()).isEqualTo("en");
    }

    @Test
    void theCorrectOptionAndTheOrderSurviveTheResolution() {
        ResolvedQuestion resolved = resolver.resolve(LanguageMode.VI_EN, instance);

        assertThat(resolved.options()).extracting(SnapshotOption::isCorrect).containsExactly(true, false);
        assertThat(resolved.options()).extracting(SnapshotOption::displayOrder).containsExactly(1, 2);
        assertThat(resolved.options()).extracting(SnapshotOption::optionId)
                .containsExactlyElementsOf(instance.options().stream().map(SnapshotOption::optionId).toList());
    }

    @Test
    void aMissingTranslationFallsBackToEnglishThenToTheStoredName() {
        AnswerItem bird = item("Bird", Map.of("en", Map.of("text", "Bird")));          // no vi
        AnswerItem fish = item("Fish", Map.of());                                       // nothing at all
        when(items.findById(bird.getId())).thenReturn(java.util.Optional.of(bird));
        when(items.findById(fish.getId())).thenReturn(java.util.Optional.of(fish));
        question.getTemplate().setI18n(Map.of("en", Map.of("text", "What is this?")));  // template without vi

        ResolvedQuestion resolved = resolver.resolve(LanguageMode.VI,
                new QuestionInstance(question, List.of(option(bird, 1, true), option(fish, 2, false))));

        assertThat(resolved.questionText()).isEqualTo("What is this?");
        assertThat(resolved.options()).extracting(SnapshotOption::text).containsExactly("Bird", "Fish");
        assertThat(resolved.options().get(0).voiceUrl()).isNull();   // no Vietnamese audio: the browser voice is used
    }

    @Test
    void aQuestionWithoutATemplateKeepsItsStoredText() {
        question.setTemplate(null);

        assertThat(resolver.resolve(LanguageMode.VI, instance).questionText()).isEqualTo("What is this? (stored)");
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static AnswerItem item(String name, Map<String, Map<String, String>> i18n) {
        AnswerItem item = AnswerItem.builder().code(name.toUpperCase()).name(name).i18n(i18n).build();
        item.setId(UUID.randomUUID());
        return item;
    }

    private static SnapshotOption option(AnswerItem item, int order, boolean correct) {
        return new SnapshotOption(UUID.randomUUID(), item.getId(), item.getName(), null, null, order, correct);
    }
}
