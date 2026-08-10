package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.List;

import br.com.rinos.app.api.enums.RecoveryCodeGenerationStatusEnum;

/**
 * Resultado da geração; os códigos legíveis existem somente no estado {@code GENERATED}.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record RecoveryCodeGenerationResultVO(
    RecoveryCodeGenerationStatusEnum status,
    String setReference,
    Instant issuedAt,
    List<String> codes) {

  /** Impede respostas parciais e protege a lista contra alterações externas. */
  public RecoveryCodeGenerationResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    codes = codes == null ? List.of() : List.copyOf(codes);
    boolean generated = status == RecoveryCodeGenerationStatusEnum.GENERATED;
    boolean completePresentation = setReference != null && !setReference.isBlank()
        && issuedAt != null
        && codes.size() == 10
        && codes.stream().noneMatch(code -> code == null || code.isBlank());
    if (generated != completePresentation) {
      throw new IllegalArgumentException(
          "presentation is allowed only for a complete generated result");
    }
  }

  /**
   * Cria um resultado terminal sem transportar códigos ou metadados do conjunto.
   *
   * @param status estado terminal diferente de {@code GENERATED}
   * @return resultado sanitizado
   */
  public static RecoveryCodeGenerationResultVO terminal(
      RecoveryCodeGenerationStatusEnum status) {
    return new RecoveryCodeGenerationResultVO(status, null, null, List.of());
  }

  @Override
  public String toString() {
    return "RecoveryCodeGenerationResultVO[status=" + status
        + ", setReference=" + setReference
        + ", issuedAt=" + issuedAt + ", codes=REDACTED]";
  }
}
