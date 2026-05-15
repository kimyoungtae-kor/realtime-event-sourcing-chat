package com.sokind.chat.session.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessions")
public class SessionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, length = 36, unique = true)
	private String publicId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SessionStatus status;

	@Column(name = "next_sequence", nullable = false)
	private long nextSequence;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	protected SessionEntity() {
	}

	private SessionEntity(String publicId, SessionStatus status, long nextSequence, LocalDateTime now) {
		this.publicId = publicId;
		this.status = status;
		this.nextSequence = nextSequence;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static SessionEntity create(LocalDateTime now) {
		return new SessionEntity(UUID.randomUUID().toString(), SessionStatus.ACTIVE, 1L, now);
	}

	public long allocateNextSequence(LocalDateTime now) {
		long allocated = this.nextSequence;
		this.nextSequence++;
		this.updatedAt = now;
		return allocated;
	}

	public void complete(LocalDateTime now) {
		this.status = SessionStatus.COMPLETED;
		this.endedAt = now;
		this.updatedAt = now;
	}

	public void interrupt(LocalDateTime now) {
		if (this.status == SessionStatus.ACTIVE) {
			this.status = SessionStatus.INTERRUPTED;
			this.updatedAt = now;
		}
	}

	public void activate(LocalDateTime now) {
		if (this.status == SessionStatus.INTERRUPTED) {
			this.status = SessionStatus.ACTIVE;
			this.updatedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public long getNextSequence() {
		return nextSequence;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}
}
