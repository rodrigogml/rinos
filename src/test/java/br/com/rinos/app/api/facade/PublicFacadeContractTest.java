package br.com.rinos.app.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

/**
 * Protege a fronteira pública do cadastro contra dependências internas e operações de consulta
 * enumeráveis.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-30
 */
class PublicFacadeContractTest {

  private static final Map<Class<?>, Set<String>> LOCAL_REGISTRATION_FACADES = Map.of(
      RegistrationStartFacade.class, Set.of("start"),
      RegistrationResendFacade.class, Set.of("resend"),
      RegistrationActivationFacade.class, Set.of("activate", "completeConsent"),
      RegistrationCancellationFacade.class,
      Set.of("requestCancellation", "confirmCancellation"));

  @Test
  void facadeMethods_shouldExposeOnlyApiContracts_whenInspected() {
    for (Class<?> facadeType : LOCAL_REGISTRATION_FACADES.keySet()) {
      assertThat(facadeType.isInterface()).isTrue();
      for (Method method : facadeType.getDeclaredMethods()) {
        assertThat(method.getReturnType()).isEqualTo(CompletionStage.class);
        assertThat(method.getGenericReturnType().getTypeName())
            .contains("br.com.rinos.app.api.vo.")
            .doesNotContain(
                "br.com.rinos.app.backend.",
                "br.com.rinos.app.ui.",
                ".entity.",
                ".repository.");
        assertThat(List.of(method.getParameterTypes()))
            .allSatisfy(parameterType -> assertThat(parameterType.getPackageName())
                .startsWith("br.com.rinos.app.api.dto"));
      }
    }
  }

  @Test
  void localRegistrationFacades_shouldPublishOnlyExpectedUseCases_whenInspected() {
    for (Map.Entry<Class<?>, Set<String>> entry : LOCAL_REGISTRATION_FACADES.entrySet()) {
      assertThat(entry.getKey().getDeclaredMethods())
          .extracting(Method::getName)
          .containsExactlyInAnyOrderElementsOf(entry.getValue())
          .noneMatch(methodName -> methodName.startsWith("find")
              || methodName.startsWith("get")
              || methodName.startsWith("search"));
    }
  }
}
