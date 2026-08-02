package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.HumanVerificationOperationEnum;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.service.TrustedProxyService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

@DisplayName("Política pública de comprovação humana")
class HumanVerificationPolicyFacadeImplTest {

  private final TrustedProxyService trustedProxyService = mock(TrustedProxyService.class);
  private final OriginAddressService originAddressService = mock(OriginAddressService.class);
  private final OriginLimitService originLimitService = mock(OriginLimitService.class);
  private final HumanVerificationPolicyFacadeImpl facade = new HumanVerificationPolicyFacadeImpl(
      trustedProxyService, originAddressService, originLimitService);

  @Test
  void resolveTrustedOrigin_shouldReturnCanonicalLiteral_whenProxyChainIsAccepted() {
    RemoteOriginRequestVO request = new RemoteOriginRequestVO(
        "10.0.0.5", "for=203.0.113.10", "203.0.113.10");
    OriginAddressVO origin = new OriginAddressVO(new byte[] {(byte) 203, 0, 113, 10});
    when(trustedProxyService.resolve(
        "10.0.0.5", "for=203.0.113.10", "203.0.113.10")).thenReturn(origin);
    when(originAddressService.toCanonicalLiteral(origin)).thenReturn("203.0.113.10");

    String result = facade.resolveTrustedOrigin(request);

    assertThat(result).isEqualTo("203.0.113.10");
  }

  @Test
  void isHumanVerificationRequired_shouldDelegateRegistrationToPersistentThreshold() {
    OriginAddressVO origin = new OriginAddressVO(new byte[] {(byte) 203, 0, 113, 10});
    when(originAddressService.normalize("203.0.113.10")).thenReturn(origin);
    when(originLimitService.requiresTurnstile(
        origin, OriginOperationEnum.USER_REGISTRATION)).thenReturn(false);

    boolean required = facade.isHumanVerificationRequired(
        HumanVerificationOperationEnum.REGISTRATION, "203.0.113.10");

    assertThat(required).isFalse();
    verify(originLimitService).requiresTurnstile(
        origin, OriginOperationEnum.USER_REGISTRATION);
  }

  @Test
  void isHumanVerificationRequired_shouldFailSafe_whenOperationHasNoThresholdPolicy() {
    boolean required = facade.isHumanVerificationRequired(
        HumanVerificationOperationEnum.SIGN_IN, "203.0.113.10");

    assertThat(required).isTrue();
    verifyNoInteractions(originAddressService, originLimitService);
  }

  @Test
  void isHumanVerificationRequired_shouldAlwaysProtectRegistrationCancellation() {
    boolean required = facade.isHumanVerificationRequired(
        HumanVerificationOperationEnum.REGISTRATION_CANCELLATION,
        "203.0.113.10");

    assertThat(required).isTrue();
    verifyNoInteractions(originAddressService, originLimitService);
  }

  @Test
  void isHumanVerificationRequired_shouldFailSafe_whenOriginIsUnavailable() {
    boolean required = facade.isHumanVerificationRequired(
        HumanVerificationOperationEnum.REGISTRATION, null);

    assertThat(required).isTrue();
    verifyNoInteractions(originAddressService, originLimitService);
  }
}
