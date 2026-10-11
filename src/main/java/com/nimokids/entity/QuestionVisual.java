package com.nimokids.entity;

/**
 * Structured picture of a dynamic question, frozen in {@link QuestionSnapshot}. {@code COUNTING_OBJECTS}: draw
 * {@code count} separate copies of {@code objectImage} (an emoji today, an image URL later: same convention as
 * {@code questionImage}). The client only draws it: the answer is graded from the options snapshot.
 */
public record QuestionVisual(String type, String objectImage, int count) {

    public static final String COUNTING_OBJECTS = "COUNTING_OBJECTS";
}
