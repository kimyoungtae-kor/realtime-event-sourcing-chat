package com.sokind.chat.session.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.application.EventAppendCommand;
import com.sokind.chat.event.application.EventAppendResult;
import com.sokind.chat.event.application.SessionEventService;
import com.sokind.chat.event.domain.EventType;
import com.sokind.chat.session.domain.SessionEntity;
import com.sokind.chat.session.domain.SessionStatus;
import com.sokind.chat.session.infrastructure.SessionRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

	private static final int MAX_LIST_LIMIT = 200;

	private final SessionRepository sessionRepository;
	private final SessionEventService sessionEventService;

	public SessionService(SessionRepository sessionRepository, SessionEventService sessionEventService) {
		this.sessionRepository = sessionRepository;
		this.sessionEventService = sessionEventService;
	}

	@Transactional
	public SessionEntity createSession() {
		SessionEntity session = sessionRepository.saveAndFlush(SessionEntity.create(UtcDateTimes.now()));
		sessionEventService.appendToLockedSession(session, sessionEventService.serverCreatedCommand(session.getPublicId()));
		return session;
	}

	@Transactional(readOnly = true)
	public List<SessionEntity> listSessions(
		SessionStatus status,
		String participantId,
		OffsetDateTime from,
		OffsetDateTime to,
		int limit
	) {
		int safeLimit = Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
		return sessionRepository.search(
			status,
			participantId,
			UtcDateTimes.toUtcLocalDateTime(from),
			UtcDateTimes.toUtcLocalDateTime(to),
			PageRequest.of(0, safeLimit)
		);
	}

	public EventAppendResult join(String sessionPublicId, String userId, String displayName, String clientEventId, OffsetDateTime clientSentAt) {
		return sessionEventService.appendEvent(
			sessionPublicId,
			new EventAppendCommand(
				EventType.JOINED,
				userId,
				clientEventId,
				clientSentAt,
				Map.of("displayName", displayName)
			)
		);
	}

	public EventAppendResult leave(String sessionPublicId, String userId, String clientEventId, OffsetDateTime clientSentAt) {
		return sessionEventService.appendEvent(
			sessionPublicId,
			new EventAppendCommand(EventType.LEFT, userId, clientEventId, clientSentAt, Map.of())
		);
	}

	public EventAppendResult end(String sessionPublicId, String endedBy, String clientEventId, OffsetDateTime clientSentAt) {
		if (endedBy == null || endedBy.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_END_REQUEST", "종료 요청자(endedBy)가 필요합니다");
		}
		return sessionEventService.appendEvent(
			sessionPublicId,
			new EventAppendCommand(EventType.SESSION_ENDED, endedBy, clientEventId, clientSentAt, Map.of("endedBy", endedBy))
		);
	}
}
