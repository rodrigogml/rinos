package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.repository.LegalDocumentVersionRepository;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.LegalDocumentIntegrityService;

/**
 * Converte o catálogo jurídico global em contratos públicos mínimos e verifica seu conteúdo.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class LegalDocumentFacadeImpl implements LegalDocumentFacade {

  private final LegalConsentService legalConsentService;
  private final LegalDocumentVersionRepository documentRepository;
  private final LegalDocumentIntegrityService integrityService;
  private final Clock clock;

  /**
   * Cria a fachada com o relógio UTC da aplicação.
   *
   * @param legalConsentService resolução e invariantes das versões vigentes
   * @param documentRepository catálogo global
   * @param integrityService verificação do conteúdo imutável
   */
  @Autowired
  public LegalDocumentFacadeImpl(
      LegalConsentService legalConsentService,
      LegalDocumentVersionRepository documentRepository,
      LegalDocumentIntegrityService integrityService) {
    this(
        legalConsentService,
        documentRepository,
        integrityService,
        Clock.systemUTC());
  }

  LegalDocumentFacadeImpl(
      LegalConsentService legalConsentService,
      LegalDocumentVersionRepository documentRepository,
      LegalDocumentIntegrityService integrityService,
      Clock clock) {
    this.legalConsentService = Objects.requireNonNull(
        legalConsentService,
        "legalConsentService must not be null");
    this.documentRepository = Objects.requireNonNull(
        documentRepository,
        "documentRepository must not be null");
    this.integrityService = Objects.requireNonNull(
        integrityService,
        "integrityService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<LegalDocumentReferenceVO> findCurrentDocuments() {
    return legalConsentService.resolveCurrentDocuments(clock.instant()).stream()
        .map(LegalDocumentFacadeImpl::toReference)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LegalDocumentContentVO> findPublishedDocument(String reference) {
    Optional<Long> id = parseReference(reference);
    if (id.isEmpty()) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    return documentRepository.findById(id.orElseThrow())
        .filter(document -> !document.getEffectiveAt().isAfter(now))
        .map(this::toContent);
  }

  private LegalDocumentContentVO toContent(LegalDocumentVersionEntity document) {
    if (!integrityService.matches(document.getContent(), document.getContentHash())) {
      throw new IllegalStateException("Legal document content integrity check failed");
    }
    return new LegalDocumentContentVO(
        document.getId().toString(),
        toPublicType(document),
        document.getVersionName(),
        document.getContent(),
        document.getEffectiveAt());
  }

  private static LegalDocumentReferenceVO toReference(
      LegalDocumentVersionEntity document) {
    return new LegalDocumentReferenceVO(
        document.getId().toString(),
        toPublicType(document),
        document.getVersionName(),
        document.isRequired());
  }

  private static LegalDocumentTypeEnum toPublicType(
      LegalDocumentVersionEntity document) {
    return LegalDocumentTypeEnum.valueOf(document.getDocumentType().name());
  }

  private static Optional<Long> parseReference(String reference) {
    if (reference == null || reference.isBlank()) {
      return Optional.empty();
    }
    try {
      long id = Long.parseLong(reference);
      return id > 0 ? Optional.of(id) : Optional.empty();
    } catch (NumberFormatException invalidReference) {
      return Optional.empty();
    }
  }
}
