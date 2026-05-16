package com.sokind.chat.timeline.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.sokind.chat.timeline.api.SnapshotResponse;
import com.sokind.chat.timeline.domain.SessionSnapshotEntity;
import com.sokind.chat.timeline.infrastructure.SessionSnapshotRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineReplayService {

	private final SessionRepository sessionRepository;
	private final SessionEventRepository eventRepository;
	private final SessionSnapshotRepository snapshotRepository;
	private final JsonPayloadMapper jsonPayloadMapper;

	public TimelineReplayService(
		SessionRepository sessionRepository,
		SessionEventRepository eventRepository,
		SessionSnapshotRepository snapshotRepository,
		JsonPayloadMapper jsonPayloadMapper
	) {
		this.sessionRepository = sessionRepository;
		this.eventRepository = eventRepository;
		this.snapshotRepository = snapshotRepository;
		this.jsonPayloadMapper = jsonPayloadMapper;
	}

	@Transactional(readOnly = true)
	public TimelineResponse restore(String sessionPublicId, OffsetDateTime at, int messageLimit) {
		if (at == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMELINE_REQUEST", "복원 기준 시점(at)이 필요합니다");
		}
		SessionEntity session = sessionRepository.findByPublicId(sessionPublicId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다"));
		LocalDateTime atUtc = UtcDateTimes.toUtcLocalDateTime(at);
		SessionSnapshotEntity snapshot = snapshotRepository
			.findFirstBySessionIdAndSnapshotAtLessThanEqualOrderByServerSequenceDesc(session.getId(), atUtc)
			.orElse(null);

		ReplayState state;
		List<SessionEventEntity> events;
		ReplayState snapshotState = restoreSnapshotState(snapshot);
		boolean restoredFromSnapshot = snapshotState != null;
		if (restoredFromSnapshot) {
			state = snapshotState;
			events = eventRepository.findReplayEventsAfterSequence(session.getId(), snapshot.getServerSequence(), atUtc);
		}
		else {
			events = eventRepository.findReplayEvents(session.getId(), atUtc);
			state = new ReplayState(sessionPublicId);
		}

		replayInto(state, events);
		state.keepRecentMessages(Math.max(1, messageLimit));
		return new TimelineResponse(
			sessionPublicId,
			state.status,
			UtcDateTimes.toOffsetDateTime(atUtc),
			state.participants(),
			state.messages(),
			events.size(),
			restoredFromSnapshot
		);
	}

	private ReplayState restoreSnapshotState(SessionSnapshotEntity snapshot) {
		if (snapshot == null) {
			return null;
		}
		try {
			return ReplayState.fromSnapshot(jsonPayloadMapper.fromJson(snapshot.getStateJson(), SnapshotState.class));
		}
		catch (RuntimeException exception) {
			// Snapshot은 복원 최적화 캐시이므로 읽기 실패 시 event log full replay로 복구한다.
			return null;
		}
	}

	@Transactional
	public SnapshotResponse createSnapshot(String sessionPublicId) {
		SessionEntity session = sessionRepository.findByPublicId(sessionPublicId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다"));
		SessionEventEntity latestEvent = eventRepository.findFirstBySessionIdOrderByServerSequenceDesc(session.getId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SNAPSHOT_NOT_AVAILABLE", "스냅샷을 만들 이벤트가 없습니다"));

		return snapshotRepository.findBySessionIdAndServerSequence(session.getId(), latestEvent.getServerSequence())
			.map(existing -> toSnapshotResponse(existing, true))
			.orElseGet(() -> createNewSnapshot(session, latestEvent));
	}

	private SnapshotResponse createNewSnapshot(SessionEntity session, SessionEventEntity latestEvent) {
		List<SessionEventEntity> events = eventRepository.findReplayEventsThroughSequence(
			session.getId(),
			latestEvent.getServerSequence()
		);
		ReplayState state = new ReplayState(session.getPublicId());
		replayInto(state, events);
		LocalDateTime now = UtcDateTimes.now();
		SessionSnapshotEntity snapshot = new SessionSnapshotEntity(
			session,
			latestEvent.getServerSequence(),
			latestEvent.getServerReceivedAt(),
			jsonPayloadMapper.toJson(state.toSnapshotState()),
			now
		);
		return toSnapshotResponse(snapshotRepository.save(snapshot), false);
	}

	private SnapshotResponse toSnapshotResponse(SessionSnapshotEntity snapshot, boolean reused) {
		return new SnapshotResponse(
			snapshot.getId(),
			snapshot.getSession().getPublicId(),
			snapshot.getServerSequence(),
			UtcDateTimes.toOffsetDateTime(snapshot.getSnapshotAt()),
			UtcDateTimes.toOffsetDateTime(snapshot.getCreatedAt()),
			reused
		);
	}

	private void replayInto(ReplayState state, List<SessionEventEntity> events) {
		for (SessionEventEntity event : events) {
			Map<String, Object> payload = jsonPayloadMapper.toMap(event.getPayloadJson());
			state.apply(event, payload);
		}
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

		private SnapshotState toSnapshotState() {
			return new SnapshotState(
				sessionPublicId,
				status,
				participants.values()
					.stream()
					.map(MutableParticipant::toSnapshot)
					.toList(),
				messages.stream()
					.map(message -> new SnapshotMessage(
						message.messageId(),
						message.senderId(),
						message.content(),
						message.serverSequence(),
						encode(message.createdAt()),
						message.status()
					))
					.toList()
			);
		}

		private static ReplayState fromSnapshot(SnapshotState snapshot) {
			if (snapshot == null || snapshot.sessionId() == null || snapshot.status() == null) {
				throw new IllegalArgumentException("Snapshot state is incomplete");
			}
			ReplayState state = new ReplayState(snapshot.sessionId());
			state.status = snapshot.status();
			for (SnapshotParticipant participant : emptyIfNull(snapshot.participants())) {
				state.participants.put(
					participant.userId(),
					MutableParticipant.fromSnapshot(participant)
				);
			}
			for (SnapshotMessage message : emptyIfNull(snapshot.messages())) {
				state.messages.add(new TimelineMessageResponse(
					message.messageId(),
					message.senderId(),
					message.content(),
					message.serverSequence(),
					UtcDateTimes.toOffsetDateTime(decode(message.createdAt())),
					message.status()
				));
			}
			return state;
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

		private static MutableParticipant fromSnapshot(SnapshotParticipant snapshot) {
			MutableParticipant participant = new MutableParticipant(
				snapshot.userId(),
				snapshot.displayName(),
				snapshot.state(),
				decode(snapshot.joinedAt())
			);
			participant.leftAt = decode(snapshot.leftAt());
			participant.lastSeenAt = decode(snapshot.lastSeenAt());
			return participant;
		}

		private SnapshotParticipant toSnapshot() {
			return new SnapshotParticipant(
				userId,
				displayName,
				state,
				encode(joinedAt),
				encode(leftAt),
				encode(lastSeenAt)
			);
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

	private record SnapshotState(
		String sessionId,
		SessionStatus status,
		List<SnapshotParticipant> participants,
		List<SnapshotMessage> messages
	) {
	}

	private record SnapshotParticipant(
		String userId,
		String displayName,
		ParticipantState state,
		String joinedAt,
		String leftAt,
		String lastSeenAt
	) {
	}

	private record SnapshotMessage(
		String messageId,
		String senderId,
		String content,
		long serverSequence,
		String createdAt,
		String status
	) {
	}

	private static String encode(LocalDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.atOffset(ZoneOffset.UTC).toString();
	}

	private static String encode(OffsetDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.toInstant()
			.atOffset(ZoneOffset.UTC)
			.toString();
	}

	private static LocalDateTime decode(String dateTime) {
		if (dateTime == null || dateTime.isBlank()) {
			return null;
		}
		return LocalDateTime.ofInstant(OffsetDateTime.parse(dateTime).toInstant(), ZoneOffset.UTC);
	}

	private static <T> List<T> emptyIfNull(List<T> values) {
		if (values == null) {
			return List.of();
		}
		return values;
	}
}
