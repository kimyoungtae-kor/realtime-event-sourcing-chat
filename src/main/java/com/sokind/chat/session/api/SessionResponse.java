package com.sokind.chat.session.api;

import java.time.OffsetDateTime;

import com.sokind.chat.session.domain.SessionStatus;

public record SessionResponse(
	String sessionId,
	SessionStatus status,
	long nextSequence,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	OffsetDateTime endedAt
) {
}
