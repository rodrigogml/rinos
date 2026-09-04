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
      "ui.securitySettings.reauthentication.operation.revokeAllSessions"),
  CREATE_ACCOUNT(
      "create-account", "account.reauthentication.operation.create",
      AuthenticationAssuranceEnum.SINGLE_FACTOR,
      Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP,
          AuthenticationMethodEnum.PASSKEY)),
  MANAGE_ACCESS(
      "manage-access", "access.reauthentication.operation.manage",
      AuthenticationAssuranceEnum.MULTI_FACTOR,
      Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY)),
  EXPLAIN_ACCESS(
      "explain-access", "access.reauthentication.operation.explain",
      AuthenticationAssuranceEnum.MULTI_FACTOR,
      Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY));

  private final String operationId;
  private final String labelKey;
  private final AuthenticationAssuranceEnum requiredAssurance;
  private final Set<AuthenticationMethodEnum> allowedMethods;

  ReauthenticationOperationEnum(String operationId, String labelKey) {
    this(operationId, labelKey, AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP,
            AuthenticationMethodEnum.PASSKEY));
  }

  ReauthenticationOperationEnum(
      String operationId, String labelKey, AuthenticationAssuranceEnum requiredAssurance,
      Set<AuthenticationMethodEnum> allowedMethods) {
    this.operationId = operationId;
    this.labelKey = labelKey;
    this.requiredAssurance = requiredAssurance;
    this.allowedMethods = Set.copyOf(allowedMethods);
  }

  public String operationId() {
    return operationId;
  }

  public String labelKey() {
    return labelKey;
  }

  public AuthenticationAssuranceEnum requiredAssurance() {
    return requiredAssurance;
  }

  public Set<AuthenticationMethodEnum> allowedMethods() {
    return allowedMethods;
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
