package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import br.com.rinos.app.backend.module.identity.service.PasswordPreparationService;
import br.com.rinos.app.backend.module.identity.service.PasswordRecoveryService;

@DisplayName("Fachada de recuperação de senha")
class PasswordRecoveryFacadeImplTest {

  @Test
  void context_shouldCreateFacade_whenTestConstructorAlsoExists() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          PasswordRecoveryService.class,
          () -> mock(PasswordRecoveryService.class));
      context.registerBean(
          PasswordPreparationService.class,
          () -> mock(PasswordPreparationService.class));
      context.register(PasswordRecoveryFacadeImpl.class);

      context.refresh();

      assertThat(context.getBean(PasswordRecoveryFacadeImpl.class)).isNotNull();
    }
  }
}
