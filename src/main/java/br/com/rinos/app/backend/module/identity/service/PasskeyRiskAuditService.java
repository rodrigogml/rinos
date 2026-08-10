package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyRiskReasonEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Persiste anomalias de passkey fora da transacao rejeitada pela validacao WebAuthn.
 *
 * <p>O evento registra somente usuario, correlacao aleatoria e motivo catalogado. A operacao nao
 * altera estado de credential, outros metodos ou sessoes.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class PasskeyRiskAuditService {

  private final UserRepository users;
  private final IdentityAuditService audit;

  /**
   * Cria o registrador isolado sobre a identidade global e a auditoria append-only.
   *
   * @param users autoridade das identidades globais
   * @param audit persistencia sanitizada dos eventos
   */
  public PasskeyRiskAuditService(UserRepository users, IdentityAuditService audit) {
    this.users = Objects.requireNonNull(users, "users must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
  }

  /**
   * Registra a anomalia em uma transacao independente para que a rejeicao da assercao nao apague
   * sua evidencia.
   *
   * @param userId identidade proprietaria da credential observada
   * @param reason motivo fechado e sem material WebAuthn
   * @param correlationId correlacao tecnica aleatoria
   * @param occurredAt instante UTC da deteccao
   * @throws EntityNotFoundException quando a identidade deixou de existir
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long userId,
      PasskeyRiskReasonEnum reason,
      UUID correlationId,
      Instant occurredAt) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    UserEntity user = users.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("Passkey risk owner not found"));
    audit.record(
        user,
        null,
        Objects.requireNonNull(correlationId, "correlationId must not be null"),
        IdentityEventTypeEnum.PASSKEY_RISK_DETECTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        Objects.requireNonNull(reason, "reason must not be null").name(),
        Objects.requireNonNull(occurredAt, "occurredAt must not be null"));
  }
}
