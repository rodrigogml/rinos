package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;

/**
 * Valida e aplica transições de estado da identidade global.
 *
 * <p>O serviço produz a descrição completa da mudança, mas a persistência append-only do evento
 * pertence ao serviço de auditoria da tarefa 2.4.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class UserLifecycleService {

  private static final String ENTITY_TYPE = "USER";

  /**
   * Aplica uma transição permitida e descreve o evento resultante.
   *
   * @param user identidade gerenciada
   * @param newStatus estado pretendido
   * @param origin origem responsável pela ação
   * @param reason motivo seguro e opcional
   * @param occurredAt instante UTC da mudança
   * @return descrição sem PII da transição aplicada
   * @throws NullPointerException quando um argumento obrigatório é nulo
   * @throws IllegalStateException quando a transição não pertence ao catálogo permitido
   */
  public IdentityTransitionVO transition(
      UserEntity user,
      UserStatusEnum newStatus,
      IdentityTransitionOriginEnum origin,
      String reason,
      Instant occurredAt) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(newStatus, "newStatus must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    UserStatusEnum previousStatus = user.getStatus();
    UserStatusTransitionEnum.find(previousStatus, newStatus)
        .orElseThrow(() -> new IllegalStateException(
            "User transition is not allowed: " + previousStatus + " -> " + newStatus));

    if (previousStatus == UserStatusEnum.PENDING_VERIFICATION
        && newStatus == UserStatusEnum.ACTIVE) {
      user.setInitialActivatedAt(occurredAt);
    }
    user.setStatus(newStatus);
    return new IdentityTransitionVO(
        ENTITY_TYPE,
        previousStatus.name(),
        newStatus.name(),
        origin,
        reason,
        occurredAt);
  }
}
