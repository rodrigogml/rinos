package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;

/** Identidade técnica estável e completa de uma chave registrada por módulo. */
public record AccessKeyDescriptor(
    String code,
    AccessScope scope,
    String categoryCode,
    String ownerModule,
    String nameI18nKey,
    String descriptionI18nKey,
    AccessKeyStatus status,
    EntitlementRequirement entitlementRequirement,
    Set<AccessKeyRequirement> sourceRequirements,
    boolean minimumAdministrative) {

  private static final Pattern CODE_PATTERN =
      Pattern.compile("(?:global|tenant)(?:\\.[a-z][a-z0-9-]*){2,}");
  private static final Pattern MODULE_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");

  public AccessKeyDescriptor {
    code = requireText(code, "code");
    scope = Objects.requireNonNull(scope, "scope must not be null");
    categoryCode = requireText(categoryCode, "categoryCode");
    ownerModule = requireText(ownerModule, "ownerModule");
    nameI18nKey = requireText(nameI18nKey, "nameI18nKey");
    descriptionI18nKey = requireText(descriptionI18nKey, "descriptionI18nKey");
    status = Objects.requireNonNull(status, "status must not be null");
    sourceRequirements = sourceRequirements == null ? Set.of() : Set.copyOf(sourceRequirements);

    if (!CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("code has an invalid format");
    }
    if (!MODULE_PATTERN.matcher(ownerModule).matches()) {
      throw new IllegalArgumentException("ownerModule has an invalid format");
    }
    String expectedPrefix = scope == AccessScope.GLOBAL ? "global." : "tenant.";
    if (!code.startsWith(expectedPrefix) || !categoryCode.startsWith(expectedPrefix)) {
      throw new IllegalArgumentException("key scope is incompatible with code or category");
    }
    if (!nameI18nKey.equals("access.key." + code + ".name")
        || !descriptionI18nKey.equals("access.key." + code + ".description")) {
      throw new IllegalArgumentException("i18n keys must be derived from the stable code");
    }
    if (sourceRequirements.isEmpty()) {
      throw new IllegalArgumentException("sourceRequirements must not be empty");
    }
    if (entitlementRequirement != null) {
      ContractScope expectedEntitlementScope = scope == AccessScope.GLOBAL
          ? ContractScope.PERSONAL : ContractScope.TENANT;
      if (entitlementRequirement.subjectScope() != expectedEntitlementScope) {
        throw new IllegalArgumentException("entitlement scope is incompatible with access key scope");
      }
    }
  }

  /** Cria um descriptor ativo com chaves i18n canônicas. */
  public static AccessKeyDescriptor active(
      String code,
      AccessScope scope,
      String categoryCode,
      String ownerModule,
      Set<AccessKeyRequirement> sourceRequirements,
      boolean minimumAdministrative) {
    return new AccessKeyDescriptor(
        code,
        scope,
        categoryCode,
        ownerModule,
        "access.key." + code + ".name",
        "access.key." + code + ".description",
        AccessKeyStatus.ACTIVE,
        null,
        sourceRequirements,
        minimumAdministrative);
  }

  /** Cria um descriptor ativo condicionado a direito tipado do mesmo escopo funcional. */
  public static AccessKeyDescriptor active(
      String code,
      AccessScope scope,
      String categoryCode,
      String ownerModule,
      EntitlementRequirement entitlementRequirement,
      Set<AccessKeyRequirement> sourceRequirements,
      boolean minimumAdministrative) {
    return new AccessKeyDescriptor(
        code,
        scope,
        categoryCode,
        ownerModule,
        "access.key." + code + ".name",
        "access.key." + code + ".description",
        AccessKeyStatus.ACTIVE,
        Objects.requireNonNull(entitlementRequirement, "entitlementRequirement must not be null"),
        sourceRequirements,
        minimumAdministrative);
  }

  /**
   * Retorna apenas o código para adapters persistentes anteriores ao schema de escopo.
   *
   * @deprecated use {@link #entitlementRequirement()} para não perder a titularidade.
   */
  @Deprecated(forRemoval = true)
  public String entitlementCode() {
    return entitlementRequirement == null ? null : entitlementRequirement.code();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
