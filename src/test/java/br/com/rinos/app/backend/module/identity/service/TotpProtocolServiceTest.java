package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.vo.ProtectedTotpEnrollmentVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.SecondFactorConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWTotpService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWTotpEnrollmentVO;

@DisplayName("Protocolo TOTP protegido do Rinos")
class TotpProtocolServiceTest {

  private static final long USER_ID = 17L;
  private static final UUID REFERENCE = UUID.fromString("6e89f399-c679-495c-8c42-b17f2db18996");
  private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  @Test
  void acceptedStep_shouldUseRfwWindowAndReturnExactMatchedStep() {
    TotpProtocolService service = service();
    ProtectedTotpEnrollmentVO enrollment = service.create(USER_ID, REFERENCE, "user@example.test");

    var accepted = service.acceptedStep(
        USER_ID,
        REFERENCE,
        enrollment.encryptedSecret(),
        "287082",
        Instant.ofEpochSecond(60));

    assertThat(accepted).hasValue(1L);
  }

  @Test
  void create_shouldReturnPresentationOnceAndBindCiphertextToIdentityReference() {
    TotpProtocolService service = service();

    ProtectedTotpEnrollmentVO enrollment = service.create(USER_ID, REFERENCE, "user@example.test");

    assertThat(enrollment.presentation().secret()).isEqualTo(RFC_SECRET);
    assertThat(enrollment.presentation().provisioningUri()).startsWith("otpauth://totp/");
    assertThat(enrollment.toString()).doesNotContain(RFC_SECRET, "otpauth://");
    assertThatThrownBy(() -> service.acceptedStep(
        USER_ID,
        UUID.randomUUID(),
        enrollment.encryptedSecret(),
        "287082",
        Instant.ofEpochSecond(60)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("encrypted authentication secret is invalid");
  }

  private static TotpProtocolService service() {
    SecondFactorConfig factorConfig = new SecondFactorConfig(6, 30, 1, 6, 10);
    RFWAuthenticationPropertiesConfig properties = mock(RFWAuthenticationPropertiesConfig.class);
    when(properties.secondFactor()).thenReturn(factorConfig);
    RFWTotpService enrollmentService = mock(RFWTotpService.class);
    when(enrollmentService.createEnrollment("Rinos", "user@example.test"))
        .thenReturn(new RFWTotpEnrollmentVO(
            RFC_SECRET,
            "otpauth://totp/Rinos:user@example.test?secret=" + RFC_SECRET));
    return new TotpProtocolService(enrollmentService, properties, keyring());
  }

  private static AuthenticationKeyringService keyring() {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 7);
    return new AuthenticationKeyringService(new AuthenticationKeyringPropertiesConfig(
        true, "v1", Map.of("v1", Base64.getEncoder().encodeToString(key))));
  }
}
