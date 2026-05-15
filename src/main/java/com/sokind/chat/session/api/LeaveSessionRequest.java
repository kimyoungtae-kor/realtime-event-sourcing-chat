package com.sokind.chat.session.api;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;

public record LeaveSessionRequest(
	@NotBlank String userId,
	@NotBlank String clientEventId,
	OffsetDateTime clientSentAt
) {
}
