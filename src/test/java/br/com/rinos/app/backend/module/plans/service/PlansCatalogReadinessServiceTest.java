package br.com.rinos.app.backend.module.plans.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

class PlansCatalogReadinessServiceTest {

  @Test
  void validate_shouldComplete_whenBothDefaultsAndCompositionsAreExact() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
        .thenReturn(1L, 1L, 0L, 1L);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

    assertThatCode(() -> new PlansCatalogReadinessService(jdbc).validate())
        .doesNotThrowAnyException();
  }

  @Test
  void validate_shouldFailClosed_whenAPublishedDefaultIsMissing() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(0L);

    assertThatThrownBy(() -> new PlansCatalogReadinessService(jdbc).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("personal default plan/version is unavailable or ambiguous");
  }
}
