package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;

/**
 * Valida e aplica transições do processo temporário de cadastro.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class RegistrationLifecycleService {

  private static final String ENTITY_TYPE = "REGISTRATION";

  /**
   * Aplica uma transição terminal permitida e descreve o evento resultante.
   *
   * @param registration processo de cadastro gerenciado
   * @param newStatus estado terminal pretendido
   * @param origin origem responsável pela ação
   * @param reason motivo seguro e opcional
   * @param occurredAt instante UTC da mudança
   * @return descrição sem PII da transição aplicada
   * @throws NullPointerException quando um argumento obrigatório é nulo
   * @throws IllegalStateException quando a transição não pertence ao catálogo permitido
   */
  public IdentityTransitionVO transition(
      RegistrationEntity registration,
      RegistrationStatusEnum newStatus,
      IdentityTransitionOriginEnum origin,
      String reason,
      Instant occurredAt) {
    Objects.requireNonNull(registration, "registration must not be null");
    Objects.requireNonNull(newStatus, "newStatus must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    RegistrationStatusEnum previousStatus = registration.getStatus();
    RegistrationStatusTransitionEnum.find(previousStatus, newStatus)
        .orElseThrow(() -> new IllegalStateException(
            "Registration transition is not allowed: "
                + previousStatus
                + " -> "
                + newStatus));

    if (newStatus == RegistrationStatusEnum.ACTIVE) {
      registration.setCompletedAt(occurredAt);
    } else if (newStatus == RegistrationStatusEnum.CANCELLED) {
      registration.setCancelledAt(occurredAt);
    }
    registration.setStatus(newStatus);
    return new IdentityTransitionVO(
        ENTITY_TYPE,
        previousStatus.name(),
        newStatus.name(),
        origin,
        reason,
        occurredAt);
  }
}
