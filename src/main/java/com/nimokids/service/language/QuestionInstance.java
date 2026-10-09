package com.nimokids.service.language;

import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.SnapshotOption;
import java.util.List;

/**
 * A question just generated for a session, BEFORE any language is applied: the question (its template) and the 4
 * options chosen by the generator (correct answer + 3 distractors, shuffled). The options carry the canonical
 * English name of their answer item; {@link com.nimokids.service.LanguageResolverService} replaces text and audio by
 * the session language.
 */
public record QuestionInstance(GameQuestion question, List<SnapshotOption> options) {
}
