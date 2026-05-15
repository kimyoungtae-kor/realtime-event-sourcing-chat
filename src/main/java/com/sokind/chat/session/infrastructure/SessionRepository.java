package com.sokind.chat.session.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.sokind.chat.session.domain.SessionEntity;
import com.sokind.chat.session.domain.SessionStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

	Optional<SessionEntity> findByPublicId(String publicId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SessionEntity s where s.publicId = :publicId")
	Optional<SessionEntity> findByPublicIdForUpdate(@Param("publicId") String publicId);

	@Query("""
		select s
		from SessionEntity s
		where (:status is null or s.status = :status)
		  and (:from is null or s.createdAt >= :from)
		  and (:to is null or s.createdAt <= :to)
		  and (
		    :participantId is null
		    or exists (
		      select 1
		      from SessionParticipantEntity p
		      where p.session = s
		        and p.userId = :participantId
		    )
		  )
		order by s.createdAt desc
		""")
	List<SessionEntity> search(
		@Param("status") SessionStatus status,
		@Param("participantId") String participantId,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);
}
