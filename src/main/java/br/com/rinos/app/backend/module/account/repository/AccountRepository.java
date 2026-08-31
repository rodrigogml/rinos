package br.com.rinos.app.backend.module.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.account.entity.AccountEntity;

/** Acessa a autoridade global de contas e a seleção concorrente da saga de criação. */
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

  /** @param publicId identidade pública estável da conta @return conta existente, quando houver */
  Optional<AccountEntity> findByPublicId(UUID publicId);

  /** @param tenantId tenant lógico da conta @return conta existente, quando houver */
  Optional<AccountEntity> findByTenantId(Long tenantId);

  /**
   * Reclama uma conta com a primeira etapa pendente elegível, preservando a ordem da saga.
   *
   * <p>Uma etapa posterior não torna a conta elegível antes de todas as predecessoras estarem
   * completas. Um checkpoint terminal falho retira a conta da fila automática até intervenção
   * externa; o {@code SKIP LOCKED} evita disputa entre instâncias de manutenção.
   *
   * @param now instante UTC usado para respeitar o backoff das etapas
   * @return no máximo uma conta bloqueada até o final da transação chamadora
   */
  @Query(value = """
      SELECT accountRow.*
      FROM account_account accountRow
      WHERE accountRow.status = 'CREATING'
        AND NOT EXISTS (
          SELECT 1 FROM account_provisioningCheckpoint failed
          WHERE failed.idAccount = accountRow.idAccount AND failed.status = 'FAILED'
        )
        AND (
          EXISTS (
            SELECT 1 FROM account_provisioningCheckpoint storage
            WHERE storage.idAccount = accountRow.idAccount AND storage.stepType = 'STORAGE'
              AND storage.status = 'PROCESSING'
              AND (storage.nextAttemptAt IS NULL OR storage.nextAttemptAt <= :now)
          )
          OR (
            EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint storage
              WHERE storage.idAccount = accountRow.idAccount AND storage.stepType = 'STORAGE'
                AND storage.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint membership
              WHERE membership.idAccount = accountRow.idAccount AND membership.stepType = 'FOUNDING_MEMBERSHIP'
                AND membership.status IN ('PENDING', 'PROCESSING')
                AND (membership.nextAttemptAt IS NULL OR membership.nextAttemptAt <= :now)
            )
          )
          OR (
            EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint storage
              WHERE storage.idAccount = accountRow.idAccount AND storage.stepType = 'STORAGE'
                AND storage.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint membership
              WHERE membership.idAccount = accountRow.idAccount AND membership.stepType = 'FOUNDING_MEMBERSHIP'
                AND membership.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint accessBootstrap
              WHERE accessBootstrap.idAccount = accountRow.idAccount AND accessBootstrap.stepType = 'ACCESS_BOOTSTRAP'
                AND accessBootstrap.status IN ('PENDING', 'PROCESSING')
                AND (accessBootstrap.nextAttemptAt IS NULL OR accessBootstrap.nextAttemptAt <= :now)
            )
          )
          OR (
            EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint storage
              WHERE storage.idAccount = accountRow.idAccount AND storage.stepType = 'STORAGE'
                AND storage.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint membership
              WHERE membership.idAccount = accountRow.idAccount AND membership.stepType = 'FOUNDING_MEMBERSHIP'
                AND membership.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint accessBootstrap
              WHERE accessBootstrap.idAccount = accountRow.idAccount AND accessBootstrap.stepType = 'ACCESS_BOOTSTRAP'
                AND accessBootstrap.status = 'COMPLETED'
            )
            AND EXISTS (
              SELECT 1 FROM account_provisioningCheckpoint defaultPlan
              WHERE defaultPlan.idAccount = accountRow.idAccount AND defaultPlan.stepType = 'DEFAULT_PLAN'
                AND defaultPlan.status IN ('PENDING', 'PROCESSING')
                AND (defaultPlan.nextAttemptAt IS NULL OR defaultPlan.nextAttemptAt <= :now)
            )
          )
        )
      ORDER BY accountRow.idAccount
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<AccountEntity> findNextCreatingWithEligibleCheckpointForUpdate(@Param("now") Instant now);
}
