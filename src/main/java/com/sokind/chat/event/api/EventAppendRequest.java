package com.sokind.chat.event.api;

import java.time.OffsetDateTime;
import java.util.Map;

import com.sokind.chat.event.domain.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventAppendRequest(
	@NotNull EventType type,
	@NotBlank String senderId,
	@NotBlank String clientEventId,
	OffsetDateTime clientSentAt,
	@NotNull Map<String, Object> payload
) {
}
