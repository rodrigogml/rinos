package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.repository.LegalDocumentVersionRepository;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.LegalDocumentIntegrityService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fachada pública de documentos jurídicos")
class LegalDocumentFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @Mock
  private LegalConsentService legalConsentService;

  @Mock
  private LegalDocumentVersionRepository documentRepository;

  @Mock
  private LegalDocumentIntegrityService integrityService;

  private LegalDocumentFacadeImpl facade;

  @BeforeEach
  void setUp() {
    facade = new LegalDocumentFacadeImpl(
        legalConsentService,
        documentRepository,
        integrityService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /**
   * Comprova que a camada pública recebe somente metadados estáveis das versões vigentes.
   */
  @Test
  void findCurrentDocuments_shouldMapPersistentDocuments_withoutExposingEntity() {
    LegalDocumentVersionEntity terms = document(
        11L,
        br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum.TERMS_OF_USE,
        "1.0.0",
        true,
        NOW.minusSeconds(60));
    when(legalConsentService.resolveCurrentDocuments(NOW)).thenReturn(List.of(terms));

    var result = facade.findCurrentDocuments();

    assertThat(result).singleElement().satisfies(document -> {
      assertThat(document.reference()).isEqualTo("11");
      assertThat(document.documentType()).isEqualTo(LegalDocumentTypeEnum.TERMS_OF_USE);
      assertThat(document.versionName()).isEqualTo("1.0.0");
      assertThat(document.required()).isTrue();
    });
  }

  /**
   * Comprova a publicação histórica somente depois do início da vigência e com hash válido.
   */
  @Test
  void findPublishedDocument_shouldReturnIntegrityCheckedContent_whenVersionAlreadyStarted() {
    LegalDocumentVersionEntity privacy = document(
        12L,
        br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum.PRIVACY_POLICY,
        "1.0.0",
        true,
        NOW.minusSeconds(60));
    when(documentRepository.findById(12L)).thenReturn(Optional.of(privacy));
    when(integrityService.matches(privacy.getContent(), privacy.getContentHash()))
        .thenReturn(true);

    var result = facade.findPublishedDocument("12");

    assertThat(result).isPresent().get().satisfies(document -> {
      assertThat(document.reference()).isEqualTo("12");
      assertThat(document.documentType()).isEqualTo(LegalDocumentTypeEnum.PRIVACY_POLICY);
      assertThat(document.content()).isEqualTo("# Conteúdo");
      assertThat(document.effectiveAt()).isEqualTo(NOW.minusSeconds(60));
    });
  }

  /**
   * Impede antecipar publicamente uma versão futura mesmo quando a referência for conhecida.
   */
  @Test
  void findPublishedDocument_shouldHideVersion_whenEffectiveAtIsFuture() {
    LegalDocumentVersionEntity future = document(
        13L,
        br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum.TERMS_OF_USE,
        "2.0.0",
        true,
        NOW.plusSeconds(60));
    when(documentRepository.findById(13L)).thenReturn(Optional.of(future));

    assertThat(facade.findPublishedDocument("13")).isEmpty();
    verify(integrityService, never()).matches(
        future.getContent(),
        future.getContentHash());
  }

  /**
   * Referências externas inválidas não chegam ao repository nem produzem diagnóstico técnico.
   */
  @Test
  void findPublishedDocument_shouldReturnEmpty_withoutRepositoryLookup_whenReferenceIsInvalid() {
    assertThat(facade.findPublishedDocument("../draft")).isEmpty();
    assertThat(facade.findPublishedDocument("0")).isEmpty();
    assertThat(facade.findPublishedDocument(null)).isEmpty();

    verify(documentRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
  }

  /**
   * Conteúdo divergente do hash falha fechado e nunca é entregue à apresentação.
   */
  @Test
  void findPublishedDocument_shouldFailClosed_whenContentIntegrityIsInvalid() {
    LegalDocumentVersionEntity terms = document(
        14L,
        br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum.TERMS_OF_USE,
        "1.0.0",
        true,
        NOW.minusSeconds(60));
    when(documentRepository.findById(14L)).thenReturn(Optional.of(terms));
    when(integrityService.matches(terms.getContent(), terms.getContentHash()))
        .thenReturn(false);

    assertThatThrownBy(() -> facade.findPublishedDocument("14"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Legal document content integrity check failed");
  }

  private static LegalDocumentVersionEntity document(
      Long id,
      br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum type,
      String version,
      boolean required,
      Instant effectiveAt) {
    LegalDocumentVersionEntity document = new LegalDocumentVersionEntity(
        type,
        version,
        required,
        "# Conteúdo",
        new byte[32],
        effectiveAt,
        null);
    ReflectionTestUtils.setField(document, "id", id);
    return document;
  }
}
