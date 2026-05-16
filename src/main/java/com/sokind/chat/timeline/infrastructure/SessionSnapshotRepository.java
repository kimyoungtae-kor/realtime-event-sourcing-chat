package com.sokind.chat.timeline.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;

import com.sokind.chat.timeline.domain.SessionSnapshotEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshotEntity, Long> {

	Optional<SessionSnapshotEntity> findBySessionIdAndServerSequence(Long sessionId, long serverSequence);

	Optional<SessionSnapshotEntity> findFirstBySessionIdAndSnapshotAtLessThanEqualOrderByServerSequenceDesc(
		Long sessionId,
		LocalDateTime snapshotAt
	);
}
