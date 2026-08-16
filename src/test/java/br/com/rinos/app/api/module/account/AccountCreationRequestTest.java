package br.com.rinos.app.api.module.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;

class AccountCreationRequestTest {

  @Test
  void shouldNormalizeCataloguedValuesAndRedactSensitiveFields() {
    UUID key = UUID.randomUUID();
    AccountCreationRequest request = new AccountCreationRequest(
        key, "  Minha   Empresa  ", "brl", "America/Sao_Paulo", "secret-token", true);

    assertThat(request.displayName()).isEqualTo("Minha Empresa");
    assertThat(request.baseCurrency()).isEqualTo("BRL");
    assertThat(request.timeZoneId()).isEqualTo("America/Sao_Paulo");
    assertThat(request.toString())
        .doesNotContain(key.toString(), "Minha Empresa", "secret-token")
        .contains("REDACTED");
  }

  @Test
  void shouldRejectInvalidOrUnconfirmedRequests() {
    UUID key = UUID.randomUUID();

    assertThatThrownBy(() -> new AccountCreationRequest(
        key, "Conta", "INVALID", "UTC", null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountCreationRequest(
        key, "Conta", "BRL", "Not/AZone", null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountCreationRequest(
        key, "Conta", "BRL", "UTC", null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
