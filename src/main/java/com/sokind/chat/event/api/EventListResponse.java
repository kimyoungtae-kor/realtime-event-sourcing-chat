package com.sokind.chat.event.api;

import java.util.List;

public record EventListResponse(
	List<EventResponse> events
) {
}
