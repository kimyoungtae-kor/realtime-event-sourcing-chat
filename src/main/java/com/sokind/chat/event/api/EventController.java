package com.sokind.chat.event.api;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.application.EventAppendCommand;
import com.sokind.chat.event.application.EventAppendResult;
import com.sokind.chat.event.application.SessionEventService;
import com.sokind.chat.event.domain.EventType;
import com.sokind.chat.event.infrastructure.SessionEventRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/sessions/{sessionId}/events")
public class EventController {

	private final SessionEventService sessionEventService;
	private final SessionEventRepository eventRepository;
	private final EventResponseMapper eventResponseMapper;

	public EventController(
		SessionEventService sessionEventService,
		SessionEventRepository eventRepository,
		EventResponseMapper eventResponseMapper
	) {
		this.sessionEventService = sessionEventService;
		this.eventRepository = eventRepository;
		this.eventResponseMapper = eventResponseMapper;
	}

	@PostMapping
	public ResponseEntity<EventResponse> appendEvent(
		@PathVariable UUID sessionId,
		@Valid @RequestBody EventAppendRequest request
	) {
		validatePublicAppendType(request.type());
		EventAppendResult result = sessionEventService.appendEvent(
			sessionId.toString(),
			new EventAppendCommand(
				request.type(),
				request.senderId(),
				request.clientEventId(),
				request.clientSentAt(),
				request.payload()
			)
		);
		EventResponse response = eventResponseMapper.toResponse(result.event(), result.duplicate());
		if (result.duplicate()) {
			return ResponseEntity.ok(response);
		}
		return ResponseEntity.created(URI.create("/sessions/" + sessionId + "/events/" + response.serverSequence()))
			.body(response);
	}

	@GetMapping
	public EventListResponse listEvents(
		@PathVariable UUID sessionId,
		@RequestParam(required = false) Long afterSequence,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
		@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
	) {
		List<EventResponse> events = eventRepository.search(
				sessionId.toString(),
				afterSequence,
				UtcDateTimes.toUtcLocalDateTime(from),
				UtcDateTimes.toUtcLocalDateTime(to),
				PageRequest.of(0, limit)
			)
			.stream()
			.map(event -> eventResponseMapper.toResponse(event, false))
			.toList();
		return new EventListResponse(events);
	}

	private void validatePublicAppendType(EventType type) {
		if (type != EventType.MESSAGE_SENT && type != EventType.DISCONNECTED && type != EventType.RECONNECTED) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"UNSUPPORTED_EVENT_TYPE",
				"Use dedicated session endpoints for " + type + " events"
			);
		}
	}
}
