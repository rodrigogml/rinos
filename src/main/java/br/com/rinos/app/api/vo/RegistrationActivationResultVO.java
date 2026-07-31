package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;

/**
 * Resultado público seguro da ativação ou de sua continuação legal.
 *
 * @param status resultado da operação
 * @param activationReference referência opaca quando faltam aceites
 * @param verifiedEmail representação mascarada do e-mail comprovado e somente leitura
 * @param legalDocumentIds documentos que precisam ser apresentados
 * @param expiresAt validade da referência
 * @param fieldErrors erros estruturais por campo
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationActivationResultVO(
    RegistrationActivationStatusEnum status,
    String activationReference,
    String verifiedEmail,
    Set<String> legalDocumentIds,
    Instant expiresAt,
    Map<String, String> fieldErrors) {

  /**
   * Preserva coleções imutáveis e dados de continuação somente no estado correspondente.
   */
  public RegistrationActivationResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    legalDocumentIds = legalDocumentIds == null ? Set.of() : Set.copyOf(legalDocumentIds);
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    if (status == RegistrationActivationStatusEnum.CONSENT_REQUIRED) {
      if (activationReference == null || activationReference.isBlank()
          || verifiedEmail == null || verifiedEmail.isBlank()
          || legalDocumentIds.isEmpty() || expiresAt == null) {
        throw new IllegalArgumentException(
            "consent continuation requires reference, email, documents and expiration");
      }
    } else if (activationReference != null || verifiedEmail != null
        || !legalDocumentIds.isEmpty() || expiresAt != null) {
      throw new IllegalArgumentException(
          "non-continuation result must not contain continuation data");
    }
  }

  /**
   * Cria um resultado simples sem dados de continuação.
   *
   * @param status estado público
   * @return resultado minimizado
   */
  public static RegistrationActivationResultVO of(
      RegistrationActivationStatusEnum status) {
    return new RegistrationActivationResultVO(
        status, null, null, Set.of(), null, Map.of());
  }

  /**
   * Cria uma continuação legal usando a mesma prova já apresentada.
   *
   * @param reference prova opaca
   * @param email representação mascarada do e-mail comprovado
   * @param legalDocumentIds versões obrigatórias ausentes
   * @param expiresAt validade original da prova
   * @return challenge público
   */
  public static RegistrationActivationResultVO consentRequired(
      String reference,
      String email,
      Set<String> legalDocumentIds,
      Instant expiresAt) {
    return new RegistrationActivationResultVO(
        RegistrationActivationStatusEnum.CONSENT_REQUIRED,
        reference,
        email,
        legalDocumentIds,
        expiresAt,
        Map.of());
  }

  /**
   * Cria uma rejeição estrutural por campo.
   *
   * @param fieldErrors erros públicos
   * @return resultado rejeitado
   */
  public static RegistrationActivationResultVO validationRejected(
      Map<String, String> fieldErrors) {
    return new RegistrationActivationResultVO(
        RegistrationActivationStatusEnum.VALIDATION_REJECTED,
        null,
        null,
        Set.of(),
        null,
        fieldErrors);
  }

  /**
   * Resume o resultado sem serializar referência, e-mail ou versões apresentadas.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationActivationResultVO[status=" + status
        + ", continuationData=REDACTED, legalDocumentCount=" + legalDocumentIds.size()
        + ", expiresAt=" + expiresAt + ", fieldErrorCount=" + fieldErrors.size() + "]";
  }
}
