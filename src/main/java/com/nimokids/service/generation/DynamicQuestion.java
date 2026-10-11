package com.nimokids.service.generation;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.QuestionVisual;
import java.util.Map;

/**
 * The part of a question that is decided per session (dynamic question, e.g. HOW_MANY): the correct answer item, the
 * condition that proves it, the picture to draw and an audit record. It travels through option generation and the
 * snapshot, so the picture and the answer used for grading can never disagree.
 *
 * @param correctItem  the vocabulary item that is the correct answer of THIS session question
 * @param correctMatch the tags the correct item carries; no wrong option may carry them
 * @param visual       what the client draws
 * @param audit        what was generated (generator, count, object), stored in the snapshot
 */
public record DynamicQuestion(AnswerItem correctItem, Map<String, Object> correctMatch, QuestionVisual visual,
                              Map<String, Object> audit) {
}
