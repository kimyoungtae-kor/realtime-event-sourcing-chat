package com.sokind.chat.event.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.sokind.chat.event.domain.SessionEventEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionEventRepository extends JpaRepository<SessionEventEntity, Long> {

	Optional<SessionEventEntity> findFirstBySessionIdOrderByServerSequenceDesc(Long sessionId);

	@EntityGraph(attributePaths = "session")
	Optional<SessionEventEntity> findBySessionIdAndSenderIdAndClientEventId(
		Long sessionId,
		String senderId,
		String clientEventId
	);

	@Query("""
		select e
		from SessionEventEntity e
		join fetch e.session s
		where e.session.publicId = :publicId
		  and (:afterSequence is null or e.serverSequence > :afterSequence)
		  and (:from is null or e.serverReceivedAt >= :from)
		  and (:to is null or e.serverReceivedAt <= :to)
		order by e.serverSequence asc
		""")
	List<SessionEventEntity> search(
		@Param("publicId") String publicId,
		@Param("afterSequence") Long afterSequence,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);

	@Query("""
		select e
		from SessionEventEntity e
		join fetch e.session s
		where e.session.id = :sessionId
		  and e.serverReceivedAt <= :at
		order by e.serverSequence asc
		""")
	List<SessionEventEntity> findReplayEvents(
		@Param("sessionId") Long sessionId,
		@Param("at") LocalDateTime at
	);

	@Query("""
		select e
		from SessionEventEntity e
		join fetch e.session s
		where e.session.id = :sessionId
		  and e.serverSequence <= :serverSequence
		order by e.serverSequence asc
		""")
	List<SessionEventEntity> findReplayEventsThroughSequence(
		@Param("sessionId") Long sessionId,
		@Param("serverSequence") long serverSequence
	);

	@Query("""
		select e
		from SessionEventEntity e
		join fetch e.session s
		where e.session.id = :sessionId
		  and e.serverSequence > :afterSequence
		  and e.serverReceivedAt <= :at
		order by e.serverSequence asc
		""")
	List<SessionEventEntity> findReplayEventsAfterSequence(
		@Param("sessionId") Long sessionId,
		@Param("afterSequence") long afterSequence,
		@Param("at") LocalDateTime at
	);
}
