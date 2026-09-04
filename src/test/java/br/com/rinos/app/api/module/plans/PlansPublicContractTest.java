package br.com.rinos.app.api.module.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.plans.dto.AssociationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationAcceptanceCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityReleaseRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.dto.TenantContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest;
import br.com.rinos.app.api.module.plans.facade.EntitlementEvaluationFacade;
import br.com.rinos.app.api.module.plans.facade.TenantUserCapacityFacade;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.port.TenantContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.api.module.plans.vo.EntitlementDecision;
import br.com.rinos.app.api.module.plans.vo.EntitlementEvaluationResult;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.PersonalEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;

class PlansPublicContractTest {

  private static final List<Class<?>> RECORDS = List.of(
      AssociationCapacityRequest.class,
      EntitlementEvaluationRequest.class,
      InvitationAcceptanceCapacityRequest.class,
      InvitationCapacityReleaseRequest.class,
      InvitationCapacityRequest.class,
      PersonalContractBootstrapRequest.class,
      TenantContractBootstrapRequest.class,
      TenantUserCapacityRequest.class,
      ContractBootstrapResult.class,
      EntitlementDecision.class,
      EntitlementEvaluationResult.class,
      EntitlementRequirement.class,
      PersonalEntitlementSubject.class,
      TenantEntitlementSubject.class,
      TenantUserCapacityResult.class);

  @Test
  void contracts_shouldBeImmutableAndIndependentFromBackendPersistence() {
    for (Class<?> contract : RECORDS) {
      assertThat(contract.isRecord()).as(contract.getSimpleName()).isTrue();
      assertThat(contract.getDeclaredFields())
          .extracting(Field::getGenericType)
          .allSatisfy(type -> assertThat(type.getTypeName())
              .doesNotContain("br.com.rinos.app.backend.", ".entity.", ".repository."));
      assertThat(contract.getRecordComponents())
          .extracting(RecordComponent::getGenericType)
          .allSatisfy(type -> assertThat(type.getTypeName())
              .doesNotContain("br.com.rinos.app.backend.", ".entity.", ".repository."));
    }
  }

  @Test
  void publishedPorts_shouldRemainInterfaces() {
    assertThat(List.of(
        EntitlementEvaluationFacade.class,
        TenantUserCapacityFacade.class,
        PersonalContractBootstrapPort.class,
        TenantContractBootstrapPort.class))
        .allMatch(Class::isInterface);
  }
}
