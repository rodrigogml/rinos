package br.com.rinos.app.backend.module.identity.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo fechado de operações sensíveis e da garantia aceita por cada uma.
 *
 * @author Rodrigo Leitão
 */
public enum ReauthenticationOperationEnum {

  RENAME_PASSKEY("rename-passkey", "ui.securitySettings.reauthentication.operation.renamePasskey"),
  REGISTER_PASSKEY("register-passkey", "ui.securitySettings.reauthentication.operation.registerPasskey"),
  CREATE_PASSWORD("create-password", "ui.securitySettings.reauthentication.operation.createPassword"),
  CHANGE_PASSWORD("change-password", "ui.securitySettings.reauthentication.operation.changePassword"),
  REVOKE_PASSKEY("revoke-passkey", "ui.securitySettings.reauthentication.operation.revokePasskey"),
  ENROLL_FACTOR("enroll-factor", "ui.securitySettings.reauthentication.operation.enrollFactor"),
  REMOVE_FACTOR("remove-factor", "ui.securitySettings.reauthentication.operation.removeFactor"),
  REGENERATE_RECOVERY_CODES(
      "regenerate-recovery-codes",
      "ui.securitySettings.reauthentication.operation.regenerateRecoveryCodes"),
  LINK_EXTERNAL_IDENTITY(
      "link-external-identity",
      "ui.securitySettings.reauthentication.operation.linkExternalIdentity"),
  UNLINK_EXTERNAL_IDENTITY(
      "unlink-external-identity",
      "ui.securitySettings.reauthentication.operation.unlinkExternalIdentity"),
  REVOKE_SESSION("revoke-session", "ui.securitySettings.reauthentication.operation.revokeSession"),
  REVOKE_ALL_SESSIONS(
      "revoke-all-sessions",
      "ui.securitySettings.reauthentication.operation.revokeAllSessions");

  private static final Set<AuthenticationMethodEnum> INTERACTIVE_METHODS = Set.of(
      AuthenticationMethodEnum.PASSWORD,
      AuthenticationMethodEnum.TOTP,
      AuthenticationMethodEnum.PASSKEY);

  private final String operationId;
  private final String labelKey;

  ReauthenticationOperationEnum(String operationId, String labelKey) {
    this.operationId = operationId;
    this.labelKey = labelKey;
  }

  public String operationId() {
    return operationId;
  }

  public String labelKey() {
    return labelKey;
  }

  public AuthenticationAssuranceEnum requiredAssurance() {
    return AuthenticationAssuranceEnum.SINGLE_FACTOR;
  }

  public Set<AuthenticationMethodEnum> allowedMethods() {
    return INTERACTIVE_METHODS;
  }

  /** Resolve somente identificadores catalogados; valores livres nunca viram operação. */
  public static Optional<ReauthenticationOperationEnum> fromOperationId(String operationId) {
    if (operationId == null || operationId.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(operation -> operation.operationId.equals(operationId))
        .findFirst();
  }
}
