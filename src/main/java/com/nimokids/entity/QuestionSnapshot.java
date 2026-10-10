package com.nimokids.entity;

import com.nimokids.service.language.ResolvedQuestion;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the child was shown for one question, frozen when the question enters a session and stored as JSON in
 * {@code session_questions.question_snapshot} (rules 9.7: "changing an AnswerItem or a Generation Rule in master data
 * must NOT change the question of a running session or the session history").
 *
 * The 4 options and the correct answer are frozen separately in {@code options_snapshot} ({@link SnapshotOption}).
 * Sessions created before V9 have no snapshot (null): the mapper then falls back to the live question.
 *
 * @param questionKey      stable key of the question (template / key of the bank)
 * @param questionText     the text in the session language (see {@code languageMode})
 * @param questionTextVi   Vietnamese subtitle of the question (VI_EN only), else null
 * @param questionImage    picture of the answer item (an emoji today, an URL later)
 * @param generation       the rules that produced the options (distractor_rules, correct_match), for audit
 */
public record QuestionSnapshot(
        String questionKey,
        String questionType,
        String ageGroup,
        int difficulty,
        UUID topicId,
        String topicCode,
        String questionText,
        String questionTextVi,
        String questionVoiceUrl,
        String objectSoundUrl,
        String questionImage,
        UUID correctAnswerItemId,
        String languageMode,
        Map<String, Object> generation,
        String templateCode,
        String instructionLanguage,
        String answerLanguage) {

    /** Freezes the question as the child sees it: text and audio already resolved for the session language. */
    public static QuestionSnapshot of(GameQuestion question, ResolvedQuestion resolved) {
        AnswerItem correct = question.getCorrectAnswerItem();
        Object emoji = correct != null && correct.getMetadata() != null ? correct.getMetadata().get("emoji") : null;
        Map<String, Object> metadata = question.getMetadata() != null ? question.getMetadata() : Map.of();
        Map<String, Object> generation = new java.util.LinkedHashMap<>();
        if (metadata.get("distractor_rules") != null) {
            generation.put("distractor_rules", metadata.get("distractor_rules"));
        }
        if (metadata.get("correct_match") != null) {
            generation.put("correct_match", metadata.get("correct_match"));
        }
        return new QuestionSnapshot(
                question.getQuestionKey(),
                question.getQuestionType() != null ? question.getQuestionType().name() : null,
                question.getAgeGroup() != null ? question.getAgeGroup().name() : null,
                question.getDifficulty() != null ? question.getDifficulty().intValue() : 1,
                question.getTopic() != null ? question.getTopic().getId() : null,
                question.getTopic() != null ? question.getTopic().getCode() : null,
                resolved.questionText(),
                resolved.questionTextVi(),
                resolved.questionAudioUrl(),
                question.getObjectSound() != null ? question.getObjectSound().getStorageUrl() : null,
                emoji instanceof String s ? s : null,
                correct != null ? correct.getId() : null,
                resolved.languageMode().name(),
                generation,
                question.getTemplate() != null ? question.getTemplate().getCode() : null,
                resolved.instructionLanguage(),
                resolved.answerLanguage());
    }
}
