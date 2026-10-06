package com.nimokids.entity;

import java.util.UUID;

/**
 * One of the 4 options generated for a session question, stored as JSON in
 * {@code session_questions.options_snapshot}. This is the permanent evidence used for scoring: an answer is graded
 * by comparing the submitted option id with this snapshot, never with the live answer_items.
 *
 * {@code isCorrect} exists ONLY here, in the database. It must never be copied into an API response that carries
 * options.
 *
 * @param optionId     fresh UUID minted for this snapshot (the id the client sees and sends back)
 * @param answerItemId the vocabulary item behind the option (null for options migrated from the old static table)
 */
public record SnapshotOption(
        UUID optionId,
        UUID answerItemId,
        String text,
        String imageUrl,
        String voiceUrl,
        int displayOrder,
        boolean isCorrect) {
}
