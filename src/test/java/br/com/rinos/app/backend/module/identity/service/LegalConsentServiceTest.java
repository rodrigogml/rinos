package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.LegalConsentEntity;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LegalConsentRepository;
import br.com.rinos.app.backend.module.identity.repository.LegalDocumentVersionRepository;
import br.com.rinos.app.backend.module.identity.vo.LegalConsentRecordingVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;

@DisplayName("Consentimentos legais da identidade")
class LegalConsentServiceTest {

  private static final Instant DECIDED_AT = Instant.parse("2026-07-29T18:00:00Z");

  private LegalDocumentVersionRepository documentRepository;
  private LegalConsentRepository consentRepository;
  private LegalConsentService service;
  private UserEntity user;
  private RegistrationEntity registration;
  private LegalDocumentVersionEntity terms;
  private LegalDocumentVersionEntity privacy;
  private LegalDocumentVersionEntity marketing;

  /**
   * Prepara catálogo mínimo vigente e identidade persistida.
   */
  @BeforeEach
  void setUp() {
    documentRepository = mock(LegalDocumentVersionRepository.class);
    consentRepository = mock(LegalConsentRepository.class);
    service = new LegalConsentService(documentRepository, consentRepository);
    user = user(31L);
    registration = registration(user, 41L);
    terms = document(51L, LegalDocumentTypeEnum.TERMS_OF_USE, "1.0", true);
    privacy = document(52L, LegalDocumentTypeEnum.PRIVACY_POLICY, "1.0", true);
    marketing = document(53L, LegalDocumentTypeEnum.MARKETING, "1.0", false);
    when(documentRepository.findEffectiveAt(DECIDED_AT))
        .thenReturn(List.of(terms, privacy, marketing));
    when(consentRepository.findByUserIdAndLegalDocumentVersionIdIn(any(), any()))
        .thenReturn(List.of());
  }

  /**
   * Resolve uma versão por finalidade quando o catálogo é íntegro.
   */
  @Test
  void resolveCurrentDocuments_shouldReturnCurrentSet_whenBaselineIsComplete() {
    List<LegalDocumentVersionEntity> result =
        service.resolveCurrentDocuments(DECIDED_AT);

    assertThat(result).containsExactly(terms, privacy, marketing);
  }

