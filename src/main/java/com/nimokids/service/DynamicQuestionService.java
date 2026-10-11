package com.nimokids.service;

import com.nimokids.entity.GameQuestion;
import com.nimokids.service.generation.DynamicQuestion;
import java.util.Optional;

/** Decides, when a session is created, the correct answer and the picture of a question that has a generator. */
public interface DynamicQuestionService {

    /**
     * @return empty for an ordinary question (fixed correct answer item)
     * @throws com.nimokids.exception.InvalidDistractorRulesException when the generator is malformed or the data it
     *         needs is missing; the session skips such a question and takes another one
     */
    Optional<DynamicQuestion> prepare(GameQuestion question);
}
