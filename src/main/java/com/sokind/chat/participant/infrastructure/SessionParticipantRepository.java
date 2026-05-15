package com.sokind.chat.participant.infrastructure;

import java.util.List;
import java.util.Optional;

import com.sokind.chat.participant.domain.SessionParticipantEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipantEntity, Long> {

	Optional<SessionParticipantEntity> findBySessionIdAndUserId(Long sessionId, String userId);

	List<SessionParticipantEntity> findBySessionPublicIdOrderByJoinedAtAsc(String publicId);

	@Query("""
		select p
		from SessionParticipantEntity p
		where p.session.id = :sessionId
		order by p.joinedAt asc
		""")
	List<SessionParticipantEntity> findAllBySessionId(@Param("sessionId") Long sessionId);
}
