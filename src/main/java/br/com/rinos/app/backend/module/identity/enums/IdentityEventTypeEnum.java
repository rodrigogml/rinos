package br.com.rinos.app.backend.module.identity.enums;

/**
 * Eventos auditáveis do ciclo inicial da identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum IdentityEventTypeEnum {

  REGISTRATION_STARTED(false),
  REGISTRATION_REJECTED(false),
  EXTERNAL_IDENTITY_RESOLVED(false),
  VERIFICATION_REISSUED(false),
  VERIFICATION_CONFIRMED(false),
  REGISTRATION_ACTIVATED(false),
  REGISTRATION_CANCELLATION_REQUESTED(false),
  REGISTRATION_EXPIRED(false),
  REGISTRATION_CANCELLED(false),
  PASSWORD_RECOVERY_REQUESTED(false),
  PASSWORD_RECOVERY_COMPLETED(false),
  AUTHENTICATION_ATTEMPTED(false),
  AUTHENTICATION_SUCCEEDED(false),
  AUTHENTICATION_CHALLENGE_ISSUED(false),
  AUTHENTICATION_CHALLENGE_CONSUMED(false),
  AUTHENTICATION_METHOD_ADDED(false),
  AUTHENTICATION_METHOD_RENAMED(false),
  AUTHENTICATION_METHOD_REMOVED(false),
  AUTHENTICATION_SESSION_CREATED(false),
  AUTHENTICATION_SESSION_REVOKED(false),
  AUTHENTICATION_SESSION_EXPIRED(false),
  PASSKEY_RISK_DETECTED(false),
  AUTHENTICATION_NEW_SESSION_NOTIFICATION_REQUESTED(false),
  AUTHENTICATION_METHOD_CHANGED_NOTIFICATION_REQUESTED(false),
  AUTHENTICATION_RECOVERY_COMPLETED_NOTIFICATION_REQUESTED(false),
  AUTHENTICATION_REPEATED_FAILURES_NOTIFICATION_REQUESTED(false),
  USER_STATUS_CHANGED(true),
  REGISTRATION_STATUS_CHANGED(true);

  private final boolean statusTransition;

  IdentityEventTypeEnum(boolean statusTransition) {
    this.statusTransition = statusTransition;
  }

  /**
   * Indica se estados anterior e novo são obrigatórios.
   *
   * @return {@code true} para evento de transição
   */
  public boolean isStatusTransition() {
    return statusTransition;
  }
}
