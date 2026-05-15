package com.sokind.chat.session.api;

import java.util.List;

public record SessionListResponse(
	List<SessionResponse> sessions
) {
}
