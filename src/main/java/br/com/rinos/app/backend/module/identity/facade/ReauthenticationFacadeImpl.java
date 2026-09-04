package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.ReauthenticationBeginRequestDTO;
import br.com.rinos.app.api.dto.ReauthenticationVerificationRequestDTO;
import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.vo.ReauthenticationResultVO;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.ReauthenticationService;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationDecisionVO;

/**
 * Converte contratos públicos para o protocolo transacional de reautenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class ReauthenticationFacadeImpl implements ReauthenticationFacade {

  private final ReauthenticationService service;

  /** Cria a facade sobre a autoridade transacional única. */
  public ReauthenticationFacadeImpl(ReauthenticationService service) {
    this.service = Objects.requireNonNull(service, "service must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public ReauthenticationResultVO begin(ReauthenticationBeginRequestDTO request) {
    if (request == null) {
      return terminal(br.com.rinos.app.api.enums.ReauthenticationStatusEnum.REJECTED);
    }
    ReauthenticationOperationEnum operation = ReauthenticationOperationEnum
        .fromOperationId(request.operationId())
        .orElse(null);
    UUID sessionReference = parseUuid(request.sessionReference());
    if (operation == null || sessionReference == null) {
      return terminal(br.com.rinos.app.api.enums.ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    return map(service.begin(request.userId(), sessionReference, operation, request.occurredAt()));
  }

  /** {@inheritDoc} */
  @Override
  public ReauthenticationResultVO verify(ReauthenticationVerificationRequestDTO request) {
    if (request == null) {
      return terminal(br.com.rinos.app.api.enums.ReauthenticationStatusEnum.REJECTED);
    }
    UUID sessionReference = parseUuid(request.sessionReference());
    if (sessionReference == null) {
      return terminal(br.com.rinos.app.api.enums.ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    AuthenticationMethodEnum method = AuthenticationMethodEnum.valueOf(request.method().name());
    return map(service.complete(
        request.userId(),
        sessionReference,
        request.challengeReference(),
        method,
        request.proof(),
        request.occurredAt()));
  }

  /** {@inheritDoc} */
  @Override
  public ReauthenticationResultVO cancel(
      long userId,
      String sessionReference,
      String challengeReference,
      Instant occurredAt) {
    UUID parsedSession = parseUuid(sessionReference);
    if (userId <= 0 || parsedSession == null || challengeReference == null
        || challengeReference.isBlank() || occurredAt == null) {
      return terminal(br.com.rinos.app.api.enums.ReauthenticationStatusEnum.REJECTED);
    }
    return map(service.cancel(userId, parsedSession, challengeReference, occurredAt));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecentlyAuthorized(
      long userId,
      String sessionReference,
      String operationId,
      Instant occurredAt) {
    ReauthenticationOperationEnum operation = ReauthenticationOperationEnum
        .fromOperationId(operationId)
        .orElse(null);
    UUID parsedSession = parseUuid(sessionReference);
    if (userId <= 0 || operation == null || parsedSession == null || occurredAt == null) {
      return false;
    }
    return service.isRecentlyAuthorized(userId, parsedSession, operation, occurredAt);
  }

  private static ReauthenticationResultVO map(ReauthenticationDecisionVO decision) {
    Set<br.com.rinos.app.api.enums.AuthenticationMethodEnum> methods =
        decision.allowedMethods().stream()
            .map(method -> br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(method.name()))
            .collect(Collectors.toUnmodifiableSet());
    return new ReauthenticationResultVO(
        br.com.rinos.app.api.enums.ReauthenticationStatusEnum.valueOf(decision.status().name()),
        decision.challengeReference(),
        decision.operationLabelKey(),
        decision.expiresAt(),
        methods);
  }

  private static ReauthenticationResultVO terminal(
      br.com.rinos.app.api.enums.ReauthenticationStatusEnum status) {
    return new ReauthenticationResultVO(status, null, null, null, Set.of());
  }

  private static UUID parseUuid(String value) {
    try {
      return value == null || value.isBlank() ? null : UUID.fromString(value);
    } catch (IllegalArgumentException invalidReference) {
      return null;
    }
  }
}
