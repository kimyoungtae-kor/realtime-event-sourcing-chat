package com.sokind.chat.timeline.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.sokind.chat.session.domain.SessionStatus;

public record TimelineResponse(
	String sessionId,
	SessionStatus status,
	OffsetDateTime at,
	List<TimelineParticipantResponse> participants,
	List<TimelineMessageResponse> messages,
	int appliedEventCount,
	boolean restoredFromSnapshot
) {
}
