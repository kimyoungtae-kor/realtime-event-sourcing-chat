package com.sokind.chat;

import java.time.OffsetDateTime;
import java.util.Map;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.event.application.EventAppendCommand;
import com.sokind.chat.event.application.EventAppendResult;
import com.sokind.chat.event.application.SessionEventService;
import com.sokind.chat.event.domain.EventType;
import com.sokind.chat.event.infrastructure.SessionEventRepository;
import com.sokind.chat.participant.domain.ParticipantState;
import com.sokind.chat.participant.infrastructure.SessionParticipantRepository;
import com.sokind.chat.session.application.SessionService;
import com.sokind.chat.session.domain.SessionEntity;
import com.sokind.chat.session.domain.SessionStatus;
import com.sokind.chat.session.infrastructure.SessionRepository;
import com.sokind.chat.timeline.api.SnapshotResponse;
import com.sokind.chat.timeline.api.TimelineResponse;
import com.sokind.chat.timeline.application.TimelineReplayService;
import com.sokind.chat.timeline.infrastructure.SessionSnapshotRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class EventSourcingIntegrationTests {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SessionEventService eventService;

	@Autowired
	private TimelineReplayService timelineReplayService;

	@Autowired
	private SessionEventRepository eventRepository;

	@Autowired
	private SessionSnapshotRepository snapshotRepository;

	@Autowired
	private SessionParticipantRepository participantRepository;

	@Autowired
	private SessionRepository sessionRepository;

	@BeforeEach
	void cleanDatabase() {
		snapshotRepository.deleteAll();
		eventRepository.deleteAll();
		participantRepository.deleteAll();
		sessionRepository.deleteAll();
	}

	@Test
	void duplicateMessageIsNotStoredTwiceAndTimelineIsDeterministic() {
		SessionEntity session = sessionService.createSession();
		sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-001", null);

		EventAppendCommand command = new EventAppendCommand(
			EventType.MESSAGE_SENT,
			"user-a",
			"message-user-a-001",
			null,
			Map.of("messageId", "msg-001", "content", "hello")
		);

		EventAppendResult first = eventService.appendEvent(session.getPublicId(), command);
		EventAppendResult duplicate = eventService.appendEvent(session.getPublicId(), command);

		assertThat(first.duplicate()).isFalse();
		assertThat(duplicate.duplicate()).isTrue();
		assertThat(duplicate.event().getId()).isEqualTo(first.event().getId());
		assertThat(duplicate.event().getServerSequence()).isEqualTo(first.event().getServerSequence());
		assertThat(eventRepository.findAll()).hasSize(3);

		TimelineResponse timeline = timelineReplayService.restore(session.getPublicId(), OffsetDateTime.now().plusDays(1), 100);

		assertThat(timeline.restoredFromSnapshot()).isFalse();
		assertThat(timeline.appliedEventCount()).isEqualTo(3);
		assertThat(timeline.participants()).hasSize(1);
		assertThat(timeline.participants().getFirst().state()).isEqualTo(ParticipantState.ONLINE);
		assertThat(timeline.messages()).hasSize(1);
		assertThat(timeline.messages().getFirst().messageId()).isEqualTo("msg-001");
	}

	@Test
	void snapshotIsReusedAndTimelineReplaysOnlyEventsAfterSnapshot() {
		SessionEntity session = sessionService.createSession();
		sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-002", null);
		eventService.appendEvent(session.getPublicId(), message("message-before-snapshot", "msg-before", "before snapshot"));

		SnapshotResponse snapshot = timelineReplayService.createSnapshot(session.getPublicId());

		assertThat(snapshot.reused()).isFalse();
		assertThat(snapshot.serverSequence()).isEqualTo(3);
		assertThat(snapshotRepository.findAll()).hasSize(1);

		SnapshotResponse duplicateSnapshot = timelineReplayService.createSnapshot(session.getPublicId());

		assertThat(duplicateSnapshot.reused()).isTrue();
		assertThat(duplicateSnapshot.snapshotId()).isEqualTo(snapshot.snapshotId());
		assertThat(snapshotRepository.findAll()).hasSize(1);

		eventService.appendEvent(session.getPublicId(), message("message-after-snapshot", "msg-after", "after snapshot"));

		TimelineResponse timeline = timelineReplayService.restore(session.getPublicId(), OffsetDateTime.now().plusDays(1), 100);

		assertThat(timeline.restoredFromSnapshot()).isTrue();
		assertThat(timeline.appliedEventCount()).isEqualTo(1);
		assertThat(timeline.messages()).extracting("messageId")
			.containsExactly("msg-before", "msg-after");
	}

	@Test
	void joinIsIdempotentAndRejoinIsAllowedAfterLeave() {
		SessionEntity session = sessionService.createSession();

		EventAppendResult firstJoin = sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-004", null);
		EventAppendResult duplicateJoin = sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-004", null);

		assertThat(firstJoin.duplicate()).isFalse();
		assertThat(duplicateJoin.duplicate()).isTrue();

		assertThatThrownBy(() -> sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-005", null))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("이미 입장한 참여자입니다");

		sessionService.leave(session.getPublicId(), "user-a", "leave-user-a-001", null);

		EventAppendResult rejoin = sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-006", null);

		assertThat(rejoin.duplicate()).isFalse();
	}

	@Test
	void completedSessionRejectsNewEvents() {
		SessionEntity session = sessionService.createSession();
		sessionService.join(session.getPublicId(), "user-a", "User A", "join-user-a-003", null);
		sessionService.end(session.getPublicId(), "user-a", "end-session-001", null);

		assertThatThrownBy(() -> eventService.appendEvent(
			session.getPublicId(),
			message("message-after-end", "msg-after-end", "should fail")
		))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("종료된 세션에는 새 이벤트를 저장할 수 없습니다");

		TimelineResponse timeline = timelineReplayService.restore(session.getPublicId(), OffsetDateTime.now().plusDays(1), 100);
		assertThat(timeline.status()).isEqualTo(SessionStatus.COMPLETED);
	}

	private EventAppendCommand message(String clientEventId, String messageId, String content) {
		return new EventAppendCommand(
			EventType.MESSAGE_SENT,
			"user-a",
			clientEventId,
			null,
			Map.of("messageId", messageId, "content", content)
		);
	}
}
