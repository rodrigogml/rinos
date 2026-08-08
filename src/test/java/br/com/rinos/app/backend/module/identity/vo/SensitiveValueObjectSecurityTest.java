package br.com.rinos.app.backend.module.identity.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;

/**
 * Protege representações diagnósticas dos objetos internos que transportam PII ou segredos.
 *
 * @author Rodrigo Leitão
 */
class SensitiveValueObjectSecurityTest {

  @Test
  void issuedAuthSession_shouldRedactCookieValue() {
    String cookie = "selector.validator";

    String diagnostic = new IssuedAuthSessionVO(
        cookie,
        UUID.fromString("784c7c76-b1d7-4fa5-8060-98dcf9202fe5"),
        Instant.parse("2026-08-09T00:00:00Z"),
        Instant.parse("2026-08-08T12:30:00Z"))
        .toString();

    assertThat(diagnostic)
        .contains("cookieValue=<redacted>")
        .doesNotContain(cookie);
  }

  @Test
  void authSessionAccess_shouldRedactRotatedCookieValue() {
    String cookie = "new-selector.new-validator";

    String diagnostic = new AuthSessionAccessVO(
        AuthSessionAccessStatusEnum.ROTATED,
        10L,
        UUID.fromString("784c7c76-b1d7-4fa5-8060-98dcf9202fe5"),
        null,
        null,
        null,
        null,
        cookie)
        .toString();

    assertThat(diagnostic)
        .contains("rotatedCookieValue=<redacted>")
        .doesNotContain(cookie);
  }

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
