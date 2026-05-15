package com.sokind.chat.timeline.infrastructure;

import com.sokind.chat.timeline.domain.SessionSnapshotEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshotEntity, Long> {
}
