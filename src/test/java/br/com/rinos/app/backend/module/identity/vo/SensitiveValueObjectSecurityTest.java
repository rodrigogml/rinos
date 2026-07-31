package br.com.rinos.app.backend.module.identity.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Protege representações diagnósticas dos objetos internos que transportam PII ou segredos.
 *
 * @author Rodrigo Leitão
 */
class SensitiveValueObjectSecurityTest {

  @Test
  void googleIdentityDomainResult_shouldRedactContinuationTokenAndVerifiedEmail() {
    String token = "continuation-secret";
    String email = "person@example.test";

    String diagnostic = GoogleIdentityDomainResultVO.continuation(
        token,
        email,
        Instant.parse("2026-07-30T18:00:00Z"))
        .toString();

    assertThat(diagnostic)
        .contains("continuationToken=REDACTED", "verifiedEmail=REDACTED")
        .doesNotContain(token, email);
  }

  @Test
  void normalizedEmail_shouldRedactOriginalAndNormalizedValues() {
    String email = "Person@Example.test";
    String normalizedEmail = "person@example.test";

    String diagnostic = new NormalizedEmailVO(email, normalizedEmail).toString();

    assertThat(diagnostic)
        .contains("email=REDACTED", "normalizedEmail=REDACTED")
        .doesNotContain(email, normalizedEmail);
  }
}
