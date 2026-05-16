package com.sokind.chat.timeline.api;

import java.time.OffsetDateTime;

public record SnapshotResponse(
	Long snapshotId,
	String sessionId,
	long serverSequence,
	OffsetDateTime snapshotAt,
	OffsetDateTime createdAt,
	boolean reused
) {
}
