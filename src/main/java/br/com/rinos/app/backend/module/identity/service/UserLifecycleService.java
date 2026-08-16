package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;
import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;

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
  private final AuthSessionService sessionService;
  private final PersonalContractBootstrapPort personalContracts;

  /**
   * Mantém compatibilidade com validadores isolados que não executam transições operacionais.
   */
  public UserLifecycleService() {
    this.sessionService = null;
    this.personalContracts = null;
  }

  /** Cria o lifecycle operacional com invalidação obrigatória das sessões globais. */
  public UserLifecycleService(@Lazy AuthSessionService sessionService) {
    this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
    this.personalContracts = null;
  }

  /** Construtor explícito para composição modular e testes de integração do contrato pessoal. */
  public UserLifecycleService(
      AuthSessionService sessionService,
      PersonalContractBootstrapPort personalContracts) {
    this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
    this.personalContracts = Objects.requireNonNull(
        personalContracts, "personalContracts must not be null");
  }

  /** Cria o lifecycle operacional com contrato pessoal obrigatório na primeira ativação. */
  @Autowired
  public UserLifecycleService(
      @Lazy AuthSessionService sessionService,
      ObjectProvider<PersonalContractBootstrapPort> personalContractProvider) {
    this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
    this.personalContracts = personalContractProvider.getIfAvailable();
  }

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
    return transition(user, newStatus, origin, reason, occurredAt, UUID.randomUUID());
  }

  /**
   * Aplica a transição com a correlação original e revoga todas as sessões quando a
   * identidade deixa o estado ativo.
   *
   * @param correlationId correlação auditável compartilhada com a operação chamadora
   */
  public IdentityTransitionVO transition(
      UserEntity user,
      UserStatusEnum newStatus,
      IdentityTransitionOriginEnum origin,
      String reason,
      Instant occurredAt,
      UUID correlationId) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(newStatus, "newStatus must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");

    UserStatusEnum previousStatus = user.getStatus();
    UserStatusTransitionEnum.find(previousStatus, newStatus)
        .orElseThrow(() -> new IllegalStateException(
            "User transition is not allowed: " + previousStatus + " -> " + newStatus));

    if (previousStatus == UserStatusEnum.PENDING_VERIFICATION
        && newStatus == UserStatusEnum.ACTIVE) {
      ensurePersonalContract(user, correlationId);
      user.setInitialActivatedAt(occurredAt);
    }
    user.setStatus(newStatus);
    if (newStatus != UserStatusEnum.ACTIVE && sessionService != null && user.getId() != null) {
      sessionService.revokeAll(
          user.getId(),
          null,
          AuthSessionRevocationReasonEnum.SECURITY_EVENT,
          occurredAt,
          correlationId);
    }
    return new IdentityTransitionVO(
        ENTITY_TYPE,
        previousStatus.name(),
        newStatus.name(),
        origin,
        reason,
        occurredAt);
  }

  private void ensurePersonalContract(UserEntity user, UUID correlationId) {
    if (personalContracts == null) {
      return;
    }
    if (user.getId() == null) {
      throw new IllegalStateException("personal plan contract context unavailable");
    }
    var result = personalContracts.ensure(new PersonalContractBootstrapRequest(
        correlationId, user.getId(), correlationId.toString()));
    if (result.status() != ContractBootstrapStatus.COMPLETED
        && result.status() != ContractBootstrapStatus.ALREADY_COMPLETED) {
      throw new IllegalStateException("personal plan contract could not be ensured");
    }
  }
}
