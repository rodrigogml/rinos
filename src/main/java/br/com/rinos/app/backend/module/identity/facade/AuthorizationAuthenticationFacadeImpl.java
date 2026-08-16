package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.facade.AuthorizationAuthenticationFacade;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;

/** Revalida a sessão persistente sem projetar tenant, chaves ou regras no principal. */
@Service
@Lazy
public class AuthorizationAuthenticationFacadeImpl
    implements AuthorizationAuthenticationFacade {

  private final AuthSessionRepository sessions;
  private final AuthSessionMethodRepository methods;
  private final IdentityReferenceService references;

  public AuthorizationAuthenticationFacadeImpl(
      AuthSessionRepository sessions,
      AuthSessionMethodRepository methods,
      IdentityReferenceService references) {
    this.sessions = sessions;
    this.methods = methods;
    this.references = references;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuthenticationAssurance> resolve(
      long expectedIdentityId, String sessionReference, Instant occurredAt) {
    if (expectedIdentityId <= 0 || sessionReference == null || sessionReference.isBlank()) {
      return Optional.empty();
    }
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference;
    try {
      reference = UUID.fromString(sessionReference);
    } catch (IllegalArgumentException invalidReference) {
      return Optional.empty();
    }
    AuthSessionEntity session = sessions.findByPublicReference(references.encode(reference))
        .orElse(null);
    if (!isCurrent(session, expectedIdentityId, occurredAt)) {
      return Optional.empty();
    }
    Set<AuthenticationMethodEnum> verifiedMethods = methods
        .findBySessionIdOrderByFactorOrder(session.getId()).stream()
        .map(method -> AuthenticationMethodEnum.valueOf(method.getMethod().name()))
        .collect(Collectors.toUnmodifiableSet());
    if (verifiedMethods.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new AuthenticationAssurance(
        AuthenticationAssuranceEnum.valueOf(session.getAssuranceLevel().name()),
        verifiedMethods, session.getAuthenticatedAt(), session.getLastStrongAuthAt()));
  }

  private static boolean isCurrent(
      AuthSessionEntity session, long expectedIdentityId, Instant occurredAt) {
    return session != null
        && session.getId() != null
        && session.getUser() != null
        && session.getUser().getId() != null
        && session.getUser().getId() == expectedIdentityId
        && session.getUser().getStatus() == UserStatusEnum.ACTIVE
        && session.getStatus() == AuthSessionStatusEnum.ACTIVE
        && occurredAt.isBefore(session.getAbsoluteExpiresAt())
        && occurredAt.isBefore(session.getIdleExpiresAt());
  }
}
