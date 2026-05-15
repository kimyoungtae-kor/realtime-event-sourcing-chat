package com.sokind.chat.timeline.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.common.json.JsonPayloadMapper;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.domain.EventType;
import com.sokind.chat.event.domain.SessionEventEntity;
import com.sokind.chat.event.infrastructure.SessionEventRepository;
import com.sokind.chat.participant.domain.ParticipantState;
import com.sokind.chat.session.domain.SessionEntity;
import com.sokind.chat.session.domain.SessionStatus;
import com.sokind.chat.session.infrastructure.SessionRepository;
import com.sokind.chat.timeline.api.TimelineMessageResponse;
import com.sokind.chat.timeline.api.TimelineParticipantResponse;
import com.sokind.chat.timeline.api.TimelineResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineReplayService {

	private final SessionRepository sessionRepository;
	private final SessionEventRepository eventRepository;
	private final JsonPayloadMapper jsonPayloadMapper;

	public TimelineReplayService(
		SessionRepository sessionRepository,
		SessionEventRepository eventRepository,
		JsonPayloadMapper jsonPayloadMapper
	) {
		this.sessionRepository = sessionRepository;
		this.eventRepository = eventRepository;
		this.jsonPayloadMapper = jsonPayloadMapper;
	}

	@Transactional(readOnly = true)
	public TimelineResponse restore(String sessionPublicId, OffsetDateTime at, int messageLimit) {
		if (at == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMELINE_REQUEST", "at is required");
		}
		SessionEntity session = sessionRepository.findByPublicId(sessionPublicId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
		LocalDateTime atUtc = UtcDateTimes.toUtcLocalDateTime(at);
		List<SessionEventEntity> events = eventRepository.findReplayEvents(session.getId(), atUtc);

		ReplayState state = replay(sessionPublicId, events, Math.max(1, messageLimit));
		return new TimelineResponse(
			sessionPublicId,
			state.status,
			UtcDateTimes.toOffsetDateTime(atUtc),
			state.participants(),
			state.messages(),
			events.size(),
			false
		);
	}

	private ReplayState replay(String sessionPublicId, List<SessionEventEntity> events, int messageLimit) {
		ReplayState state = new ReplayState(sessionPublicId);
		for (SessionEventEntity event : events) {
			Map<String, Object> payload = jsonPayloadMapper.toMap(event.getPayloadJson());
			state.apply(event, payload);
		}
		state.keepRecentMessages(messageLimit);
		return state;
	}

	private static final class ReplayState {

		private final String sessionPublicId;
		private SessionStatus status = SessionStatus.ACTIVE;
		private final Map<String, MutableParticipant> participants = new LinkedHashMap<>();
		private final List<TimelineMessageResponse> messages = new ArrayList<>();

		private ReplayState(String sessionPublicId) {
			this.sessionPublicId = sessionPublicId;
		}

		private void apply(SessionEventEntity event, Map<String, Object> payload) {
			switch (event.getType()) {
				case SESSION_CREATED -> status = SessionStatus.ACTIVE;
				case JOINED -> join(event, payload);
				case LEFT -> updateParticipant(event.getSenderId(), participant -> participant.leave(event.getServerReceivedAt()));
				case MESSAGE_SENT -> addMessage(event, payload);
				case DISCONNECTED -> {
					status = SessionStatus.INTERRUPTED;
					updateParticipant(event.getSenderId(), participant -> participant.disconnect(event.getServerReceivedAt()));
				}
				case RECONNECTED -> {
					status = SessionStatus.ACTIVE;
					updateParticipant(event.getSenderId(), participant -> participant.reconnect(event.getServerReceivedAt()));
				}
				case SESSION_ENDED -> status = SessionStatus.COMPLETED;
			}
		}

		private void join(SessionEventEntity event, Map<String, Object> payload) {
			String displayName = Objects.toString(payload.getOrDefault("displayName", event.getSenderId()), event.getSenderId());
			participants.compute(event.getSenderId(), (userId, current) -> {
				if (current == null) {
					return MutableParticipant.joined(userId, displayName, event.getServerReceivedAt());
				}
				current.rejoin(displayName, event.getServerReceivedAt());
				return current;
			});
		}

		private void addMessage(SessionEventEntity event, Map<String, Object> payload) {
			String messageId = Objects.toString(
				payload.getOrDefault("messageId", sessionPublicId + "-" + event.getServerSequence())
			);
			String content = Objects.toString(payload.getOrDefault("content", ""));
			messages.add(new TimelineMessageResponse(
				messageId,
				event.getSenderId(),
				content,
				event.getServerSequence(),
				UtcDateTimes.toOffsetDateTime(event.getServerReceivedAt()),
				"SENT"
			));
		}

		private void updateParticipant(String userId, ParticipantUpdater updater) {
			MutableParticipant participant = participants.get(userId);
			if (participant != null) {
				updater.update(participant);
			}
		}

		private void keepRecentMessages(int limit) {
			if (messages.size() <= limit) {
				return;
			}
			List<TimelineMessageResponse> recent = new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
			messages.clear();
			messages.addAll(recent);
		}

		private List<TimelineParticipantResponse> participants() {
			return participants.values()
				.stream()
				.map(MutableParticipant::toResponse)
				.toList();
		}

		private List<TimelineMessageResponse> messages() {
			return List.copyOf(messages);
		}
	}

	@FunctionalInterface
	private interface ParticipantUpdater {
		void update(MutableParticipant participant);
	}

	private static final class MutableParticipant {

		private final String userId;
		private String displayName;
		private ParticipantState state;
		private LocalDateTime joinedAt;
		private LocalDateTime leftAt;
		private LocalDateTime lastSeenAt;

		private MutableParticipant(String userId, String displayName, ParticipantState state, LocalDateTime joinedAt) {
			this.userId = userId;
			this.displayName = displayName;
			this.state = state;
			this.joinedAt = joinedAt;
			this.lastSeenAt = joinedAt;
		}

		private static MutableParticipant joined(String userId, String displayName, LocalDateTime joinedAt) {
			return new MutableParticipant(userId, displayName, ParticipantState.ONLINE, joinedAt);
		}

		private void rejoin(String displayName, LocalDateTime at) {
			this.displayName = displayName;
			this.state = ParticipantState.ONLINE;
			this.leftAt = null;
			this.lastSeenAt = at;
		}

		private void leave(LocalDateTime at) {
			this.state = ParticipantState.LEFT;
			this.leftAt = at;
			this.lastSeenAt = at;
		}

		private void disconnect(LocalDateTime at) {
			if (state != ParticipantState.LEFT) {
				this.state = ParticipantState.OFFLINE;
				this.lastSeenAt = at;
			}
		}

		private void reconnect(LocalDateTime at) {
			if (state != ParticipantState.LEFT) {
				this.state = ParticipantState.ONLINE;
				this.lastSeenAt = at;
			}
		}

		private TimelineParticipantResponse toResponse() {
			return new TimelineParticipantResponse(
				userId,
				displayName,
				state,
				UtcDateTimes.toOffsetDateTime(joinedAt),
				UtcDateTimes.toOffsetDateTime(leftAt),
				UtcDateTimes.toOffsetDateTime(lastSeenAt)
			);
		}
	}
}
