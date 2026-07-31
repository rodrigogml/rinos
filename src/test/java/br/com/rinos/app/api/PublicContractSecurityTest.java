package br.com.rinos.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;
import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;
import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;
import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;

/**
 * Protege a fronteira pública contra dependências de persistência, mutação externa e diagnóstico
 * com dados sensíveis.
 *
 * @author Rodrigo Leitão
 */
class PublicContractSecurityTest {

  private static final UUID CORRELATION_ID =
      UUID.fromString("9e81db19-37b7-4ef2-a378-d28a4d6047a4");
  private static final Instant EXPIRES_AT = Instant.parse("2026-07-30T18:00:00Z");

  private static final List<Class<?>> PUBLIC_CONTRACT_TYPES = List.of(
      ActivationConsentRequestDTO.class,
      ExternalRegistrationCompletionRequestDTO.class,
      RegistrationActivationRequestDTO.class,
      RegistrationCancellationConfirmationDTO.class,
      RegistrationCancellationRequestDTO.class,
      RegistrationResendRequestDTO.class,
      RegistrationStartRequestDTO.class,
      ExternalRegistrationCompletionResultVO.class,
      GoogleIdentityResolutionRequestVO.class,
      GoogleIdentityResolutionResultVO.class,
      LegalDocumentContentVO.class,
      LegalDocumentReferenceVO.class,
      RegistrationActivationResultVO.class,
      RegistrationCancellationConfirmationResultVO.class,
      RegistrationCancellationRequestResultVO.class,
      RegistrationResendResultVO.class,
      RegistrationStartResultVO.class,
      RemoteOriginRequestVO.class,
      RinosUserPrincipalVO.class);

  @Test
  void publicContracts_shouldNotReferencePersistenceTypes() {
    for (Class<?> contractType : PUBLIC_CONTRACT_TYPES) {
      assertThat(contractType.getDeclaredFields())
          .extracting(Field::getGenericType)
          .allSatisfy(type -> assertThat(type.getTypeName())
              .doesNotContain(".entity.", ".repository."));
      if (contractType.isRecord()) {
        assertThat(contractType.getRecordComponents())
            .extracting(RecordComponent::getGenericType)
            .allSatisfy(type -> assertThat(type.getTypeName())
                .doesNotContain(".entity.", ".repository."));
      }
    }
  }

  @Test
  void publicContracts_shouldDefensivelyCopyMutableInputs() {
    char[] sourcePassword = "Sensitive1!".toCharArray();
    List<String> acceptedDocuments = new ArrayList<>(List.of("terms-v1"));
    RegistrationStartRequestDTO start = new RegistrationStartRequestDTO(
        "person@example.test",
        sourcePassword,
        acceptedDocuments,
        "192.0.2.10",
        Locale.of("pt", "BR"),
        CORRELATION_ID);
    sourcePassword[0] = 'X';
    acceptedDocuments.add("privacy-v1");

    assertThat(start.consumePassword()).containsExactly("Sensitive1!".toCharArray());
    assertThat(start.consumePassword()).containsOnly('\0');
    assertThat(start.getAcceptedLegalDocumentIds()).containsExactly("terms-v1");
    assertThatThrownBy(() -> start.getAcceptedLegalDocumentIds().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);

    Set<String> documentIds = new HashSet<>(Set.of("privacy-v2"));
    Map<String, String> fieldErrors = new HashMap<>(Map.of("proof", "error.proof"));
    RegistrationActivationResultVO activation = new RegistrationActivationResultVO(
        RegistrationActivationStatusEnum.CONSENT_REQUIRED,
        "activation-reference",
        "p***@example.test",
        documentIds,
        EXPIRES_AT,
        fieldErrors);
    documentIds.add("terms-v2");
    fieldErrors.put("other", "error.other");

    assertThat(activation.legalDocumentIds()).containsExactly("privacy-v2");
    assertThat(activation.fieldErrors()).containsExactlyEntriesOf(Map.of("proof", "error.proof"));
    assertThatThrownBy(() -> activation.legalDocumentIds().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> activation.fieldErrors().put("other", "error.other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void publicContracts_shouldNotExposeSensitiveValuesInDiagnosticStrings() {
    String email = "person@example.test";
    String proof = "secret-proof";
    String reference = "secret-reference";
    String issuer = "https://issuer.example.test";
    String subject = "external-subject";
    String address = "192.0.2.10";
    String forwarded = "for=198.51.100.20";

    List<Object> contracts = List.of(
        new RegistrationStartRequestDTO(
            email,
            "Sensitive1!".toCharArray(),
            List.of("terms-v1"),
            address,
            Locale.of("pt", "BR"),
            CORRELATION_ID),
        new RegistrationResendRequestDTO(email, Locale.of("pt", "BR"), CORRELATION_ID),
        new RegistrationActivationRequestDTO(email, proof, CORRELATION_ID),
        new ActivationConsentRequestDTO(reference, List.of("terms-v2"), CORRELATION_ID),
        new ExternalRegistrationCompletionRequestDTO(
            reference,
            List.of("terms-v1"),
            CORRELATION_ID),
        new RegistrationCancellationRequestDTO(
            email,
            Locale.of("pt", "BR"),
            CORRELATION_ID),
        new RegistrationCancellationConfirmationDTO(email, proof, CORRELATION_ID),
        new GoogleIdentityResolutionRequestVO(
            "google",
            issuer,
            subject,
            email,
            true,
            CORRELATION_ID),
        GoogleIdentityResolutionResultVO.continuation(
            reference,
            "google",
            email,
            EXPIRES_AT),
        RegistrationActivationResultVO.consentRequired(
            reference,
            email,
            Set.of("terms-v2"),
            EXPIRES_AT),
        new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
            reference,
            EXPIRES_AT,
            Map.of()),
        ExternalRegistrationCompletionResultVO.authenticated(
            new RinosUserPrincipalVO(42L, email)),
        new RemoteOriginRequestVO(address, forwarded, "198.51.100.20"),
        new RinosUserPrincipalVO(42L, email));

    assertThat(contracts)
        .extracting(Object::toString)
        .allSatisfy(description -> assertThat(description)
            .doesNotContain(
                email,
                proof,
                reference,
                issuer,
                subject,
                address,
                forwarded,
                "198.51.100.20",
                "Sensitive1!"));
  }

  @Test
  void resultValueObjects_shouldEnforceTheirStructuralInvariants() {
    assertThatThrownBy(() -> GoogleIdentityResolutionResultVO.of(
        GoogleIdentityResolutionStatusEnum.CONTINUATION_REQUIRED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RegistrationActivationResultVO.of(
        RegistrationActivationStatusEnum.CONSENT_REQUIRED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExternalRegistrationCompletionResultVO.of(
        ExternalRegistrationCompletionStatusEnum.AUTHENTICATED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RegistrationCancellationRequestResultVO(
        RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
        null,
        EXPIRES_AT,
        Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatNoException().isThrownBy(() -> {
      RegistrationStartResultVO.of(RegistrationStartStatusEnum.EMAIL_SENT);
      RegistrationResendResultVO.of(RegistrationResendStatusEnum.REQUEST_ACCEPTED);
      new RegistrationCancellationConfirmationResultVO(
          RegistrationCancellationConfirmationStatusEnum.CANCELLED,
          Map.of());
      new LegalDocumentReferenceVO(
          "terms-v1",
          LegalDocumentTypeEnum.TERMS_OF_USE,
          "1.0",
          true);
    });
  }
}
