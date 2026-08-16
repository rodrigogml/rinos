package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.contributor.AccessKeyContributor;
import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.dto.UserInitiatedAuthorizationJob;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyRequirement;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;

class AccessPublicContractTest {

  private static final List<Class<?>> CONTRACTS = List.of(
      AccessCategoryDescriptor.class,
      AccessExplanation.class,
      AccessKeyDescriptor.class,
      AccessKeyRequirement.class,
      AuthenticationAssurance.class,
      AuthorizationActor.class,
      AuthorizationContext.class,
      AuthorizationDecision.class,
      AuthorizationGateResult.class,
      AuthorizationKeyResult.class,
      AuthorizationOperation.class,
      AuthorizationWorkspaceContext.class,
      AccessExplanationRequest.class,
      AuthorizationRequest.class,
      UserInitiatedAuthorizationJob.class,
      AuthorizationRuleSource.class);

  @Test
  void contracts_shouldBeImmutableAndIndependentFromBackendPersistence() {
    for (Class<?> contract : CONTRACTS) {
      assertThat(contract.isRecord()).isTrue();
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
  void facadeAndContributor_shouldExposeOnlyPublicAccessContracts() {
    assertThat(AuthorizationFacade.class.isInterface()).isTrue();
    assertThat(AccessKeyContributor.class.isInterface()).isTrue();

    for (Method method : AuthorizationFacade.class.getDeclaredMethods()) {
      if (method.getName().equals("explain")) {
        assertThat(method.getParameterTypes()).containsExactly(AccessExplanationRequest.class);
      } else {
        assertThat(method.getParameterTypes()).containsExactly(AuthorizationRequest.class);
      }
      assertThat(method.getReturnType().getPackageName())
          .startsWith("br.com.rinos.app.api.module.access");
    }
    for (Method method : AccessKeyContributor.class.getDeclaredMethods()) {
      assertThat(method.getGenericReturnType().getTypeName())
          .doesNotContain("br.com.rinos.app.backend.", ".entity.", ".repository.");
    }
  }
}
