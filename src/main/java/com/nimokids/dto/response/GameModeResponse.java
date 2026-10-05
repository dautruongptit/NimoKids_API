package com.nimokids.dto.response;

import java.util.UUID;

public record GameModeResponse(UUID id, String code, String name, String description) {
}
