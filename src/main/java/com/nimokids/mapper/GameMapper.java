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
import com.nimokids.entity.QuestionOption;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.Sticker;
import com.nimokids.entity.Topic;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Entity to DTO mapping only. Contains no business rule. */
@Component
public class GameMapper {

    public TopicResponse toTopicResponse(Topic topic) {
        return new TopicResponse(
                topic.getId(),
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

    /** The question as shown to the child: it never reveals which option is correct. */
    public QuestionResponse toQuestionResponse(GameQuestion question) {
        return new QuestionResponse(
                question.getId(),
                question.getQuestionText(),
                url(question.getQuestionVoice()),
                url(question.getObjectSound()),
                question.getOptions().stream()
                        .map(option -> new OptionResponse(
                                option.getId(), option.getOptionText(), url(option.getImage()), url(option.getVoice())))
                        .toList());
    }

    /** {@code currentQuestion} is null when the session is no longer STARTED. */
    public GameSessionResponse toSessionResponse(GameSession session, GameQuestion currentQuestion) {
        Topic topic = session.getTopic();
        return new GameSessionResponse(
                session.getSessionId(),
                session.getStatus(),
                new TopicSummaryResponse(topic.getId(), topic.getName()),
                session.getTotalQuestions().intValue(),
                session.getCurrentQuestionNumber().intValue(),
                currentQuestion == null ? null : currentQuestion.getTimeLimitSeconds().intValue(),
                currentQuestion == null ? null : toQuestionResponse(currentQuestion));
    }

    public NextQuestionResponse toNextQuestionResponse(SessionQuestion sessionQuestion) {
        GameQuestion question = sessionQuestion.getQuestion();
        return new NextQuestionResponse(
                sessionQuestion.getQuestionNumber().intValue(),
                question.getTimeLimitSeconds().intValue(),
                toQuestionResponse(question));
    }

    public CorrectAnswerResponse toCorrectAnswerResponse(QuestionOption option) {
        return new CorrectAnswerResponse(option.getId(), option.getOptionText(), url(option.getVoice()));
    }

    public StickerResponse toStickerResponse(Sticker sticker, Instant earnedAt) {
        return new StickerResponse(
                sticker.getCode(), sticker.getName(), url(sticker.getImage()), sticker.getRarity(), earnedAt);
    }

    private static String url(MediaAsset asset) {
        return asset == null ? null : asset.getStorageUrl();
    }
}
