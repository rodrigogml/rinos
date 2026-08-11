package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationNotificationTemplateEnum;

/**
 * Evento transitório publicado depois de registrar uma solicitação de notificação de segurança.
 *
 * @param user identidade destinatária
 * @param template template sem segredo
 * @param correlationId correlação técnica
 * @param occurredAt instante da operação original
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
public record AuthenticationNotificationRequestedEvent(
    UserEntity user,
    AuthenticationNotificationTemplateEnum template,
    UUID correlationId,
    Instant occurredAt) {

  public AuthenticationNotificationRequestedEvent {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
