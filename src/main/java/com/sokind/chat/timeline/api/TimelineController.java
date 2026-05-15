package com.sokind.chat.timeline.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.sokind.chat.timeline.application.TimelineReplayService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/sessions/{sessionId}/timeline")
public class TimelineController {

	private final TimelineReplayService timelineReplayService;

	public TimelineController(TimelineReplayService timelineReplayService) {
		this.timelineReplayService = timelineReplayService;
	}

	@GetMapping
	public TimelineResponse restore(
		@PathVariable UUID sessionId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at,
		@RequestParam(defaultValue = "100") @Min(1) @Max(500) int messageLimit
	) {
		return timelineReplayService.restore(sessionId.toString(), at, messageLimit);
	}
}
