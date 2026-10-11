package com.nimokids.service;

import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.service.generation.DynamicQuestion;
import java.util.List;

public interface OptionGenerator {

    /**
     * Builds the 4 options of a question: the correct answer item plus 3 distractors chosen by explicit tag matching,
     * shuffled and given display orders 1..4. The options are language-neutral (canonical item name): the
     * language is applied afterwards by {@link LanguageResolverService}.
     *
     * @throws com.nimokids.exception.InvalidDistractorRulesException  when the rules are missing or malformed
     * @throws com.nimokids.exception.InsufficientDistractorsException when a rule cannot supply enough distinct items
     */
    List<SnapshotOption> generate(GameQuestion question);

    /**
     * Same, for a dynamic question: the correct item and the condition that proves it come from {@code dynamic} (decided
     * for this session) instead of the question row. The wrong answers are still checked against that condition.
     */
    List<SnapshotOption> generate(GameQuestion question, DynamicQuestion dynamic);
}
