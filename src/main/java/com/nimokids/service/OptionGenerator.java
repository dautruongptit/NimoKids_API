package com.nimokids.service;

import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.SnapshotOption;
import java.util.List;

public interface OptionGenerator {

    /**
     * Builds the 4 options of a question: the correct answer item plus 3 distractors chosen by explicit tag matching,
     * shuffled and given display orders 1..4. The result is what gets stored in {@code options_snapshot}.
     *
     * @throws com.nimokids.exception.InvalidDistractorRulesException  when the rules are missing or malformed
     * @throws com.nimokids.exception.InsufficientDistractorsException when a rule cannot supply enough distinct items
     */
    List<SnapshotOption> generate(GameQuestion question);
}
