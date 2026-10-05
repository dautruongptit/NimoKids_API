package com.nimokids.dto.response;

import java.util.UUID;

/** An answer option as shown to the child. Deliberately has no "isCorrect": the server decides correctness. */
public record OptionResponse(UUID id, String text, String image, String voice) {
}
