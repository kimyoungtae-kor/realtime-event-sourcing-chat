package com.sokind.chat.realtime.api;

import java.time.OffsetDateTime;

public record RealtimeErrorResponse(
	String code,
	String message,
	OffsetDateTime timestamp
) {
}
