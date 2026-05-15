package com.sokind.chat.timeline.domain;

import java.time.LocalDateTime;

import com.sokind.chat.session.domain.SessionEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "session_snapshots",
	uniqueConstraints = @UniqueConstraint(name = "uk_snapshots_session_sequence", columnNames = {"session_id", "server_sequence"})
)
public class SessionSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private SessionEntity session;

	@Column(name = "server_sequence", nullable = false)
	private long serverSequence;

	@Column(name = "snapshot_at", nullable = false)
	private LocalDateTime snapshotAt;

	@Column(name = "state_json", nullable = false, columnDefinition = "json")
	private String stateJson;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected SessionSnapshotEntity() {
	}
}
