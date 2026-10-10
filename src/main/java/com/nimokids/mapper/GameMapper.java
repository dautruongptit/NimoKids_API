package com.nimokids.mapper;

import com.nimokids.util.GameConstants;
import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.dto.response.CorrectAnswerResponse;
import com.nimokids.dto.response.GameModeResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.dto.response.NextQuestionResponse;
import com.nimokids.dto.response.OptionResponse;
import com.nimokids.dto.response.QuestionResponse;
import com.nimokids.dto.response.StickerResponse;
import com.nimokids.dto.response.TopicResponse;
import com.nimokids.dto.response.TopicSummaryResponse;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.QuestionSnapshot;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Sticker;
import com.nimokids.entity.Topic;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/** Entity to DTO mapping only. Contains no business rule. */
@Component
public class GameMapper {

    public TopicResponse toTopicResponse(Topic topic) {
        return toTopicResponse(topic, LanguageMode.EN);
    }

    public TopicResponse toTopicResponse(Topic topic, LanguageMode language) {
        return new TopicResponse(
                topic.getId(),
                topic.getParentTopic() == null ? null : topic.getParentTopic().getId(),
                topic.getCode(),
                topic.nameFor(language),
                topic.getSlug(),
                topic.descriptionFor(language),
                topic.getIconUrl(),
                topic.getCoverImageUrl(),
                topic.getMinAge().intValue(),
                topic.getMaxAge().intValue(),
                topic.getDisplayOrder());
    }

    public GameModeResponse toGameModeResponse(GameMode mode) {
        return new GameModeResponse(mode.getId(), mode.getCode(), mode.getName(), mode.getDescription());
    }

    /**
     * The question as shown to the child, built from the stored options snapshot. {@link OptionResponse} has no
     * correctness field: the answer is only revealed after it has been recorded.
     */
    public QuestionResponse toQuestionResponse(SessionQuestion sessionQuestion) {
        GameQuestion question = sessionQuestion.getQuestion();
        QuestionSnapshot frozen = sessionQuestion.getQuestionSnapshot();
        if (frozen == null) {
            // Session created before V9: no snapshot, read the live question.
            Object emoji = question.getCorrectAnswerItem() != null
                    && question.getCorrectAnswerItem().getMetadata() != null
                    ? question.getCorrectAnswerItem().getMetadata().get("emoji") : null;
            frozen = new QuestionSnapshot(question.getQuestionKey(), null, null, 1, null, null,
                    question.getQuestionText(), null,
                    url(question.getQuestionVoice()), url(question.getObjectSound()),
                    emoji instanceof String s ? s : null, null, null, null, null, null, null);
        }
        // Everything shown to the child comes from the frozen snapshot (rules 9.7).
        return new QuestionResponse(
                question.getId(),
                frozen.questionText(),
                frozen.questionTextVi(),
                frozen.questionVoiceUrl(),
                frozen.objectSoundUrl(),
                frozen.questionImage(),
                sessionQuestion.getOptionsSnapshot().stream()
                        .sorted(Comparator.comparingInt(SnapshotOption::displayOrder))
                        .map(option -> new OptionResponse(option.optionId(), option.text(), option.imageUrl(), option.voiceUrl()))
                        .toList());
    }

    /** {@code current} is null when the session is no longer STARTED. */
    public GameSessionResponse toSessionResponse(GameSession session, SessionQuestion current) {
        Topic topic = session.getTopic();
        LanguageMode language = session.getLanguageMode();
        TopicSummaryResponse topicSummary = topic != null
                ? new TopicSummaryResponse(topic.getId(), topic.nameFor(language))
                : new TopicSummaryResponse(null,
                        language.servesVietnamese() ? GameConstants.ALL_TOPICS_VI : GameConstants.ALL_TOPICS_EN);
        return new GameSessionResponse(
                session.getSessionId(),
                session.getStatus(),
                topicSummary,
                session.getTotalQuestions().intValue(),
                session.getCurrentQuestionNumber().intValue(),
                current == null ? null : current.getQuestion().getTimeLimitSeconds().intValue(),
                current == null ? null : toQuestionResponse(current));
    }

    public NextQuestionResponse toNextQuestionResponse(SessionQuestion sessionQuestion) {
        return new NextQuestionResponse(
                sessionQuestion.getQuestionNumber().intValue(),
                sessionQuestion.getQuestion().getTimeLimitSeconds().intValue(),
                toQuestionResponse(sessionQuestion));
    }

    public CorrectAnswerResponse toCorrectAnswerResponse(SnapshotOption option) {
        return new CorrectAnswerResponse(option.optionId(), option.text(), option.voiceUrl());
    }

    public StickerResponse toStickerResponse(Sticker sticker, Instant earnedAt) {
        return new StickerResponse(
                sticker.getCode(), sticker.getName(), url(sticker.getImage()), sticker.getRarity(), earnedAt);
    }

    private static String url(MediaAsset asset) {
        return asset == null ? null : asset.getStorageUrl();
    }
}
