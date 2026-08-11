package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationNotificationTemplateEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.vo.IdentityEventReferenceVO;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationNotificationRequestedEvent;
import br.com.rinos.app.config.AuthenticationNotificationPropertiesConfig;

/**
 * Registra resultados técnicos do ciclo de identidade sem receber ou persistir PII e segredos.
 *
 * <p>Estados e motivos são códigos fechados em caixa alta. Esse contrato impede que e-mail,
 * endereço de origem, token ou texto livre sejam acidentalmente promovidos a auditoria permanente.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class IdentityAuditService {

  private static final Pattern STATUS_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
  private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  private final IdentityEventRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final AuthenticationNotificationPropertiesConfig notificationProperties;

  /**
   * Cria o serviço sobre o registro append-only.
   *
   * @param repository persistência dos eventos
   */
  public IdentityAuditService(IdentityEventRepository repository) {
    this(repository, null, null);
  }

  /** Cria o serviço integrado ao publisher e à política de cooldown. */
  @Autowired
  public IdentityAuditService(
      IdentityEventRepository repository,
      ApplicationEventPublisher eventPublisher,
      AuthenticationNotificationPropertiesConfig notificationProperties) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.notificationProperties = notificationProperties;
  }

  /**
   * Registra um evento já reduzido aos seus resultados públicos.
   *
   * @param user usuário relacionado ou {@code null}
   * @param registration cadastro relacionado ou {@code null}
   * @param correlationId correlação técnica da operação
   * @param eventType tipo estável
   * @param previousStatus estado anterior, obrigatório apenas em transições
   * @param newStatus novo estado, obrigatório apenas em transições
   * @param originType origem técnica
   * @param reason código de motivo opcional
   * @param occurredAt instante UTC do resultado
   * @return referência segura do evento persistido
   * @throws IllegalArgumentException quando relações ou códigos violam o contrato
   */
  @Transactional
  public IdentityEventReferenceVO record(
      UserEntity user,
      RegistrationEntity registration,
      UUID correlationId,
      IdentityEventTypeEnum eventType,
      String previousStatus,
      String newStatus,
      IdentityTransitionOriginEnum originType,
      String reason,
      Instant occurredAt) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(originType, "originType must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    validateRelationship(user, registration);
    validateStatuses(eventType, previousStatus, newStatus);
    validateOptionalCode(reason, REASON_CODE, "reason");

    IdentityEventEntity event = repository.saveAndFlush(new IdentityEventEntity(
        user,
        registration,
        toBytes(correlationId),
        eventType,
        previousStatus,
        newStatus,
        originType,
        reason,
        occurredAt));
    scheduleNotification(user, correlationId, eventType, reason, occurredAt);
    return new IdentityEventReferenceVO(
        event.getId(),
        correlationId,
        event.getEventType(),
        event.getOccurredAt());
  }

  private void scheduleNotification(
      UserEntity user,
      UUID correlationId,
      IdentityEventTypeEnum eventType,
      String reason,
      Instant occurredAt) {
    if (user == null || eventPublisher == null || notificationProperties == null) {
      return;
    }
    AuthenticationNotificationTemplateEnum template = notificationTemplate(eventType, reason);
    if (template == null) {
      return;
    }
    IdentityEventTypeEnum notificationEvent = notificationEvent(template);
    if (template == AuthenticationNotificationTemplateEnum.REPEATED_FAILURES
        && repository.existsByUserIdAndEventTypeAndOccurredAtAfter(
            user.getId(), notificationEvent,
            occurredAt.minus(notificationProperties.failedLoginCooldown()))) {
      return;
    }
    repository.saveAndFlush(new IdentityEventEntity(
        user,
        null,
        toBytes(correlationId),
        notificationEvent,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        template.name(),
        occurredAt));
    eventPublisher.publishEvent(new AuthenticationNotificationRequestedEvent(
        user, template, correlationId, occurredAt));
  }

  private static AuthenticationNotificationTemplateEnum notificationTemplate(
      IdentityEventTypeEnum eventType,
      String reason) {
    return switch (eventType) {
      case AUTHENTICATION_METHOD_ADDED, AUTHENTICATION_METHOD_RENAMED,
          AUTHENTICATION_METHOD_REMOVED -> AuthenticationNotificationTemplateEnum.METHOD_CHANGED;
      case PASSWORD_RECOVERY_COMPLETED -> AuthenticationNotificationTemplateEnum.RECOVERY_COMPLETED;
      case AUTHENTICATION_REPEATED_FAILURES -> AuthenticationNotificationTemplateEnum.REPEATED_FAILURES;
      case AUTHENTICATION_SESSION_CREATED -> reason != null && reason.startsWith("NEW_DEVICE")
          ? AuthenticationNotificationTemplateEnum.NEW_SESSION : null;
      default -> null;
    };
  }

  private static IdentityEventTypeEnum notificationEvent(
      AuthenticationNotificationTemplateEnum template) {
    return switch (template) {
      case NEW_SESSION -> IdentityEventTypeEnum.SECURITY_NOTIFICATION_NEW_SESSION;
      case METHOD_CHANGED -> IdentityEventTypeEnum.SECURITY_NOTIFICATION_METHOD_CHANGED;
      case RECOVERY_COMPLETED -> IdentityEventTypeEnum.SECURITY_NOTIFICATION_RECOVERY_COMPLETED;
      case REPEATED_FAILURES -> IdentityEventTypeEnum.SECURITY_NOTIFICATION_REPEATED_FAILURES;
    };
  }

  /**
   * Registra uma transição produzida pelos serviços de lifecycle.
   *
   * @param user usuário relacionado ou {@code null}
   * @param registration cadastro relacionado ou {@code null}
   * @param correlationId correlação técnica
   * @param eventType evento compatível com transição
   * @param transition resultado sem dados sensíveis
   * @return referência segura persistida
   */
  @Transactional
  public IdentityEventReferenceVO recordTransition(
      UserEntity user,
      RegistrationEntity registration,
      UUID correlationId,
      IdentityEventTypeEnum eventType,
      IdentityTransitionVO transition) {
    Objects.requireNonNull(transition, "transition must not be null");
    return record(
        user,
        registration,
        correlationId,
        eventType,
        transition.previousStatus(),
        transition.newStatus(),
        transition.origin(),
        transition.reason(),
        transition.occurredAt());
  }

  /**
   * Persiste a evidência mínima de um cancelamento após remover seus identificadores diretos.
   *
   * @param correlationId correlação técnica aleatória
   * @param originType origem técnica
   * @param reason código de motivo opcional
   * @param occurredAt instante UTC do cancelamento
   * @return referência segura sem usuário ou cadastro
   */
  @Transactional
  public IdentityEventReferenceVO recordCancellationTombstone(
      UUID correlationId,
      IdentityTransitionOriginEnum originType,
      String reason,
      Instant occurredAt) {
    return record(
        null,
        null,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_CANCELLED,
        null,
        null,
        originType,
        reason,
        occurredAt);
  }

  /**
   * Persiste a evidência mínima de uma expiração depois da remoção dos identificadores diretos.
   *
   * @param correlationId correlação técnica aleatória
   * @param occurredAt instante UTC da expiração
   * @return referência segura sem usuário ou cadastro
   */
  @Transactional
  public IdentityEventReferenceVO recordExpiryTombstone(
      UUID correlationId,
      Instant occurredAt) {
    return record(
        null,
        null,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_EXPIRED,
        null,
        null,
        IdentityTransitionOriginEnum.SCHEDULED_JOB,
        "RETENTION_ELAPSED",
        occurredAt);
  }

  /**
   * Elimina eventos temporários relacionados antes de substituí-los pelo tombstone terminal.
   *
   * <p>Esta é a única exceção de retenção à escrita append-only: somente cadastro e identidade
   * ainda pendentes, já autorizados para deleção terminal, podem usar esta operação.
   *
   * @param user identidade pendente que será removida
   * @param registration cadastro pendente que será removido
   * @return quantidade de eventos temporários eliminados
   */
  @Transactional
  public int minimizeForTerminalRemoval(
      UserEntity user,
      RegistrationEntity registration) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(registration, "registration must not be null");
    validateRelationship(user, registration);
    Long userId = Objects.requireNonNull(user.getId(), "user id must not be null");
    Long registrationId = Objects.requireNonNull(
        registration.getId(),
        "registration id must not be null");
    return repository.deleteRelatedEventsForTerminalRemoval(userId, registrationId);
  }

  private static void validateRelationship(
      UserEntity user,
      RegistrationEntity registration) {
    if (registration != null && user != null && registration.getUser() != user) {
      Long userId = user.getId();
      Long registrationUserId = registration.getUser().getId();
      if (userId == null || !userId.equals(registrationUserId)) {
        throw new IllegalArgumentException("registration must belong to user");
      }
    }
  }

  private static void validateStatuses(
      IdentityEventTypeEnum eventType,
      String previousStatus,
      String newStatus) {
    if (eventType.isStatusTransition()) {
      validateRequiredCode(previousStatus, STATUS_CODE, "previousStatus");
      validateRequiredCode(newStatus, STATUS_CODE, "newStatus");
      return;
    }
    if (previousStatus != null || newStatus != null) {
      throw new IllegalArgumentException("non-transition event must not contain statuses");
    }
  }

  private static void validateRequiredCode(
      String value,
      Pattern pattern,
      String fieldName) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new IllegalArgumentException(fieldName + " must be a safe code");
    }
  }

  private static void validateOptionalCode(
      String value,
      Pattern pattern,
      String fieldName) {
    if (value != null && !pattern.matcher(value).matches()) {
      throw new IllegalArgumentException(fieldName + " must be a safe code");
    }
  }

  private static byte[] toBytes(UUID correlationId) {
    return ByteBuffer.allocate(16)
        .putLong(correlationId.getMostSignificantBits())
        .putLong(correlationId.getLeastSignificantBits())
        .array();
  }
}
