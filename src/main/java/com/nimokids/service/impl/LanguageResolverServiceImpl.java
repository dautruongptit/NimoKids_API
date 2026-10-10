package com.nimokids.service.impl;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.QuestionTemplate;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.LanguageResolverService;
import com.nimokids.service.language.I18nContent;
import com.nimokids.service.language.QuestionInstance;
import com.nimokids.service.language.ResolvedQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageResolverServiceImpl implements LanguageResolverService {

    private final AnswerItemRepository answerItemRepository;

    @Override
    @Transactional(readOnly = true)
    public ResolvedQuestion resolve(LanguageMode languageMode, QuestionInstance instance) {
        Objects.requireNonNull(languageMode, "languageMode");
        Objects.requireNonNull(instance, "instance");
        String instructionLanguage = languageMode.instructionLanguage();
        String answerLanguage = languageMode.answerLanguage();

        GameQuestion question = instance.question();
        QuestionTemplate template = question.getTemplate();
        String text = resolveQuestionText(question, template, instructionLanguage);
        String audio = resolveQuestionAudio(question, template, instructionLanguage);

        Map<UUID, AnswerItem> items = loadItems(instance.options());
        List<SnapshotOption> options = new ArrayList<>(instance.options().size());
        for (SnapshotOption option : instance.options()) {
            AnswerItem item = option.answerItemId() == null ? null : items.get(option.answerItemId());
            if (item == null) {
                options.add(option);   // an option without a vocabulary item (legacy) is left as generated
                continue;
            }
            options.add(new SnapshotOption(
                    option.optionId(),
                    option.answerItemId(),
                    resolveItemText(item, answerLanguage),
                    option.imageUrl(),
                    resolveItemAudio(item, answerLanguage, option.voiceUrl()),
                    option.displayOrder(),
                    option.isCorrect()));
        }
        return new ResolvedQuestion(text, audio, options, languageMode, instructionLanguage, answerLanguage);
    }

    // ------------------------------------------------------------------------------------------ question

    private static String resolveQuestionText(GameQuestion question, QuestionTemplate template, String language) {
        if (template != null) {
            String text = I18nContent.text(template.getI18n(), language);
            if (text != null) {
                return text;
            }
            String english = I18nContent.text(template.getI18n(), I18nContent.EN);
            if (english != null) {
                log.warn("Template {} has no {} text: using English", template.getCode(), language);
                return english;
            }
        }
        log.warn("Question {} has no template text: using its stored text", question.getId());
        return question.getQuestionText();
    }

    private static String resolveQuestionAudio(GameQuestion question, QuestionTemplate template, String language) {
        if (template != null) {
            String audio = I18nContent.audio(template.getI18n(), language);
            if (audio != null) {
                return audio;
            }
        }
        // The legacy voice asset on the question is English: never play it for another language.
        return I18nContent.EN.equals(language) && question.getQuestionVoice() != null
                ? question.getQuestionVoice().getStorageUrl() : null;
    }

    // ------------------------------------------------------------------------------------------ answers

    private static String resolveItemText(AnswerItem item, String language) {
        String text = I18nContent.text(item.getI18n(), language);
        if (text != null) {
            return text;
        }
        String english = I18nContent.text(item.getI18n(), I18nContent.EN);
        return english != null ? english : item.getName();
    }

    private static String resolveItemAudio(AnswerItem item, String language, String legacyEnglishVoiceUrl) {
        String audio = I18nContent.audio(item.getI18n(), language);
        if (audio != null) {
            return audio;
        }
        return I18nContent.EN.equals(language) ? legacyEnglishVoiceUrl : null;
    }

    private Map<UUID, AnswerItem> loadItems(List<SnapshotOption> options) {
        List<UUID> ids = options.stream().map(SnapshotOption::answerItemId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // findById reads the persistence context first: the items were just loaded by the option generator, so this costs
        // no database round trip (findAllById always queries).
        return ids.stream().distinct().map(answerItemRepository::findById).flatMap(Optional::stream)
                .collect(Collectors.toMap(AnswerItem::getId, Function.identity()));
    }
}
