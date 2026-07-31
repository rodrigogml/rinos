package br.com.rinos.app.backend.module.identity.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo das transições admitidas para um processo de cadastro.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationStatusTransitionEnum {

  ACTIVATE(RegistrationStatusEnum.PENDING_VERIFICATION, RegistrationStatusEnum.ACTIVE),
  CANCEL(RegistrationStatusEnum.PENDING_VERIFICATION, RegistrationStatusEnum.CANCELLED),
  EXPIRE(RegistrationStatusEnum.PENDING_VERIFICATION, RegistrationStatusEnum.EXPIRED);

  private final RegistrationStatusEnum previousStatus;
  private final RegistrationStatusEnum newStatus;

  RegistrationStatusTransitionEnum(
      RegistrationStatusEnum previousStatus,
      RegistrationStatusEnum newStatus) {
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
  }

  /**
   * Retorna o estado exigido antes da transição.
   *
   * @return estado anterior
   */
  public RegistrationStatusEnum getPreviousStatus() {
    return previousStatus;
  }

  /**
   * Retorna o estado resultante da transição.
   *
   * @return novo estado
   */
  public RegistrationStatusEnum getNewStatus() {
    return newStatus;
  }

  /**
   * Localiza uma transição pelo par de estados.
   *
   * @param previousStatus estado persistido atual
   * @param newStatus estado pretendido
   * @return transição permitida ou vazio quando o par é inválido
   */
  public static Optional<RegistrationStatusTransitionEnum> find(
      RegistrationStatusEnum previousStatus,
      RegistrationStatusEnum newStatus) {
    return Arrays.stream(values())
        .filter(transition -> transition.previousStatus == previousStatus)
        .filter(transition -> transition.newStatus == newStatus)
        .findFirst();
  }
}
