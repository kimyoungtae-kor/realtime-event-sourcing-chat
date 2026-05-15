package com.sokind.chat.timeline.api;

import java.time.OffsetDateTime;

public record TimelineMessageResponse(
	String messageId,
	String senderId,
	String content,
	long serverSequence,
	OffsetDateTime createdAt,
	String status
) {
}
