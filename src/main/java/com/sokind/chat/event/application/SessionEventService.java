package com.sokind.chat.event.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.common.json.JsonPayloadMapper;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.domain.EventType;
import com.sokind.chat.event.domain.SessionEventEntity;
import com.sokind.chat.event.infrastructure.SessionEventRepository;
import com.sokind.chat.participant.domain.SessionParticipantEntity;
import com.sokind.chat.participant.domain.ParticipantState;
import com.sokind.chat.participant.infrastructure.SessionParticipantRepository;
import com.sokind.chat.session.domain.SessionEntity;
import com.sokind.chat.session.domain.SessionStatus;
import com.sokind.chat.session.infrastructure.SessionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionEventService {

	private static final String SYSTEM_SENDER = "system";

	private final SessionRepository sessionRepository;
	private final SessionEventRepository eventRepository;
	private final SessionParticipantRepository participantRepository;
	private final JsonPayloadMapper jsonPayloadMapper;

	public SessionEventService(
		SessionRepository sessionRepository,
		SessionEventRepository eventRepository,
		SessionParticipantRepository participantRepository,
		JsonPayloadMapper jsonPayloadMapper
	) {
		this.sessionRepository = sessionRepository;
		this.eventRepository = eventRepository;
		this.participantRepository = participantRepository;
		this.jsonPayloadMapper = jsonPayloadMapper;
	}

	@Transactional
	public EventAppendResult appendEvent(String sessionPublicId, EventAppendCommand command) {
		SessionEntity session = sessionRepository.findByPublicIdForUpdate(sessionPublicId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다"));
		return appendToLockedSession(session, command);
	}

	public EventAppendResult appendToLockedSession(SessionEntity session, EventAppendCommand command) {
		validateCommand(command);

		return eventRepository.findBySessionIdAndSenderIdAndClientEventId(
				session.getId(),
				command.senderId(),
				command.clientEventId()
			)
			.map(existing -> new EventAppendResult(existing, true))
			.orElseGet(() -> appendNewEvent(session, command));
	}

	public EventAppendCommand serverCreatedCommand(String sessionPublicId) {
		return new EventAppendCommand(
			EventType.SESSION_CREATED,
			SYSTEM_SENDER,
			"server-session-created-" + sessionPublicId,
			null,
			Map.of("sessionId", sessionPublicId)
		);
	}

	private EventAppendResult appendNewEvent(SessionEntity session, EventAppendCommand command) {
		LocalDateTime now = UtcDateTimes.now();
		ensureSessionCanAccept(session, command.type());
		validateCurrentState(session, command);

		long sequence = session.allocateNextSequence(now);
		SessionEventEntity event = new SessionEventEntity(
			session,
			sequence,
			command.type(),
			command.senderId(),
			command.clientEventId(),
			UtcDateTimes.toUtcLocalDateTime(command.clientSentAt()),
			now,
			jsonPayloadMapper.toJson(command.payload())
		);
		SessionEventEntity savedEvent = eventRepository.save(event);
		applyCurrentState(session, command, now);
		return new EventAppendResult(savedEvent, false);
	}

	private void validateCommand(EventAppendCommand command) {
		if (command == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "이벤트 요청이 필요합니다");
		}
		if (command.type() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "이벤트 타입이 필요합니다");
		}
		if (isBlank(command.senderId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "senderId가 필요합니다");
		}
		if (isBlank(command.clientEventId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "clientEventId가 필요합니다");
		}
		if (command.payload() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "payload가 필요합니다");
		}
	}

	private void ensureSessionCanAccept(SessionEntity session, EventType type) {
		if (session.getStatus() == SessionStatus.COMPLETED) {
			throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED", "종료된 세션에는 새 이벤트를 저장할 수 없습니다");
		}
	}

	private void validateCurrentState(SessionEntity session, EventAppendCommand command) {
		if (command.type() == EventType.SESSION_CREATED || command.type() == EventType.SESSION_ENDED) {
			return;
		}
		if (command.type() == EventType.JOINED) {
			validateJoinState(session, command);
			return;
		}
		if (command.type() == EventType.MESSAGE_SENT && isBlank(Objects.toString(command.payload().get("content"), ""))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "메시지 내용(payload.content)이 필요합니다");
		}
		SessionParticipantEntity participant = participantRepository
			.findBySessionIdAndUserId(session.getId(), command.senderId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTICIPANT_NOT_JOINED", "이벤트를 보내기 전에 먼저 입장해야 합니다"));

		if (participant.getState() == ParticipantState.LEFT) {
			throw new ApiException(HttpStatus.CONFLICT, "PARTICIPANT_LEFT", "이미 퇴장한 참여자입니다");
		}
	}

	private void validateJoinState(SessionEntity session, EventAppendCommand command) {
		participantRepository.findBySessionIdAndUserId(session.getId(), command.senderId())
			.filter(participant -> participant.getState() != ParticipantState.LEFT)
			.ifPresent(participant -> {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"PARTICIPANT_ALREADY_JOINED",
					"이미 입장한 참여자입니다"
				);
			});
	}

	private void applyCurrentState(SessionEntity session, EventAppendCommand command, LocalDateTime now) {
		switch (command.type()) {
			case SESSION_CREATED -> {
			}
			case JOINED -> upsertJoinedParticipant(session, command, now);
			case LEFT -> participantRepository.findBySessionIdAndUserId(session.getId(), command.senderId())
				.ifPresent(participant -> participant.markLeft(now));
			case MESSAGE_SENT -> participantRepository.findBySessionIdAndUserId(session.getId(), command.senderId())
				.ifPresent(participant -> participant.touch(now));
			case DISCONNECTED -> {
				session.interrupt(now);
				participantRepository.findBySessionIdAndUserId(session.getId(), command.senderId())
					.ifPresent(participant -> participant.markDisconnected(now));
			}
			case RECONNECTED -> {
				session.activate(now);
				participantRepository.findBySessionIdAndUserId(session.getId(), command.senderId())
					.ifPresent(participant -> participant.markReconnected(now));
			}
			case SESSION_ENDED -> session.complete(now);
		}
	}

	private void upsertJoinedParticipant(SessionEntity session, EventAppendCommand command, LocalDateTime now) {
		String displayName = Objects.toString(command.payload().getOrDefault("displayName", command.senderId()), command.senderId());
		SessionParticipantEntity participant = participantRepository
			.findBySessionIdAndUserId(session.getId(), command.senderId())
			.orElseGet(() -> SessionParticipantEntity.join(session, command.senderId(), displayName, now));
		participant.markJoined(displayName, now);
		participantRepository.save(participant);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
