package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.backend.module.identity.facade.RegistrationActivationFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationCancellationFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationResendFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationStartFacadeImpl;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

@DisplayName("Fronteiras transacionais do ciclo local de cadastro")
class RegistrationTransactionBoundaryTest {

  @Test
  void persistentCommands_shouldDeclareTransaction_whenTheyOwnCompleteWrite() throws Exception {
    assertTransactional(
        RegistrationCreationService.class,
        "create",
        String.class,
        String.class,
        Map.class,
        OriginAddressVO.class,
        Locale.class,
        UUID.class,
        Instant.class);
    assertTransactional(
        RegistrationResendService.class,
        "resend",
        Long.class,
        Locale.class,
        UUID.class,
        Instant.class);
    assertTransactional(
        RegistrationActivationService.class,
        "activate",
        String.class,
        UUID.class,
        Instant.class);
    assertTransactional(
        RegistrationActivationService.class,
        "completeConsent",
        String.class,
        List.class,
        UUID.class,
        Instant.class);
    assertTransactional(
        RegistrationCancellationService.class,
        "issue",
        Long.class,
        Locale.class,
        UUID.class,
        Instant.class);
    assertTransactional(
        RegistrationCancellationService.class,
        "confirm",
        String.class,
        String.class,
        UUID.class,
        Instant.class);
  }

  @Test
  void publicFacadeMethods_shouldNotOwnTransaction_whenTheyMapCommittedOutcome() throws Exception {
    assertNotTransactional(
        RegistrationStartFacadeImpl.class,
        "start",
        RegistrationStartRequestDTO.class);
    assertNotTransactional(
        RegistrationResendFacadeImpl.class,
        "resend",
        RegistrationResendRequestDTO.class);
    assertNotTransactional(
        RegistrationActivationFacadeImpl.class,
        "activate",
        RegistrationActivationRequestDTO.class);
    assertNotTransactional(
        RegistrationActivationFacadeImpl.class,
        "completeConsent",
        ActivationConsentRequestDTO.class);
    assertNotTransactional(
        RegistrationCancellationFacadeImpl.class,
        "requestCancellation",
        RegistrationCancellationRequestDTO.class);
    assertNotTransactional(
        RegistrationCancellationFacadeImpl.class,
        "confirmCancellation",
        RegistrationCancellationConfirmationDTO.class);
  }

  @Test
  void backendFacades_shouldCoordinateServices_withoutRepositoryDependency() {
    List<Class<?>> facadeTypes = List.of(
        RegistrationStartFacadeImpl.class,
        RegistrationResendFacadeImpl.class,
        RegistrationActivationFacadeImpl.class,
        RegistrationCancellationFacadeImpl.class);

    List<Class<?>> fieldTypes = facadeTypes.stream()
        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
        .map(Field::getType)
        .toList();

    assertThat(fieldTypes)
        .noneMatch(type -> type.getPackageName().contains(".repository"));
  }

  private static void assertTransactional(
      Class<?> type,
      String methodName,
      Class<?>... parameterTypes) throws Exception {
    Method method = type.getMethod(methodName, parameterTypes);

    assertThat(method.getAnnotation(Transactional.class))
        .as("%s#%s deve possuir fronteira transacional", type.getSimpleName(), methodName)
        .isNotNull();
  }

  private static void assertNotTransactional(
      Class<?> type,
      String methodName,
      Class<?>... parameterTypes) throws Exception {
    Method method = type.getMethod(methodName, parameterTypes);

    assertThat(type.getAnnotation(Transactional.class))
        .as("%s não deve abrir transação para toda a facade", type.getSimpleName())
        .isNull();
    assertThat(method.getAnnotation(Transactional.class))
        .as("%s#%s deve delegar à fronteira transacional interna", type.getSimpleName(), methodName)
        .isNull();
  }
}
