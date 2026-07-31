package br.com.rinos.app.api.vo;

import java.util.Objects;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;

/**
 * Referência pública de uma versão jurídica vigente.
 *
 * @param reference referência estável usada pelo aceite e pela rota de leitura
 * @param documentType finalidade do documento
 * @param versionName versão legível
 * @param required obrigatoriedade no cadastro
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record LegalDocumentReferenceVO(
    String reference,
    LegalDocumentTypeEnum documentType,
    String versionName,
    boolean required) {

  public LegalDocumentReferenceVO {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    documentType = Objects.requireNonNull(documentType, "documentType must not be null");
    if (versionName == null || versionName.isBlank()) {
      throw new IllegalArgumentException("versionName must not be blank");
    }
  }
}
