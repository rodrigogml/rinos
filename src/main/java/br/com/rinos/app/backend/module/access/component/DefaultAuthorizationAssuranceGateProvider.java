package br.com.rinos.app.backend.module.access.component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.backend.module.access.service.AuthorizationAssuranceGateProvider;

/** Aplica a garantia apresentada sem persistir segredo ou congelá-la em ACL/cache. */
@Component
@org.springframework.context.annotation.Lazy
public class DefaultAuthorizationAssuranceGateProvider
    implements AuthorizationAssuranceGateProvider {

  private static final Duration STRONG_AUTHENTICATION_WINDOW = Duration.ofMinutes(15);

  @Override
  public List<AuthorizationGateResult> evaluate(AuthorizationRequest request) {
    if (!request.sensitive()) {
      return List.of(new AuthorizationGateResult("AUTHENTICATION_ASSURANCE", true, null));
    }
    if (request.actor().type() == AuthorizationActorType.SYSTEM) {
      return List.of(new AuthorizationGateResult(
          "AUTHENTICATION_ASSURANCE", true, null));
    }
    Instant strongAt = request.assurance().lastStrongAuthenticationAt();
    boolean recent = strongAt != null
        && !strongAt.isAfter(Instant.now())
        && strongAt.plus(STRONG_AUTHENTICATION_WINDOW).isAfter(Instant.now());
    boolean strongMethod = request.assurance().methods().stream().anyMatch(
        method -> method == AuthenticationMethodEnum.TOTP
            || method == AuthenticationMethodEnum.PASSKEY);
    boolean allowed = recent && strongMethod;
    return List.of(new AuthorizationGateResult(
        "AUTHENTICATION_ASSURANCE", allowed,
        allowed ? null : "ACL_ASSURANCE_REQUIRED"));
  }
}