  /**
   * Impede ativação sobre duas versões simultaneamente vigentes da mesma finalidade.
   */
  @Test
  void resolveCurrentDocuments_shouldRejectOverlap_whenTypeHasTwoCurrentVersions() {
    LegalDocumentVersionEntity overlapping =
        document(54L, LegalDocumentTypeEnum.TERMS_OF_USE, "2.0", true);
    when(documentRepository.findEffectiveAt(DECIDED_AT))
        .thenReturn(List.of(terms, overlapping, privacy));

    assertThatThrownBy(() -> service.resolveCurrentDocuments(DECIDED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Multiple legal document versions");
  }

  /**
   * Impede cadastro quando falta um documento-base obrigatório.
   */
  @Test
  void resolveCurrentDocuments_shouldRejectCatalog_whenPrivacyPolicyIsMissing() {
    when(documentRepository.findEffectiveAt(DECIDED_AT)).thenReturn(List.of(terms));

    assertThatThrownBy(() -> service.resolveCurrentDocuments(DECIDED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PRIVACY_POLICY");
  }

  /**
   * Aceita a fotografia publicada exibida pelo cadastro mesmo quando uma versão foi retirada
   * antes da submissão.
   */
  @Test
  void validatePublishedAcceptances_shouldAcceptRetiredPresentedVersion() {
    terms.setRetiredAt(DECIDED_AT.minusSeconds(60));
    when(documentRepository.findAllById(any()))
        .thenReturn(List.of(terms, privacy));

    Map<Long, LegalConsentDecisionEnum> decisions =
        service.validatePublishedAcceptances(List.of(51L, 52L), DECIDED_AT);

    assertThat(decisions).containsExactlyInAnyOrderEntriesOf(Map.of(
        51L, LegalConsentDecisionEnum.ACCEPTED,
        52L, LegalConsentDecisionEnum.ACCEPTED));
  }

  /**
   * Não permite usar no cadastro uma versão futura que ainda não foi publicada.
   */
  @Test
  void validatePublishedAcceptances_shouldRejectFutureVersion() {
    LegalDocumentVersionEntity futureTerms = document(
        61L,
        LegalDocumentTypeEnum.TERMS_OF_USE,
        "2.0",
        true,
        DECIDED_AT.plusSeconds(60));
    when(documentRepository.findAllById(any()))
        .thenReturn(List.of(futureTerms, privacy));

    assertThatThrownBy(() ->
        service.validatePublishedAcceptances(List.of(61L, 52L), DECIDED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Decision references a legal document that is not published yet");
  }

  /**
   * Exige que a fotografia apresentada contenha os dois documentos-base aceitos.
   */
  @Test
  void validatePublishedAcceptances_shouldRejectMissingBaselineDocument() {
    when(documentRepository.findAllById(any())).thenReturn(List.of(terms));

    assertThatThrownBy(() ->
        service.validatePublishedAcceptances(List.of(51L), DECIDED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Every presented baseline legal document must be explicitly accepted");
  }

  /**
   * Registra a versão retirada como evidência sem convertê-la em aceite da versão atual.
   */
  @Test
  void recordPublishedDecisions_shouldPersistExactPresentedVersions() {
    terms.setRetiredAt(DECIDED_AT.minusSeconds(60));
    when(documentRepository.findAllById(any()))
        .thenReturn(List.of(terms, privacy));
    List<LegalConsentEntity> saved = captureSavedConsents();

    LegalConsentRecordingVO result = service.recordPublishedDecisions(
        user,
        registration,
        Map.of(
            51L, LegalConsentDecisionEnum.ACCEPTED,
            52L, LegalConsentDecisionEnum.ACCEPTED),
        DECIDED_AT);

    assertThat(result.recorded()).isEqualTo(2);
    assertThat(saved).extracting(
        consent -> consent.getLegalDocumentVersion().getId())
        .containsExactlyInAnyOrder(51L, 52L);
  }

  /**
   * Registra obrigatórios aceitos e preserva separadamente uma recusa opcional.
   */
  @Test
  void recordCurrentDecisions_shouldPreserveOptionalDecision_whenRequiredAreAccepted() {
    List<LegalConsentEntity> saved = captureSavedConsents();
    Map<Long, LegalConsentDecisionEnum> decisions = Map.of(
        51L, LegalConsentDecisionEnum.ACCEPTED,
        52L, LegalConsentDecisionEnum.ACCEPTED,
        53L, LegalConsentDecisionEnum.DECLINED);

    LegalConsentRecordingVO result = service.recordCurrentDecisions(
        user,
        registration,
        decisions,
        DECIDED_AT);

    assertThat(result.recorded()).isEqualTo(3);
    assertThat(result.alreadyExisting()).isZero();
    assertThat(saved).extracting(LegalConsentEntity::getDecision)
        .containsExactlyInAnyOrder(
            LegalConsentDecisionEnum.ACCEPTED,
            LegalConsentDecisionEnum.ACCEPTED,
            LegalConsentDecisionEnum.DECLINED);
  }

  /**
   * Não converte a ausência de escolha opcional em recusa.
   */
  @Test
  void recordCurrentDecisions_shouldNotCreateOptionalConsent_whenDecisionIsAbsent() {
    List<LegalConsentEntity> saved = captureSavedConsents();

    service.recordCurrentDecisions(
        user,
        registration,
        Map.of(
            51L, LegalConsentDecisionEnum.ACCEPTED,
            52L, LegalConsentDecisionEnum.ACCEPTED),
        DECIDED_AT);

    assertThat(saved).hasSize(2);
    assertThat(saved).extracting(
        consent -> consent.getLegalDocumentVersion().getDocumentType())
        .doesNotContain(LegalDocumentTypeEnum.MARKETING);
  }

  /**
   * Rejeita recusa ou ausência de qualquer versão obrigatória.
   */
  @Test
  void recordCurrentDecisions_shouldRejectRequest_whenRequiredDecisionIsNotAccepted() {
    assertThatThrownBy(() -> service.recordCurrentDecisions(
        user,
        registration,
        Map.of(
            51L, LegalConsentDecisionEnum.ACCEPTED,
            52L, LegalConsentDecisionEnum.DECLINED),
        DECIDED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Every required legal document must be explicitly accepted");
  }

  /**
   * Mantém idempotência para a mesma decisão e recusa sua reescrita.
   */
  @Test
  void recordCurrentDecisions_shouldRejectChange_whenVersionAlreadyHasOtherDecision() {
    LegalConsentEntity existing = new LegalConsentEntity(
        user,
        registration,
        marketing,
        LegalConsentDecisionEnum.DECLINED,
        DECIDED_AT.minusSeconds(60));
    when(consentRepository.findByUserIdAndLegalDocumentVersionIdIn(any(), any()))
        .thenReturn(List.of(existing));

    assertThatThrownBy(() -> service.recordCurrentDecisions(
        user,
        registration,
        Map.of(
            51L, LegalConsentDecisionEnum.ACCEPTED,
            52L, LegalConsentDecisionEnum.ACCEPTED,
            53L, LegalConsentDecisionEnum.ACCEPTED),
        DECIDED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("A legal decision cannot be changed for the same document version");
  }

  /**
   * Detecta uma nova versão obrigatória mesmo quando a anterior foi aceita.
   */
  @Test
  void evaluateRequiredConsents_shouldRequireNewAcceptance_whenCurrentVersionChanged() {
    LegalDocumentVersionEntity newTerms =
        document(61L, LegalDocumentTypeEnum.TERMS_OF_USE, "2.0", true);
    when(documentRepository.findEffectiveAt(DECIDED_AT))
        .thenReturn(List.of(newTerms, privacy, marketing));
    LegalConsentEntity acceptedPrivacy = new LegalConsentEntity(
        user,
        registration,
        privacy,
        LegalConsentDecisionEnum.ACCEPTED,
        DECIDED_AT.minusSeconds(60));
    when(consentRepository.findByUserIdAndLegalDocumentVersionIdIn(
        31L,
        List.of(61L, 52L))).thenReturn(List.of(acceptedPrivacy));

    LegalRequirementStatusVO result =
        service.evaluateRequiredConsents(31L, DECIDED_AT);

    assertThat(result.currentRequiredVersionIds()).containsExactly(61L, 52L);
    assertThat(result.missingRequiredVersionIds()).containsExactly(61L);
    assertThat(result.requiresConsent()).isTrue();
  }

  private List<LegalConsentEntity> captureSavedConsents() {
    List<LegalConsentEntity> saved = new ArrayList<>();
    when(consentRepository.saveAll(any())).thenAnswer(invocation -> {
      Iterable<LegalConsentEntity> consents = invocation.getArgument(0);
      consents.forEach(saved::add);
      return saved;
    });
    return saved;
  }

  private static UserEntity user(Long id) {
    UserEntity entity = new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static RegistrationEntity registration(UserEntity owner, Long id) {
    RegistrationEntity entity = new RegistrationEntity(
        owner,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        DECIDED_AT.plusSeconds(86_400));
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private LegalDocumentVersionEntity document(
      Long id,
      LegalDocumentTypeEnum type,
      String version,
      boolean required) {
    return document(
        id,
        type,
        version,
        required,
        DECIDED_AT.minusSeconds(3_600));
  }

  private LegalDocumentVersionEntity document(
      Long id,
      LegalDocumentTypeEnum type,
      String version,
      boolean required,
      Instant effectiveAt) {
    String content = type + " " + version;
    LegalDocumentVersionEntity entity = new LegalDocumentVersionEntity(
        type,
        version,
        required,
        content,
        new LegalDocumentIntegrityService().hash(content),
        effectiveAt,
        null);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
