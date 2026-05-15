package com.sokind.chat.event.api;

import com.sokind.chat.common.json.JsonPayloadMapper;
import com.sokind.chat.common.time.UtcDateTimes;
import com.sokind.chat.event.domain.SessionEventEntity;

import org.springframework.stereotype.Component;

@Component
public class EventResponseMapper {

	private final JsonPayloadMapper jsonPayloadMapper;

	public EventResponseMapper(JsonPayloadMapper jsonPayloadMapper) {
		this.jsonPayloadMapper = jsonPayloadMapper;
	}

	public EventResponse toResponse(SessionEventEntity event, boolean duplicate) {
		return new EventResponse(
			event.getId(),
			event.getSession().getPublicId(),
			event.getServerSequence(),
			event.getType(),
			event.getSenderId(),
			event.getClientEventId(),
			UtcDateTimes.toOffsetDateTime(event.getClientSentAt()),
			UtcDateTimes.toOffsetDateTime(event.getServerReceivedAt()),
			jsonPayloadMapper.toMap(event.getPayloadJson()),
			duplicate
		);
	}
}
