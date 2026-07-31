package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;

/**
 * Conteúdo público e íntegro de uma versão jurídica já vigente.
 *
 * @param reference referência estável da versão
 * @param documentType finalidade do documento
 * @param versionName versão legível
 * @param content conteúdo Markdown exato
 * @param effectiveAt início UTC da vigência
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record LegalDocumentContentVO(
    String reference,
    LegalDocumentTypeEnum documentType,
    String versionName,
    String content,
    Instant effectiveAt) {

  public LegalDocumentContentVO {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    documentType = Objects.requireNonNull(documentType, "documentType must not be null");
    if (versionName == null || versionName.isBlank()) {
      throw new IllegalArgumentException("versionName must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
  }
}
