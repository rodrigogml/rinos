package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationAbuseDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationWindowDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;

@DisplayName("Coordenação das janelas de abuso no login")
class AuthenticationAbuseProtectionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private EmailNormalizationService normalization;
  private OriginAddressService origins;
  private AuthenticationKeyringMacService mac;
  private AuthenticationWindowService windows;
  private AuthenticationAbuseProtectionService service;

  @BeforeEach
  void setUp() {
    normalization = mock(EmailNormalizationService.class);
    origins = mock(OriginAddressService.class);
    mac = mock(AuthenticationKeyringMacService.class);
    windows = mock(AuthenticationWindowService.class);
    service = new AuthenticationAbuseProtectionService(normalization, origins, mac, windows);
    when(normalization.normalize("Person@Example.test"))
        .thenReturn(new NormalizedEmailVO("Person@Example.test", "person@example.test"));
    when(origins.normalize("198.51.100.12"))
        .thenReturn(new OriginAddressVO(new byte[] {(byte) 198, 51, 100, 12}));
    when(mac.protect(eq("sign-in/identifier"), any()))
        .thenReturn(new ProtectedAuthenticationKeyVO(bytes(1), "v1"));
    when(mac.protect(eq("sign-in/origin"), any()))
        .thenReturn(new ProtectedAuthenticationKeyVO(bytes(2), "v1"));
  }

  @Test
  void registerFailure_shouldLockIdentifierBeforeOriginAndCombineStrictestPolicy() {
    when(windows.registerFailure(
        any(), eq("v1"), eq(AuthenticationWindowOperationEnum.SIGN_IN), eq(NOW)))
        .thenReturn(new AuthenticationWindowDecisionVO(
            2, false, Duration.ofSeconds(2), NOW.plusSeconds(900), null))
        .thenReturn(new AuthenticationWindowDecisionVO(
            3, true, Duration.ofSeconds(4), NOW.plusSeconds(900), NOW.plusSeconds(900)));

    AuthenticationAbuseDecisionVO result = service.registerFailure(
        "Person@Example.test", "198.51.100.12", NOW);

    assertThat(result.maximumFailureCount()).isEqualTo(3);
    assertThat(result.turnstileRequired()).isTrue();
    assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(4));
    InOrder order = inOrder(windows);
    order.verify(windows).registerFailure(
        bytes(1), "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW);
    order.verify(windows).registerFailure(
        bytes(2), "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW);
  }

  @Test
  void isOriginTurnstileRequired_shouldInspectWithoutIncrementing() {
    when(windows.inspect(
        any(), eq("v1"), eq(AuthenticationWindowOperationEnum.SIGN_IN), eq(NOW)))
        .thenReturn(new AuthenticationWindowDecisionVO(
            3, true, Duration.ofSeconds(4), NOW.plusSeconds(900), NOW.plusSeconds(900)));

    assertThat(service.isOriginTurnstileRequired("198.51.100.12", NOW)).isTrue();
  }

  @Test
  void isTurnstileRequired_shouldInspectIdentifierBeforeOriginAndCombinePolicy() {
    when(windows.inspect(
        any(), eq("v1"), eq(AuthenticationWindowOperationEnum.SIGN_IN), eq(NOW)))
        .thenReturn(new AuthenticationWindowDecisionVO(
            3, true, Duration.ofSeconds(4), NOW.plusSeconds(900), NOW.plusSeconds(900)))
        .thenReturn(new AuthenticationWindowDecisionVO(
            1, false, Duration.ofSeconds(1), NOW.plusSeconds(900), null));

    assertThat(service.isTurnstileRequired(
        "Person@Example.test", "198.51.100.12", NOW)).isTrue();

    InOrder order = inOrder(windows);
    order.verify(windows).inspect(
        bytes(1), "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW);
    order.verify(windows).inspect(
        bytes(2), "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW);
  }

  @Test
  void isTurnstileRequired_shouldInspectOnlyOriginBeforeIdentifierIsEntered() {
    when(windows.inspect(
        bytes(2), "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW))
        .thenReturn(new AuthenticationWindowDecisionVO(
            0, false, Duration.ZERO, NOW.plusSeconds(900), null));

    assertThat(service.isTurnstileRequired(null, "198.51.100.12", NOW)).isFalse();
  }

  private static byte[] bytes(int first) {
    byte[] value = new byte[32];
    value[0] = (byte) first;
    return value;
  }
}
