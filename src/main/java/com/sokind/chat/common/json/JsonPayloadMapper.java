package com.sokind.chat.common.json;

import java.util.Collections;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class JsonPayloadMapper {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public JsonPayloadMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String toJson(Map<String, Object> payload) {
		try {
			return objectMapper.writeValueAsString(payload == null ? Collections.emptyMap() : payload);
		}
		catch (JacksonException exception) {
			throw new IllegalArgumentException("Payload cannot be serialized to JSON", exception);
		}
	}

	public Map<String, Object> toMap(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(payloadJson, MAP_TYPE);
		}
		catch (JacksonException exception) {
			throw new IllegalArgumentException("Stored payload is not valid JSON", exception);
		}
	}
}
