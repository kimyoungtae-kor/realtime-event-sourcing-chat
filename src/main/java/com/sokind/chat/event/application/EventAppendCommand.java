package com.sokind.chat.event.application;

import java.time.OffsetDateTime;
import java.util.Map;

import com.sokind.chat.event.domain.EventType;

public record EventAppendCommand(
	EventType type,
	String senderId,
	String clientEventId,
	OffsetDateTime clientSentAt,
	Map<String, Object> payload
) {
}
