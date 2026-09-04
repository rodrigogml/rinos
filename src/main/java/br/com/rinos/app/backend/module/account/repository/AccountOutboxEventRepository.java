package br.com.rinos.app.backend.module.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.account.entity.AccountOutboxEventEntity;

/** Acessa a fila durável de integrações da criação de conta. */
public interface AccountOutboxEventRepository extends JpaRepository<AccountOutboxEventEntity, Long> {

  /**
   * Obtém e bloqueia o primeiro evento elegível sem aguardar item já reclamado por outra instância.
   *
   * @param now instante UTC usado para avaliar agendamento e lease vencido
   * @return no máximo um evento bloqueado até o fim da transação chamadora
   */
  @Query(value = """
      SELECT *
      FROM account_outboxEvent
      WHERE (status = 'PENDING' AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now))
         OR (status = 'PROCESSING' AND leaseUntil <= :now)
      ORDER BY idAccountOutboxEvent
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<AccountOutboxEventEntity> findNextEligibleForUpdate(@Param("now") Instant now);

  /**
   * Reabre um evento sob lock antes de confirmar resultado da chamada fora da transação.
   *
   * @param eventId identificador técnico imutável do evento
   * @return evento bloqueado quando ainda existir
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT event FROM AccountOutboxEventEntity event WHERE event.eventId = :eventId")
  Optional<AccountOutboxEventEntity> findByEventIdForUpdate(@Param("eventId") UUID eventId);
}
