package br.com.rinos.app.backend.module.membership.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.membership.entity.MembershipOutboxEventEntity;
import jakarta.persistence.LockModeType;

public interface MembershipOutboxEventRepository
    extends JpaRepository<MembershipOutboxEventEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select event from MembershipOutboxEventEntity event
      where event.eventType = 'INVITATION_ISSUED'
        and event.secretCiphertext is not null
        and event.secretExpiresAt > :now
        and ((event.status = 'PENDING' and (event.nextAttemptAt is null or event.nextAttemptAt <= :now))
          or (event.status = 'PROCESSING' and event.leaseUntil <= :now))
      order by event.id
      """)
  List<MembershipOutboxEventEntity> findDispatchableForUpdate(
      @Param("now") Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select event from MembershipOutboxEventEntity event where event.eventId = :eventId")
  Optional<MembershipOutboxEventEntity> findByEventIdForUpdate(@Param("eventId") UUID eventId);

  @Modifying
  @Query("""
      update MembershipOutboxEventEntity event
      set event.status = 'CANCELLED', event.secretCiphertext = null, event.secretNonce = null,
          event.secretKeyId = null, event.secretExpiresAt = null, event.nextAttemptAt = null,
          event.leaseOwner = null, event.leaseUntil = null
      where event.aggregateType = 'INVITATION' and event.aggregateId = :invitationId
        and event.eventType = 'INVITATION_ISSUED' and event.status in ('PENDING', 'PROCESSING')
      """)
  int cancelInvitationDelivery(@Param("invitationId") long invitationId);
}
