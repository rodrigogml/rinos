package br.com.rinos.app.backend.module.identity.vo;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Protege as projeções de gestão contra material criptográfico. */
class AuthenticationFactorProjectionTest {
  @Test
  void summaries_shouldExposeOnlyRecognizableManagementFields() {
    List<Class<?>> summaries = List.of(TotpFactorSummaryVO.class, EmailFactorSummaryVO.class,
        RecoveryCodeSetSummaryVO.class, PasskeyCredentialSummaryVO.class);
    assertThat(summaries).allSatisfy(type -> assertThat(type.getRecordComponents())
        .extracting(RecordComponent::getName)
        .noneMatch(name -> name.matches(".*(?i)(secret|hash|credentialId|publicKey|attestation|handle|nonce|digest).*")));
  }

  @Test
  void registrationTransport_shouldCopyAndRedactPublicCryptographicMaterial() {
    byte[] credentialId = new byte[] {1, 2};
    PasskeyCredentialRegistrationVO value = new PasskeyCredentialRegistrationVO("public-key",
        credentialId, new byte[] {3}, 0, true, false, false, "internal",
        new byte[] {4}, new byte[] {5}, "Notebook");
    credentialId[0] = 9;
    assertThat(value.credentialId()).containsExactly(1, 2);
    assertThat(value.toString()).contains("material=REDACTED").doesNotContain("[1, 2]");
  }
}
