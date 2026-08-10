package br.com.rinos.app.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pedido reduzido de autenticação por passkey")
class PasskeyAuthenticationRequestDTOTest {

  @Test
  void request_shouldDefensivelyCopyHandleAndRedactDiagnosticRepresentation() {
    byte[] handle = new byte[32];
    handle[0] = 7;
    PasskeyAuthenticationRequestDTO request = new PasskeyAuthenticationRequestDTO(
        handle, Instant.parse("2026-08-10T12:00:00Z"), UUID.randomUUID());

    handle[0] = 9;
    byte[] exposed = request.userHandle();
    exposed[0] = 11;

    assertThat(request.userHandle()[0]).isEqualTo((byte) 7);
    assertThat(request.toString()).contains("userHandle=REDACTED").doesNotContain("[7, 0");
  }

  @Test
  void request_shouldRejectHandleOutsidePersistentContract() {
    assertThatThrownBy(() -> new PasskeyAuthenticationRequestDTO(
        new byte[15], Instant.now(), UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
