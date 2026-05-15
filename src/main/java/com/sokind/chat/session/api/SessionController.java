package com.sokind.chat.session.api;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.sokind.chat.event.api.EventResponse;
import com.sokind.chat.event.api.EventResponseMapper;
import com.sokind.chat.event.application.EventAppendResult;
import com.sokind.chat.session.application.SessionService;
import com.sokind.chat.session.domain.SessionStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/sessions")
public class SessionController {

	private final SessionService sessionService;
	private final SessionResponseMapper sessionResponseMapper;
	private final EventResponseMapper eventResponseMapper;

	public SessionController(
		SessionService sessionService,
		SessionResponseMapper sessionResponseMapper,
		EventResponseMapper eventResponseMapper
	) {
		this.sessionService = sessionService;
		this.sessionResponseMapper = sessionResponseMapper;
		this.eventResponseMapper = eventResponseMapper;
	}

	@PostMapping
	public ResponseEntity<SessionResponse> createSession(@RequestBody(required = false) CreateSessionRequest ignored) {
		SessionResponse response = sessionResponseMapper.toResponse(sessionService.createSession());
		return ResponseEntity.created(URI.create("/sessions/" + response.sessionId()))
			.body(response);
	}

	@GetMapping
	public SessionListResponse listSessions(
		@RequestParam(required = false) SessionStatus status,
		@RequestParam(required = false) String participantId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
		@RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
	) {
		List<SessionResponse> sessions = sessionService.listSessions(status, participantId, from, to, limit)
			.stream()
			.map(sessionResponseMapper::toResponse)
			.toList();
		return new SessionListResponse(sessions);
	}

	@PostMapping("/{sessionId}/join")
	public ResponseEntity<EventResponse> join(
		@PathVariable UUID sessionId,
		@Valid @RequestBody JoinSessionRequest request
	) {
		EventAppendResult result = sessionService.join(
			sessionId.toString(),
			request.userId(),
			request.displayName(),
			request.clientEventId(),
			request.clientSentAt()
		);
		return eventResponse(result);
	}

	@PostMapping("/{sessionId}/leave")
	public ResponseEntity<EventResponse> leave(
		@PathVariable UUID sessionId,
		@Valid @RequestBody LeaveSessionRequest request
	) {
		EventAppendResult result = sessionService.leave(
			sessionId.toString(),
			request.userId(),
			request.clientEventId(),
			request.clientSentAt()
		);
		return eventResponse(result);
	}

	@PostMapping("/{sessionId}/end")
	public ResponseEntity<EventResponse> end(
		@PathVariable UUID sessionId,
		@Valid @RequestBody EndSessionRequest request
	) {
		EventAppendResult result = sessionService.end(
			sessionId.toString(),
			request.endedBy(),
			request.clientEventId(),
			request.clientSentAt()
		);
		return eventResponse(result);
	}

	private ResponseEntity<EventResponse> eventResponse(EventAppendResult result) {
		EventResponse response = eventResponseMapper.toResponse(result.event(), result.duplicate());
		if (result.duplicate()) {
			return ResponseEntity.ok(response);
		}
		return ResponseEntity.status(201).body(response);
	}
}
