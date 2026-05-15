package com.sokind.chat.event.api;

import java.time.OffsetDateTime;
import java.util.Map;

import com.sokind.chat.event.domain.EventType;

public record EventResponse(
	Long eventId,
	String sessionId,
	long serverSequence,
	EventType type,
	String senderId,
	String clientEventId,
	OffsetDateTime clientSentAt,
	OffsetDateTime serverReceivedAt,
	Map<String, Object> payload,
	boolean duplicate
) {
}
