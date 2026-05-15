package com.sokind.chat.event.domain;

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
	name = "session_events",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_events_session_sequence", columnNames = {"session_id", "server_sequence"}),
		@UniqueConstraint(name = "uk_events_idempotency", columnNames = {"session_id", "sender_id", "client_event_id"})
	}
)
public class SessionEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private SessionEntity session;

	@Column(name = "server_sequence", nullable = false)
	private long serverSequence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private EventType type;

	@Column(name = "sender_id", nullable = false, length = 100)
	private String senderId;

	@Column(name = "client_event_id", nullable = false, length = 100)
	private String clientEventId;

	@Column(name = "client_sent_at")
	private LocalDateTime clientSentAt;

	@Column(name = "server_received_at", nullable = false)
	private LocalDateTime serverReceivedAt;

	@Column(name = "payload_json", nullable = false, columnDefinition = "json")
	private String payloadJson;

	protected SessionEventEntity() {
	}

	public SessionEventEntity(
		SessionEntity session,
		long serverSequence,
		EventType type,
		String senderId,
		String clientEventId,
		LocalDateTime clientSentAt,
		LocalDateTime serverReceivedAt,
		String payloadJson
	) {
		this.session = session;
		this.serverSequence = serverSequence;
		this.type = type;
		this.senderId = senderId;
		this.clientEventId = clientEventId;
		this.clientSentAt = clientSentAt;
		this.serverReceivedAt = serverReceivedAt;
		this.payloadJson = payloadJson;
	}

	public Long getId() {
		return id;
	}

	public SessionEntity getSession() {
		return session;
	}

	public long getServerSequence() {
		return serverSequence;
	}

	public EventType getType() {
		return type;
	}

	public String getSenderId() {
		return senderId;
	}

	public String getClientEventId() {
		return clientEventId;
	}

	public LocalDateTime getClientSentAt() {
		return clientSentAt;
	}

	public LocalDateTime getServerReceivedAt() {
		return serverReceivedAt;
	}

	public String getPayloadJson() {
		return payloadJson;
	}
}
