package com.nimokids.dto.response;

/**
 * What to draw for a dynamic question, e.g. {"type":"COUNTING_OBJECTS","objectImage":"🍎","count":3} = draw 3 apples.
 * {@code objectImage} is an emoji or an image URL (same convention as {@code questionImage}).
 */
public record QuestionVisualResponse(String type, String objectImage, int count) {
}
