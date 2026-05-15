package com.sokind.chat.session.api;

import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.session.domain.SessionEntity;

import org.springframework.stereotype.Component;

@Component
public class SessionResponseMapper {

	public SessionResponse toResponse(SessionEntity session) {
		return new SessionResponse(
			session.getPublicId(),
			session.getStatus(),
			session.getNextSequence(),
			UtcDateTimes.toOffsetDateTime(session.getCreatedAt()),
			UtcDateTimes.toOffsetDateTime(session.getUpdatedAt()),
			UtcDateTimes.toOffsetDateTime(session.getEndedAt())
		);
	}
}
