package com.sokind.chat.session.api;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;

public record EndSessionRequest(
	@NotBlank String endedBy,
	@NotBlank String clientEventId,
	OffsetDateTime clientSentAt
) {
}
