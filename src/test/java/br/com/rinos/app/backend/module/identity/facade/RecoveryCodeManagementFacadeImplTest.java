package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.RecoveryCodeGenerationRequestDTO;
import br.com.rinos.app.api.enums.RecoveryCodeGenerationStatusEnum;
import br.com.rinos.app.api.vo.RecoveryCodeGenerationResultVO;
import br.com.rinos.app.backend.module.identity.service.RecoveryCodeService;
import br.com.rinos.app.backend.module.identity.vo.IssuedRecoveryCodeSetVO;

@DisplayName("Fachada de códigos de recuperação")
class RecoveryCodeManagementFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
  private static final UUID CORRELATION_ID = UUID.fromString(
      "ec61be88-10dc-47cb-b0d3-74e812e6b7c2");

  @Test
  void generate_shouldExposeCompletePresentationOnce_whenServiceCompletes() {
    RecoveryCodeService service = mock(RecoveryCodeService.class);
    List<String> rawCodes = rawCodes();
    UUID reference = UUID.randomUUID();
    when(service.generate(17L, CORRELATION_ID, NOW))
        .thenReturn(new IssuedRecoveryCodeSetVO(reference, NOW, rawCodes));
    RecoveryCodeManagementFacadeImpl facade = new RecoveryCodeManagementFacadeImpl(service);

    RecoveryCodeGenerationResultVO result = facade.generate(
        new RecoveryCodeGenerationRequestDTO(17L, CORRELATION_ID, NOW));

    assertThat(result.status()).isEqualTo(RecoveryCodeGenerationStatusEnum.GENERATED);
    assertThat(result.setReference()).isEqualTo(reference.toString());
    assertThat(result.codes()).containsExactlyElementsOf(rawCodes);
    assertThat(result.toString()).doesNotContain(rawCodes.getFirst()).contains("codes=REDACTED");
  }

  @Test
  void generate_shouldRejectInvalidRequest_withoutCallingService() {
    RecoveryCodeService service = mock(RecoveryCodeService.class);
    RecoveryCodeManagementFacadeImpl facade = new RecoveryCodeManagementFacadeImpl(service);

    RecoveryCodeGenerationResultVO result = facade.generate(
        new RecoveryCodeGenerationRequestDTO(0, CORRELATION_ID, NOW));

    assertThat(result.status()).isEqualTo(RecoveryCodeGenerationStatusEnum.REJECTED);
    assertThat(result.codes()).isEmpty();
  }

  @Test
  void generate_shouldFailClosedWithoutSecrets_whenBackendIsUnavailable() {
    RecoveryCodeService service = mock(RecoveryCodeService.class);
    when(service.generate(17L, CORRELATION_ID, NOW))
        .thenThrow(new IllegalStateException("backend detail"));
    RecoveryCodeManagementFacadeImpl facade = new RecoveryCodeManagementFacadeImpl(service);

    RecoveryCodeGenerationResultVO result = facade.generate(
        new RecoveryCodeGenerationRequestDTO(17L, CORRELATION_ID, NOW));

    assertThat(result.status()).isEqualTo(RecoveryCodeGenerationStatusEnum.UNAVAILABLE);
    assertThat(result.codes()).isEmpty();
    assertThat(result.toString()).doesNotContain("backend detail");
  }

  private static List<String> rawCodes() {
    return IntStream.rangeClosed(1, 10)
        .mapToObj(index -> "AAAA-BBBB-%04d".formatted(index))
        .toList();
  }
}
