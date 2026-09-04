package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.vo.RegistrationAuthenticationContinuationVO;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;

@DisplayName("Continuação autenticável após ativação de cadastro")
class RegistrationAuthenticationContinuationServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-01T18:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("b8eb8ee5-e1d1-4ec5-80c8-67281d7b91f7");

  @Test
  void issue_shouldUseLocalPasswordAndShortRegistrationFlow_whenRegistrationIsLocal() {
    AuthenticationFlowService flows = mock(AuthenticationFlowService.class);
    when(flows.issue(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
        .thenReturn(new IssuedAuthenticationFlowVO("opaque-local-flow", NOW.plusSeconds(900), CORRELATION_ID));
    RegistrationAuthenticationContinuationService service = new RegistrationAuthenticationContinuationService(
        flows, sessionProperties());

    RegistrationAuthenticationContinuationVO result = service.issue(
        user(), RegistrationMethodEnum.LOCAL, CORRELATION_ID, NOW);

    assertThat(result.principal().userId()).isEqualTo(41L);
    assertThat(result.completion().purpose())
        .isEqualTo(AuthenticationFlowPurposeEnum.REGISTRATION_ACTIVATION);
    ArgumentCaptor<AuthenticationMethodEnum> method = ArgumentCaptor.forClass(
        AuthenticationMethodEnum.class);
    ArgumentCaptor<br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum> purpose =
        ArgumentCaptor.forClass(br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum.class);
    ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
    verify(flows).issue(
        org.mockito.ArgumentMatchers.eq(41L),
        purpose.capture(),
        method.capture(),
        org.mockito.ArgumentMatchers.eq(AuthenticationAssuranceEnum.SINGLE_FACTOR),
        org.mockito.ArgumentMatchers.eq(java.util.Set.of(AuthenticationMethodEnum.PASSWORD)),
        org.mockito.ArgumentMatchers.eq(false),
        org.mockito.ArgumentMatchers.eq(NOW),
        expiresAt.capture(),
        org.mockito.ArgumentMatchers.eq(CORRELATION_ID));
    assertThat(purpose.getValue())
        .isEqualTo(br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum
            .REGISTRATION_ACTIVATION);
    assertThat(method.getValue()).isEqualTo(AuthenticationMethodEnum.PASSWORD);
    assertThat(expiresAt.getValue()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void issue_shouldUseVerifiedGoogleMethod_whenRegistrationIsGoogle() {
    AuthenticationFlowService flows = mock(AuthenticationFlowService.class);
    when(flows.issue(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
        .thenReturn(new IssuedAuthenticationFlowVO("opaque-google-flow", NOW.plusSeconds(900), CORRELATION_ID));
    RegistrationAuthenticationContinuationService service = new RegistrationAuthenticationContinuationService(
        flows, sessionProperties());

    service.issue(user(), RegistrationMethodEnum.GOOGLE, CORRELATION_ID, NOW);

    verify(flows).issue(
        org.mockito.ArgumentMatchers.eq(41L),
        org.mockito.ArgumentMatchers.eq(
            br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum
                .REGISTRATION_ACTIVATION),
        org.mockito.ArgumentMatchers.eq(AuthenticationMethodEnum.GOOGLE),
        org.mockito.ArgumentMatchers.eq(AuthenticationAssuranceEnum.SINGLE_FACTOR),
        org.mockito.ArgumentMatchers.eq(java.util.Set.of(AuthenticationMethodEnum.GOOGLE)),
        org.mockito.ArgumentMatchers.eq(false),
        org.mockito.ArgumentMatchers.eq(NOW),
        org.mockito.ArgumentMatchers.eq(NOW.plus(Duration.ofMinutes(15))),
        org.mockito.ArgumentMatchers.eq(CORRELATION_ID));
  }

  private static UserEntity user() {
    UserEntity user = new UserEntity("person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    return user;
  }

  private static AuthenticationSessionPropertiesConfig sessionProperties() {
    return new AuthenticationSessionPropertiesConfig(
        Duration.ofHours(12), Duration.ofMinutes(30), Duration.ofDays(30), Duration.ofDays(7),
        Duration.ofMinutes(5), Duration.ofMinutes(15), "RINOS_AUTH", true, "Strict");
  }
}
