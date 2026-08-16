package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.contributor.AccessKeyContributor;
import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.backend.module.access.component.AccessControlKeyContributor;

class AccessKeyRegistryServiceTest {

  @Test
  void registry_shouldAggregateCanonicalContributorInStableOrder() {
    AccessKeyRegistryService registry =
        new AccessKeyRegistryService(List.of(new AccessControlKeyContributor()));

    assertThat(registry.accessKeys()).hasSize(13)
        .extracting(AccessKeyDescriptor::code)
        .isSorted();
    assertThat(registry.categories()).hasSize(12)
        .extracting(AccessCategoryDescriptor::code)
        .isSorted();
    assertThat(registry.require("global.access.catalog.view"))
        .isSameAs(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW);
    assertThat(registry.find("tenant.unknown.view")).isEmpty();
    assertThatThrownBy(() -> registry.require("tenant.unknown.view"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unknown access key");
  }

  @Test
  void registry_shouldRejectOwnerCollisionAndMissingCategory() {
    AccessKeyContributor core = new AccessControlKeyContributor();
    AccessKeyDescriptor incompatible = new AccessKeyDescriptor(
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.code(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.scope(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.categoryCode(),
        "another-module",
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.nameI18nKey(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.descriptionI18nKey(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.status(),
        null,
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.sourceRequirements(),
        true);

    assertThatThrownBy(() -> new AccessKeyRegistryService(List.of(
        core,
        contributor("another-module", List.of(incompatible), core.categories()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incompatible access key descriptor");

    assertThatThrownBy(() -> new AccessKeyRegistryService(List.of(
        contributor("access-control", List.of(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW), List.of()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("category is missing");
  }

  @Test
  void registry_shouldRejectDescriptorOwnedByAnotherContributor() {
    assertThatThrownBy(() -> new AccessKeyRegistryService(List.of(
        contributor("wrong-module", List.of(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW), List.of()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("owner differs");
  }

  @Test
  void registry_shouldAcceptIdempotentInclusionAndExplicitInactivation() {
    AccessKeyContributor core = new AccessControlKeyContributor();
    AccessKeyRegistryService idempotent = new AccessKeyRegistryService(List.of(
        core,
        contributor(
            "access-control",
            List.of(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW),
            core.categories())));

    assertThat(idempotent.require(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.code()))
        .isEqualTo(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW);

    AccessKeyDescriptor inactive = new AccessKeyDescriptor(
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.code(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.scope(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.categoryCode(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.ownerModule(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.nameI18nKey(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.descriptionI18nKey(),
        AccessKeyStatus.INACTIVE,
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.entitlementRequirement(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.sourceRequirements(),
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW.minimumAdministrative());
    AccessKeyRegistryService explicitlyInactive = new AccessKeyRegistryService(List.of(
        contributor("access-control", List.of(inactive), core.categories())));

    assertThat(explicitlyInactive.require(inactive.code()).status()).isEqualTo(AccessKeyStatus.INACTIVE);
    assertThatThrownBy(() -> new AccessKeyRegistryService(List.of(
        core,
        contributor("access-control", List.of(inactive), core.categories()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incompatible access key descriptor");
  }

  private static AccessKeyContributor contributor(
      String module,
      Collection<AccessKeyDescriptor> keys,
      Collection<AccessCategoryDescriptor> categories) {
    return new AccessKeyContributor() {
      @Override
      public String moduleCode() {
        return module;
      }

      @Override
      public Collection<AccessKeyDescriptor> accessKeys() {
        return keys;
      }

      @Override
      public Collection<AccessCategoryDescriptor> categories() {
        return categories;
      }
    };
  }
}
