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
