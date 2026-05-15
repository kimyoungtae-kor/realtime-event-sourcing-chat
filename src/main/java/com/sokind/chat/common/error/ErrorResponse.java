package com.sokind.chat.common.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
	String code,
	String message,
	OffsetDateTime timestamp
) {
}
