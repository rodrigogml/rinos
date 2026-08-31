package br.com.rinos.app.backend.module.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.account.entity.AccountProvisioningCheckpointEntity;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;

/** Acessa as etapas duráveis que antecedem a ativação de uma conta. */
public interface AccountProvisioningCheckpointRepository
    extends JpaRepository<AccountProvisioningCheckpointEntity, Long> {

  /**
   * Reabre uma única etapa sob lock pessimista para combinar o resultado da outbox.
   *
   * @param accountId identificador interno da conta
   * @param stepType etapa exclusiva da saga
   * @return checkpoint bloqueado quando presente
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT checkpoint FROM AccountProvisioningCheckpointEntity checkpoint "
      + "WHERE checkpoint.accountId = :accountId AND checkpoint.stepType = :stepType")
  Optional<AccountProvisioningCheckpointEntity> findByAccountIdAndStepTypeForUpdate(
      @Param("accountId") Long accountId,
      @Param("stepType") ProvisioningStepType stepType);

  /**
   * Obtém todas as etapas de uma conta sob lock para avaliar sua ordem sem observar estado parcial.
   *
   * @param accountId identificador interno da conta
   * @return etapas bloqueadas até o final da transação chamadora
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT checkpoint FROM AccountProvisioningCheckpointEntity checkpoint "
      + "WHERE checkpoint.accountId = :accountId")
  List<AccountProvisioningCheckpointEntity> findByAccountIdForUpdate(
      @Param("accountId") Long accountId);
}
