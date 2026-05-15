package com.sokind.chat.participant.domain;

import java.time.LocalDateTime;

import com.sokind.chat.session.domain.SessionEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	name = "session_participants",
	uniqueConstraints = @UniqueConstraint(name = "uk_participants_session_user", columnNames = {"session_id", "user_id"})
)
public class SessionParticipantEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private SessionEntity session;

	@Column(name = "user_id", nullable = false, length = 100)
	private String userId;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ParticipantState state;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	@Column(name = "left_at")
	private LocalDateTime leftAt;

	@Column(name = "last_seen_at")
	private LocalDateTime lastSeenAt;

	protected SessionParticipantEntity() {
	}

	private SessionParticipantEntity(
		SessionEntity session,
		String userId,
		String displayName,
		ParticipantState state,
		LocalDateTime now
	) {
		this.session = session;
		this.userId = userId;
		this.displayName = displayName;
		this.state = state;
		this.joinedAt = now;
		this.lastSeenAt = now;
	}

	public static SessionParticipantEntity join(
		SessionEntity session,
		String userId,
		String displayName,
		LocalDateTime now
	) {
		return new SessionParticipantEntity(session, userId, displayName, ParticipantState.ONLINE, now);
	}

	public void markJoined(String displayName, LocalDateTime now) {
		this.displayName = displayName;
		this.state = ParticipantState.ONLINE;
		this.leftAt = null;
		this.lastSeenAt = now;
	}

	public void markLeft(LocalDateTime now) {
		this.state = ParticipantState.LEFT;
		this.leftAt = now;
		this.lastSeenAt = now;
	}

	public void markDisconnected(LocalDateTime now) {
		if (this.state != ParticipantState.LEFT) {
			this.state = ParticipantState.OFFLINE;
			this.lastSeenAt = now;
		}
	}

	public void markReconnected(LocalDateTime now) {
		if (this.state != ParticipantState.LEFT) {
			this.state = ParticipantState.ONLINE;
			this.lastSeenAt = now;
		}
	}

	public void touch(LocalDateTime now) {
		this.lastSeenAt = now;
	}

	public Long getId() {
		return id;
	}

	public SessionEntity getSession() {
		return session;
	}

	public String getUserId() {
		return userId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ParticipantState getState() {
		return state;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public LocalDateTime getLeftAt() {
		return leftAt;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}
}
