package com.sokind.chat.timeline.api;

import java.net.URI;
import java.util.UUID;

import com.sokind.chat.timeline.application.TimelineReplayService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions/{sessionId}/snapshots")
public class SnapshotController {

	private final TimelineReplayService timelineReplayService;

	public SnapshotController(TimelineReplayService timelineReplayService) {
		this.timelineReplayService = timelineReplayService;
	}

	@PostMapping
	public ResponseEntity<SnapshotResponse> createSnapshot(@PathVariable UUID sessionId) {
		SnapshotResponse response = timelineReplayService.createSnapshot(sessionId.toString());
		if (response.reused()) {
			return ResponseEntity.ok(response);
		}
		return ResponseEntity.created(URI.create("/sessions/" + sessionId + "/snapshots/" + response.serverSequence()))
			.body(response);
	}
}
