package com.sokind.chat.session.api;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;

public record JoinSessionRequest(
	@NotBlank String userId,
	@NotBlank String displayName,
	@NotBlank String clientEventId,
	OffsetDateTime clientSentAt
) {
}
