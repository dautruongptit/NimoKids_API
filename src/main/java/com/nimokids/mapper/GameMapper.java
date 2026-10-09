package com.nimokids.mapper;

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
        return new TopicResponse(
                topic.getId(),
                topic.getParentTopic() == null ? null : topic.getParentTopic().getId(),
                topic.getCode(),
                topic.getName(),
                topic.getSlug(),
                topic.getDescription(),
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
        // Emoji from the correct answer item's metadata — the large visual above the options.
        Object emoji = question.getCorrectAnswerItem() != null
                && question.getCorrectAnswerItem().getMetadata() != null
                ? question.getCorrectAnswerItem().getMetadata().get("emoji") : null;
        return new QuestionResponse(
                question.getId(),
                question.getQuestionText(),
                url(question.getQuestionVoice()),
                url(question.getObjectSound()),
                emoji instanceof String s ? s : null,
                sessionQuestion.getOptionsSnapshot().stream()
                        .sorted(Comparator.comparingInt(SnapshotOption::displayOrder))
                        .map(option -> new OptionResponse(option.optionId(), option.text(), option.imageUrl(), option.voiceUrl()))
                        .toList());
    }

    /** {@code current} is null when the session is no longer STARTED. */
    public GameSessionResponse toSessionResponse(GameSession session, SessionQuestion current) {
        Topic topic = session.getTopic();
        TopicSummaryResponse topicSummary = topic != null
                ? new TopicSummaryResponse(topic.getId(), topic.getName())
                : new TopicSummaryResponse(null, "All Topics");
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
