package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.enums.HumanVerificationOperationEnum;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAbuseProtectionService;
import br.com.rinos.app.backend.module.identity.service.TrustedProxyService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

/**
 * Implementa a política de comprovação humana sem expor o modelo persistente à camada de apresentação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class HumanVerificationPolicyFacadeImpl implements HumanVerificationPolicyFacade {

  private final TrustedProxyService trustedProxyService;
  private final OriginAddressService originAddressService;
  private final OriginLimitService originLimitService;
  private final AuthenticationAbuseProtectionService abuseProtectionService;
  private final Clock clock;

  /**
   * Cria a fachada sobre os serviços atômicos de origem e limitação.
   *
   * @param trustedProxyService validação da cadeia encaminhada
   * @param originAddressService normalização e representação canônica
   * @param originLimitService decisão persistida do limiar de cadastro
   */
  @Autowired
  public HumanVerificationPolicyFacadeImpl(
      TrustedProxyService trustedProxyService,
      OriginAddressService originAddressService,
      @Lazy OriginLimitService originLimitService,
      @Lazy AuthenticationAbuseProtectionService abuseProtectionService) {
    this(trustedProxyService, originAddressService, originLimitService,
        abuseProtectionService, Clock.systemUTC());
  }

  HumanVerificationPolicyFacadeImpl(
      TrustedProxyService trustedProxyService,
      OriginAddressService originAddressService,
      OriginLimitService originLimitService,
      AuthenticationAbuseProtectionService abuseProtectionService,
      Clock clock) {
    this.trustedProxyService = trustedProxyService;
    this.originAddressService = originAddressService;
    this.originLimitService = originLimitService;
    this.abuseProtectionService = abuseProtectionService;
    this.clock = clock;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolveTrustedOrigin(RemoteOriginRequestVO request) {
    Objects.requireNonNull(request, "request não pode ser nulo.");
    OriginAddressVO origin = trustedProxyService.resolve(
        request.immediatePeer(),
        request.forwarded(),
        request.xForwardedFor());
    return originAddressService.toCanonicalLiteral(origin);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isHumanVerificationRequired(
      HumanVerificationOperationEnum operation,
      String canonicalOrigin) {
    return isHumanVerificationRequired(operation, canonicalOrigin, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isHumanVerificationRequired(
      HumanVerificationOperationEnum operation,
      String canonicalOrigin,
      String identifier) {
    Objects.requireNonNull(operation, "operation não pode ser nula.");
    if (canonicalOrigin == null
        || canonicalOrigin.isBlank()) {
      return true;
    }
    if (operation == HumanVerificationOperationEnum.SIGN_IN) {
      try {
        return abuseProtectionService.isTurnstileRequired(
            identifier, canonicalOrigin, clock.instant());
      } catch (RuntimeException unavailablePolicy) {
        return true;
      }
    }
    if (operation != HumanVerificationOperationEnum.REGISTRATION) {
      return true;
    }
    OriginAddressVO origin = originAddressService.normalize(canonicalOrigin);
    return originLimitService.requiresTurnstile(origin, OriginOperationEnum.USER_REGISTRATION);
  }
}
