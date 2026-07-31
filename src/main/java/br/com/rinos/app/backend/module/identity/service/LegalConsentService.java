package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.LegalConsentEntity;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.repository.LegalConsentRepository;
import br.com.rinos.app.backend.module.identity.repository.LegalDocumentVersionRepository;
import br.com.rinos.app.backend.module.identity.vo.LegalConsentRecordingVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;

/**
 * Resolve versões legais vigentes e registra decisões imutáveis do cadastro.
 *
 * <p>Termos de uso e política de privacidade são sempre obrigatórios. A ausência de decisão
 * opcional não é convertida automaticamente em recusa.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class LegalConsentService {

  private static final Set<LegalDocumentTypeEnum> BASELINE_REQUIRED_TYPES =
      EnumSet.of(
          LegalDocumentTypeEnum.TERMS_OF_USE,
          LegalDocumentTypeEnum.PRIVACY_POLICY);

  private final LegalDocumentVersionRepository documentRepository;
  private final LegalConsentRepository consentRepository;

  /**
   * Cria o serviço sobre o catálogo e as evidências globais.
   *
   * @param documentRepository versões legais imutáveis
   * @param consentRepository decisões dos usuários
   */
  public LegalConsentService(
      LegalDocumentVersionRepository documentRepository,
      LegalConsentRepository consentRepository) {
    this.documentRepository = documentRepository;
    this.consentRepository = consentRepository;
  }

  /**
   * Retorna uma única versão vigente por finalidade e valida o conjunto mínimo.
   *
   * @param effectiveAt instante UTC de referência
   * @return versões vigentes ordenadas pelo repository
   * @throws IllegalStateException quando falta documento-base, sua obrigatoriedade está incorreta
   *     ou duas versões da mesma finalidade se sobrepõem
   */
  @Transactional(readOnly = true)
  public List<LegalDocumentVersionEntity> resolveCurrentDocuments(Instant effectiveAt) {
    Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
    List<LegalDocumentVersionEntity> current = documentRepository.findEffectiveAt(effectiveAt);
    Map<LegalDocumentTypeEnum, LegalDocumentVersionEntity> byType =
        new EnumMap<>(LegalDocumentTypeEnum.class);
    for (LegalDocumentVersionEntity document : current) {
      LegalDocumentVersionEntity previous = byType.putIfAbsent(
          document.getDocumentType(),
          document);
      if (previous != null) {
        throw new IllegalStateException(
            "Multiple legal document versions are effective for type "
                + document.getDocumentType());
      }
    }
    for (LegalDocumentTypeEnum requiredType : BASELINE_REQUIRED_TYPES) {
      LegalDocumentVersionEntity document = byType.get(requiredType);
      if (document == null || !document.isRequired()) {
        throw new IllegalStateException(
            "Required legal document is not effective: " + requiredType);
      }
    }
    return List.copyOf(current);
  }

  /**
   * Valida previamente as referências aceitas e produz decisões prontas para a transação.
   *
   * @param acceptedDocumentIds versões que a interface marcou como aceitas
   * @param effectiveAt instante UTC de referência
   * @return decisões explícitas sobre versões vigentes
   */
  @Transactional(readOnly = true)
  public Map<Long, LegalConsentDecisionEnum> validateCurrentAcceptances(
      List<Long> acceptedDocumentIds,
      Instant effectiveAt) {
    Objects.requireNonNull(acceptedDocumentIds, "acceptedDocumentIds must not be null");
    List<LegalDocumentVersionEntity> currentDocuments = resolveCurrentDocuments(effectiveAt);
    Map<Long, LegalDocumentVersionEntity> currentById = mapByPersistentId(currentDocuments);
    Map<Long, LegalConsentDecisionEnum> decisions = acceptedDocumentIds.stream()
        .collect(Collectors.toMap(
            id -> Objects.requireNonNull(id, "acceptedDocumentId must not be null"),
            ignored -> LegalConsentDecisionEnum.ACCEPTED,
            (first, ignored) -> first));
    validateDecisions(currentDocuments, currentById, decisions);
    return Map.copyOf(decisions);
  }

  /**
   * Valida as versões publicadas que foram apresentadas pelo formulário de cadastro.
   *
   * <p>Uma versão já retirada pode originar uma identidade pendente quando era a fotografia
   * apresentada antes da submissão. Versões desconhecidas ou ainda não vigentes são rejeitadas.
   * A ativação continua responsável por exigir todas as versões obrigatórias então vigentes.
   *
   * @param acceptedDocumentIds versões que a interface marcou como aceitas
   * @param decidedAt instante UTC da decisão
   * @return decisões explícitas sobre as versões apresentadas
   */
  @Transactional(readOnly = true)
  public Map<Long, LegalConsentDecisionEnum> validatePublishedAcceptances(
      List<Long> acceptedDocumentIds,
      Instant decidedAt) {
    Objects.requireNonNull(acceptedDocumentIds, "acceptedDocumentIds must not be null");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    Map<Long, LegalConsentDecisionEnum> decisions = acceptedDocumentIds.stream()
        .collect(Collectors.toMap(
            id -> Objects.requireNonNull(id, "acceptedDocumentId must not be null"),
            ignored -> LegalConsentDecisionEnum.ACCEPTED,
            (first, ignored) -> first));
    List<LegalDocumentVersionEntity> documents =
        resolvePublishedDocuments(decisions.keySet(), decidedAt);
    validatePublishedDecisions(documents, decisions);
    return Map.copyOf(decisions);
  }

  /**
   * Registra decisões sobre as versões vigentes sem sobrescrever evidências anteriores.
   *
   * @param user identidade persistida
   * @param registration cadastro de origem, quando aplicável
   * @param decisions decisões explícitas indexadas pela versão
   * @param decidedAt instante UTC da decisão
   * @return quantidade de novas evidências e repetições idempotentes
   */
  @Transactional
  public LegalConsentRecordingVO recordCurrentDecisions(
      UserEntity user,
      RegistrationEntity registration,
      Map<Long, LegalConsentDecisionEnum> decisions,
      Instant decidedAt) {
    Objects.requireNonNull(decisions, "decisions must not be null");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");

    List<LegalDocumentVersionEntity> currentDocuments = resolveCurrentDocuments(decidedAt);
    Map<Long, LegalDocumentVersionEntity> currentById = mapByPersistentId(currentDocuments);
    validateDecisions(currentDocuments, currentById, decisions);
    return recordValidatedDecisions(
        user,
        registration,
        decisions,
        decidedAt,
        currentById);
  }

  /**
   * Registra as decisões iniciais sobre versões publicadas apresentadas no cadastro.
   *
   * <p>Esta operação aceita versões retiradas depois da abertura do formulário, mas nunca versões
   * desconhecidas ou futuras. A ativação revalida separadamente as versões obrigatórias vigentes.
   *
   * @param user identidade persistida
   * @param registration cadastro de origem
   * @param decisions decisões explícitas indexadas pela versão apresentada
   * @param decidedAt instante UTC da decisão
   * @return quantidade de novas evidências e repetições idempotentes
   */
  @Transactional
  public LegalConsentRecordingVO recordPublishedDecisions(
      UserEntity user,
      RegistrationEntity registration,
      Map<Long, LegalConsentDecisionEnum> decisions,
      Instant decidedAt) {
    Objects.requireNonNull(decisions, "decisions must not be null");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    List<LegalDocumentVersionEntity> documents =
        resolvePublishedDocuments(decisions.keySet(), decidedAt);
    validatePublishedDecisions(documents, decisions);
    return recordValidatedDecisions(
        user,
        registration,
        decisions,
        decidedAt,
        mapByPersistentId(documents));
  }

  private LegalConsentRecordingVO recordValidatedDecisions(
      UserEntity user,
      RegistrationEntity registration,
      Map<Long, LegalConsentDecisionEnum> decisions,
      Instant decidedAt,
      Map<Long, LegalDocumentVersionEntity> documentsById) {
    Long userId = requirePositiveId(
        Objects.requireNonNull(user, "user must not be null").getId(),
        "userId");
    validateRegistrationOwner(userId, registration);
    List<LegalConsentEntity> existing = consentRepository
        .findByUserIdAndLegalDocumentVersionIdIn(userId, decisions.keySet());
    Map<Long, LegalConsentEntity> existingByVersion = new HashMap<>();
    existing.forEach(consent -> existingByVersion.put(
        consent.getLegalDocumentVersion().getId(),
        consent));

    List<LegalConsentEntity> newConsents = new ArrayList<>();
    int alreadyExisting = 0;
    for (Map.Entry<Long, LegalConsentDecisionEnum> entry : decisions.entrySet()) {
      LegalConsentEntity persisted = existingByVersion.get(entry.getKey());
      if (persisted != null) {
        if (persisted.getDecision() != entry.getValue()) {
          throw new IllegalStateException(
              "A legal decision cannot be changed for the same document version");
        }
        alreadyExisting++;
      } else {
        newConsents.add(new LegalConsentEntity(
            user,
            registration,
            documentsById.get(entry.getKey()),
            entry.getValue(),
            decidedAt));
      }
    }
    consentRepository.saveAll(newConsents);
    return new LegalConsentRecordingVO(newConsents.size(), alreadyExisting);
  }

  private List<LegalDocumentVersionEntity> resolvePublishedDocuments(
      Collection<Long> documentIds,
      Instant decidedAt) {
    List<Long> ids = documentIds.stream()
        .map(id -> requirePositiveId(id, "legalDocumentVersionId"))
        .distinct()
        .toList();
    List<LegalDocumentVersionEntity> documents = documentRepository.findAllById(ids);
    if (!mapByPersistentId(documents).keySet().equals(Set.copyOf(ids))) {
      throw new IllegalArgumentException("Decision references an unknown legal document");
    }
    for (LegalDocumentVersionEntity document : documents) {
      if (document.getEffectiveAt().isAfter(decidedAt)) {
        throw new IllegalArgumentException(
            "Decision references a legal document that is not published yet");
      }
    }
    return List.copyOf(documents);
  }

  private static void validatePublishedDecisions(
      List<LegalDocumentVersionEntity> documents,
      Map<Long, LegalConsentDecisionEnum> decisions) {
    Map<LegalDocumentTypeEnum, LegalDocumentVersionEntity> byType =
        new EnumMap<>(LegalDocumentTypeEnum.class);
    for (LegalDocumentVersionEntity document : documents) {
      Long documentId = requirePositiveId(document.getId(), "legalDocumentVersionId");
      Objects.requireNonNull(decisions.get(documentId), "decision must not be null");
      LegalDocumentVersionEntity previous = byType.putIfAbsent(
          document.getDocumentType(),
          document);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Only one presented version per legal document type is accepted");
      }
    }
    for (LegalDocumentTypeEnum requiredType : BASELINE_REQUIRED_TYPES) {
      LegalDocumentVersionEntity document = byType.get(requiredType);
      if (document == null || !document.isRequired()
          || decisions.get(document.getId()) != LegalConsentDecisionEnum.ACCEPTED) {
        throw new IllegalArgumentException(
            "Every presented baseline legal document must be explicitly accepted");
      }
    }
  }

  /**
   * Detecta versões obrigatórias vigentes que ainda não foram aceitas.
   *
   * @param userId identificador persistido da identidade
   * @param effectiveAt instante UTC da ativação
   * @return versões atuais e subconjunto que exige novo aceite
   */
  @Transactional(readOnly = true)
  public LegalRequirementStatusVO evaluateRequiredConsents(
      Long userId,
      Instant effectiveAt) {
    requirePositiveId(userId, "userId");
    List<LegalDocumentVersionEntity> requiredDocuments = resolveCurrentDocuments(effectiveAt)
        .stream()
        .filter(LegalDocumentVersionEntity::isRequired)
        .toList();
    List<Long> requiredIds = requiredDocuments.stream()
        .map(LegalDocumentVersionEntity::getId)
        .toList();
    Set<Long> acceptedIds = consentRepository
        .findByUserIdAndLegalDocumentVersionIdIn(userId, requiredIds)
        .stream()
        .filter(consent -> consent.getDecision() == LegalConsentDecisionEnum.ACCEPTED)
        .map(consent -> consent.getLegalDocumentVersion().getId())
        .collect(Collectors.toUnmodifiableSet());
    List<Long> missingIds = requiredIds.stream()
        .filter(id -> !acceptedIds.contains(id))
        .toList();
    return new LegalRequirementStatusVO(requiredIds, missingIds);
  }

  /**
   * Indexa versões persistidas para validar decisões recebidas.
   *
   * @param documents versões vigentes
   * @return mapa por identificador interno
   */
  private static Map<Long, LegalDocumentVersionEntity> mapByPersistentId(
      List<LegalDocumentVersionEntity> documents) {
    Map<Long, LegalDocumentVersionEntity> byId = new HashMap<>();
    for (LegalDocumentVersionEntity document : documents) {
      Long id = requirePositiveId(document.getId(), "legalDocumentVersionId");
      byId.put(id, document);
    }
    return byId;
  }

  /**
   * Exige decisões somente para versões atuais e aceite de todas as obrigatórias.
   *
   * @param currentDocuments versões vigentes
   * @param currentById índice das versões vigentes
   * @param decisions decisões explícitas
   */
  private static void validateDecisions(
      List<LegalDocumentVersionEntity> currentDocuments,
      Map<Long, LegalDocumentVersionEntity> currentById,
      Map<Long, LegalConsentDecisionEnum> decisions) {
    for (Map.Entry<Long, LegalConsentDecisionEnum> entry : decisions.entrySet()) {
      if (!currentById.containsKey(entry.getKey())) {
        throw new IllegalArgumentException("Decision references a non-current legal document");
      }
      Objects.requireNonNull(entry.getValue(), "decision must not be null");
    }
    for (LegalDocumentVersionEntity document : currentDocuments) {
      if (document.isRequired()
          && decisions.get(document.getId()) != LegalConsentDecisionEnum.ACCEPTED) {
        throw new IllegalArgumentException(
            "Every required legal document must be explicitly accepted");
      }
    }
  }

  /**
   * Impede associar a evidência ao cadastro de outra identidade.
   *
   * @param userId usuário responsável
   * @param registration cadastro de origem opcional
   */
  private static void validateRegistrationOwner(Long userId, RegistrationEntity registration) {
    if (registration == null) {
      return;
    }
    Long registrationId = requirePositiveId(registration.getId(), "registrationId");
    Long registrationUserId = requirePositiveId(registration.getUser().getId(), "registrationUserId");
    if (!userId.equals(registrationUserId)) {
      throw new IllegalArgumentException(
          "Registration does not belong to the consent user: " + registrationId);
    }
  }

  /**
   * Valida identificadores antes de consultas persistentes.
   *
   * @param id identificador a validar
   * @param fieldName nome seguro usado no diagnóstico
   * @return identificador positivo
   */
  private static Long requirePositiveId(Long id, String fieldName) {
    Objects.requireNonNull(id, fieldName + " must not be null");
    if (id <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return id;
  }
}
