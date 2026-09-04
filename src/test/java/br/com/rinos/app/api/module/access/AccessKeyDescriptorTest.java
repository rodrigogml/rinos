package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyRequirement;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;

class AccessKeyDescriptorTest {

  private static final AccessKeyRequirement REQUIREMENT =
      new AccessKeyRequirement("access-control", "FR-ACL-KEY-001");

  @Test
  void descriptor_shouldNormalizeAndDefensivelyCopyRequirements() {
    Set<AccessKeyRequirement> requirements = new HashSet<>(Set.of(REQUIREMENT));

    AccessKeyDescriptor descriptor = AccessKeyDescriptor.active(
        "tenant.access.catalog.view",
        AccessScope.TENANT,
        "tenant.foundation.access",
        "access-control",
        requirements,
        true);
    requirements.clear();

    assertThat(descriptor.sourceRequirements()).containsExactly(REQUIREMENT);
    assertThat(descriptor.status()).isEqualTo(AccessKeyStatus.ACTIVE);
    assertThat(descriptor.nameI18nKey())
        .isEqualTo("access.key.tenant.access.catalog.view.name");
    assertThatThrownBy(() -> descriptor.sourceRequirements().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void descriptor_shouldRejectScopeI18nAndRequirementMismatches() {
    assertThatThrownBy(() -> AccessKeyDescriptor.active(
        "global.access.catalog.view",
        AccessScope.TENANT,
        "tenant.foundation.access",
        "access-control",
        Set.of(REQUIREMENT),
        true)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AccessKeyDescriptor(
        "tenant.access.catalog.view",
        AccessScope.TENANT,
        "tenant.foundation.access",
        "access-control",
        "wrong.name",
        "wrong.description",
        AccessKeyStatus.ACTIVE,
        null,
        Set.of(REQUIREMENT),
        true)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> AccessKeyDescriptor.active(
        "tenant.access.catalog.view",
        AccessScope.TENANT,
        "tenant.foundation.access",
        "access-control",
        Set.of(),
        true)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AccessKeyRequirement("access-control", "FR-ACL-KEY-*"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exact requirement ID");

    assertThatThrownBy(() -> AccessKeyDescriptor.active(
        "global.personal.files.view",
        AccessScope.GLOBAL,
        "global.platform.access",
        "access-control",
        new EntitlementRequirement(ContractScope.TENANT, "storage.files.personal"),
        Set.of(REQUIREMENT),
        false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entitlement scope");
  }
}
