package br.com.rinos.app.backend.module.identity.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo das transições admitidas para a identidade global.
 *
 * <p>O catálogo descreve o contrato completo da identidade. A feature de cadastro executa somente
 * as transições que partem de {@link UserStatusEnum#PENDING_VERIFICATION}.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum UserStatusTransitionEnum {

  VERIFY(UserStatusEnum.PENDING_VERIFICATION, UserStatusEnum.ACTIVE),
  CANCEL(UserStatusEnum.PENDING_VERIFICATION, UserStatusEnum.CANCELLED),
  CANCEL_ACTIVE(UserStatusEnum.ACTIVE, UserStatusEnum.CANCELLED),
  CANCEL_BLOCKED(UserStatusEnum.BLOCKED, UserStatusEnum.CANCELLED),
  CANCEL_DEACTIVATED(UserStatusEnum.DEACTIVATED, UserStatusEnum.CANCELLED),
  BLOCK(UserStatusEnum.ACTIVE, UserStatusEnum.BLOCKED),
  DEACTIVATE_ACTIVE(UserStatusEnum.ACTIVE, UserStatusEnum.DEACTIVATED),
  REACTIVATE_BLOCKED(UserStatusEnum.BLOCKED, UserStatusEnum.ACTIVE),
  DEACTIVATE_BLOCKED(UserStatusEnum.BLOCKED, UserStatusEnum.DEACTIVATED),
  REACTIVATE_DEACTIVATED(UserStatusEnum.DEACTIVATED, UserStatusEnum.ACTIVE);

  private final UserStatusEnum previousStatus;
  private final UserStatusEnum newStatus;

  UserStatusTransitionEnum(UserStatusEnum previousStatus, UserStatusEnum newStatus) {
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
  }

  /**
   * Retorna o estado exigido antes da transição.
   *
   * @return estado anterior
   */
  public UserStatusEnum getPreviousStatus() {
    return previousStatus;
  }

  /**
   * Retorna o estado resultante da transição.
   *
   * @return novo estado
   */
  public UserStatusEnum getNewStatus() {
    return newStatus;
  }

  /**
   * Localiza uma transição pelo par de estados.
   *
   * @param previousStatus estado persistido atual
   * @param newStatus estado pretendido
   * @return transição permitida ou vazio quando o par é inválido
   */
  public static Optional<UserStatusTransitionEnum> find(
      UserStatusEnum previousStatus,
      UserStatusEnum newStatus) {
    return Arrays.stream(values())
        .filter(transition -> transition.previousStatus == previousStatus)
        .filter(transition -> transition.newStatus == newStatus)
        .findFirst();
  }
}
