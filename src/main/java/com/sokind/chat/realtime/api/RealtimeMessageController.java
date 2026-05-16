package com.sokind.chat.realtime.api;

import java.util.UUID;

import com.sokind.chat.common.error.ApiException;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.api.EventAppendRequest;
import com.sokind.chat.event.api.EventResponse;
import com.sokind.chat.event.api.EventResponseMapper;
import com.sokind.chat.event.application.EventAppendCommand;
import com.sokind.chat.event.application.EventAppendResult;
import com.sokind.chat.event.application.SessionEventService;
import com.sokind.chat.event.domain.EventType;

import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class RealtimeMessageController {

	private final SessionEventService sessionEventService;
	private final EventResponseMapper eventResponseMapper;
	private final SimpMessagingTemplate messagingTemplate;

	public RealtimeMessageController(
		SessionEventService sessionEventService,
		EventResponseMapper eventResponseMapper,
		SimpMessagingTemplate messagingTemplate
	) {
		this.sessionEventService = sessionEventService;
		this.eventResponseMapper = eventResponseMapper;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/sessions/{sessionId}/messages")
	public void publishMessage(
		@DestinationVariable UUID sessionId,
		@Valid @Payload EventAppendRequest request
	) {
		if (request.type() != EventType.MESSAGE_SENT) {
			sendError(sessionId, "UNSUPPORTED_EVENT_TYPE", "Only MESSAGE_SENT is accepted on the messages channel");
			return;
		}
		appendAndBroadcast(sessionId, request);
	}

	@MessageMapping("/sessions/{sessionId}/events")
	public void publishPresenceEvent(
		@DestinationVariable UUID sessionId,
		@Valid @Payload EventAppendRequest request
	) {
		if (request.type() != EventType.DISCONNECTED && request.type() != EventType.RECONNECTED) {
			sendError(sessionId, "UNSUPPORTED_EVENT_TYPE", "Only DISCONNECTED or RECONNECTED is accepted on the realtime events channel");
			return;
		}
		appendAndBroadcast(sessionId, request);
	}

	private void appendAndBroadcast(UUID sessionId, EventAppendRequest request) {
		try {
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
			messagingTemplate.convertAndSend("/topic/sessions/" + sessionId, response);
		}
		catch (ApiException exception) {
			sendError(sessionId, exception.getCode(), exception.getMessage());
		}
		catch (RuntimeException exception) {
			sendError(sessionId, "REALTIME_EVENT_FAILED", "Failed to process realtime event");
		}
	}

	private void sendError(UUID sessionId, String code, String message) {
		messagingTemplate.convertAndSend(
			"/topic/sessions/" + sessionId + "/errors",
			new RealtimeErrorResponse(code, message, UtcDateTimes.toOffsetDateTime(UtcDateTimes.now()))
		);
	}
}
