package br.com.rinos.app.backend.module.identity.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationNotificationTemplateEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationNotificationRequestedEvent;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailMessage;

class AuthenticationNotificationDispatchServiceTest {

  @Test
  void dispatch_shouldRenderAndSendTemplateWithoutArguments() throws Exception {
    EmailDispatchService email = Mockito.mock(EmailDispatchService.class);
    EmailMessage message = Mockito.mock(EmailMessage.class);
    when(email.createMessage(
        "authentication-method-changed",
        java.util.Locale.ROOT,
        null,
        java.util.List.of("person@example.test"),
        java.util.List.of(),
        java.util.List.of(),
        null))
        .thenReturn(message);
    UserEntity user = new UserEntity(
        "person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    AuthenticationNotificationDispatchService service =
        new AuthenticationNotificationDispatchService(email);

    service.dispatch(new AuthenticationNotificationRequestedEvent(
        user,
        AuthenticationNotificationTemplateEnum.METHOD_CHANGED,
        UUID.randomUUID(),
        Instant.parse("2026-08-11T12:00:00Z")));

    verify(email).createMessage(
        "authentication-method-changed",
        java.util.Locale.ROOT,
        null,
        java.util.List.of("person@example.test"),
        java.util.List.of(),
        java.util.List.of(),
        null);
    verify(email).dispatch(message);
  }

  @Test
  void dispatch_shouldContainFailureWithoutRetryingOrThrowing() throws Exception {
    EmailDispatchService email = Mockito.mock(EmailDispatchService.class);
    when(email.createMessage(
        "authentication-new-session",
        java.util.Locale.ROOT,
        null,
        java.util.List.of("person@example.test"),
        java.util.List.of(),
        java.util.List.of(),
        null))
        .thenThrow(new IllegalStateException("template unavailable"));
    UserEntity user = new UserEntity(
        "person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    AuthenticationNotificationDispatchService service =
        new AuthenticationNotificationDispatchService(email);

    service.dispatch(new AuthenticationNotificationRequestedEvent(
        user,
        AuthenticationNotificationTemplateEnum.NEW_SESSION,
        UUID.randomUUID(),
        Instant.parse("2026-08-11T12:00:00Z")));

    verify(email, never()).dispatch(any());
  }
}
