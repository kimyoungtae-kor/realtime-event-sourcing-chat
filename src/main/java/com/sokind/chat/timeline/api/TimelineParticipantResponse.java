package com.sokind.chat.timeline.api;

import java.time.OffsetDateTime;

import com.sokind.chat.participant.domain.ParticipantState;

public record TimelineParticipantResponse(
	String userId,
	String displayName,
	ParticipantState state,
	OffsetDateTime joinedAt,
	OffsetDateTime leftAt,
	OffsetDateTime lastSeenAt
) {
}
