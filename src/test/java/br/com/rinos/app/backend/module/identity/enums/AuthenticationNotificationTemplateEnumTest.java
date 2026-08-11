package br.com.rinos.app.backend.module.identity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticationNotificationTemplateEnumTest {

  @Test
  void templates_shouldExposeStableRfwNames() {
    assertThat(AuthenticationNotificationTemplateEnum.NEW_SESSION.getTemplateName())
        .isEqualTo("authentication-new-session");
    assertThat(AuthenticationNotificationTemplateEnum.METHOD_CHANGED.getTemplateName())
        .isEqualTo("authentication-method-changed");
    assertThat(AuthenticationNotificationTemplateEnum.RECOVERY_COMPLETED.getTemplateName())
        .isEqualTo("authentication-recovery-completed");
    assertThat(AuthenticationNotificationTemplateEnum.REPEATED_FAILURES.getTemplateName())
        .isEqualTo("authentication-repeated-failures");
  }
}
