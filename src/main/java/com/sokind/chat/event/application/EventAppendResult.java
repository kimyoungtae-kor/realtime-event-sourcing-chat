package com.sokind.chat.event.application;

import com.sokind.chat.event.domain.SessionEventEntity;

public record EventAppendResult(
	SessionEventEntity event,
	boolean duplicate
) {
}
