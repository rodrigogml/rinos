package br.com.rinos.app.backend.module.access.config;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.rinos.app.api.module.access.contributor.AccessKeyContributor;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;

/** Registra um contributor independente para cada owner da baseline documental v1. */
@Configuration(proxyBeanMethods = false)
public class InitialModuleAccessKeyConfiguration {

  @Bean
  AccessKeyContributor accountMembershipAccessKeyContributor() {
    return contributor("account-membership");
  }

  @Bean
  AccessKeyContributor accountRegistrationAccessKeyContributor() {
    return contributor("account-registration");
  }

  @Bean
  AccessKeyContributor accountsPayableAccessKeyContributor() {
    return contributor("accounts-payable");
  }

  @Bean
  AccessKeyContributor accountsReceivableAccessKeyContributor() {
    return contributor("accounts-receivable");
  }

  @Bean
  AccessKeyContributor bankStatementsReconciliationAccessKeyContributor() {
    return contributor("bank-statements-reconciliation");
  }

  @Bean
  AccessKeyContributor creditCardsAccessKeyContributor() {
    return contributor("credit-cards");
  }

  @Bean
  AccessKeyContributor financialAccountsAccessKeyContributor() {
    return contributor("financial-accounts");
  }

  @Bean
  AccessKeyContributor financialCategoriesAccessKeyContributor() {
    return contributor("financial-categories");
  }

  @Bean
  AccessKeyContributor financialClosingControlAccessKeyContributor() {
    return contributor("financial-closing-control");
  }

  @Bean
  AccessKeyContributor financialDimensionsAccessKeyContributor() {
    return contributor("financial-dimensions");
  }

  @Bean
  AccessKeyContributor financialRecurrencesAccessKeyContributor() {
    return contributor("financial-recurrences");
  }

  @Bean
  AccessKeyContributor financialTransactionsAccessKeyContributor() {
    return contributor("financial-transactions");
  }

  @Bean
  AccessKeyContributor financialTransfersAccessKeyContributor() {
    return contributor("financial-transfers");
  }

  @Bean
  AccessKeyContributor partyPaymentDetailsAccessKeyContributor() {
    return contributor("party-payment-details");
  }

  @Bean
  AccessKeyContributor partyRegistrationAccessKeyContributor() {
    return contributor("party-registration");
  }

  @Bean
  AccessKeyContributor partyRelationshipsRolesAccessKeyContributor() {
    return contributor("party-relationships-roles");
  }

  @Bean
  AccessKeyContributor plansEntitlementsAccessKeyContributor() {
    return contributor("plans-entitlements");
  }

  @Bean
  AccessKeyContributor platformConfigurationAccessKeyContributor() {
    return contributor("platform-configuration");
  }

  @Bean
  AccessKeyContributor platformOperationsAccessKeyContributor() {
    return contributor("platform-operations");
  }

  @Bean
  AccessKeyContributor systemDirectoryAdministrationAccessKeyContributor() {
    return contributor("system-directory-administration");
  }

  @Bean
  AccessKeyContributor tenantDataGovernanceAccessKeyContributor() {
    return contributor("tenant-data-governance");
  }

  @Bean
  AccessKeyContributor tenantStorageProvisioningAccessKeyContributor() {
    return contributor("tenant-storage-provisioning");
  }

  @Bean
  AccessKeyContributor tenantSupportAccessAccessKeyContributor() {
    return contributor("tenant-support-access");
  }

  private static AccessKeyContributor contributor(String moduleCode) {
    Set<AccessKeyDescriptor> keys = InitialModuleAccessKeys.ALL.stream()
        .filter(key -> key.ownerModule().equals(moduleCode))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (keys.isEmpty()) {
      throw new IllegalStateException("module contributor must publish at least one key");
    }
    return new StaticAccessKeyContributor(moduleCode, keys);
  }

  private record StaticAccessKeyContributor(
      String moduleCode,
      Collection<AccessKeyDescriptor> accessKeys) implements AccessKeyContributor {

    private StaticAccessKeyContributor {
      if (moduleCode == null || moduleCode.isBlank()) {
        throw new IllegalArgumentException("moduleCode must not be blank");
      }
      accessKeys = Set.copyOf(accessKeys);
    }
  }
}
