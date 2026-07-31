package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.VerificationEntity;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.VerificationRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;
import br.com.rinos.app.config.VerificationPropertiesConfig;
import jakarta.persistence.EntityNotFoundException;

/**
 * Emite, invalida e consome comprovações de uso único do cadastro.
 *
 * <p>O serviço persiste somente hashes, bloqueia as linhas antes de transições e nunca inclui o
 * token apresentado em erros ou resultados de consumo.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class VerificationService {

  private final VerificationRepository repository;
  private final RegistrationRepository registrationRepository;
  private final VerificationTokenService tokenService;
  private final Duration validity;

  /**
   * Cria o serviço com a validade tipada da comprovação.
   *
   * @param repository persistência bloqueável das provas
   * @param registrationRepository persistência usada para serializar emissões
   * @param tokenService geração e comparação criptográfica
   * @param properties configuração exclusiva da validade
   */
  public VerificationService(
      VerificationRepository repository,
      RegistrationRepository registrationRepository,
      VerificationTokenService tokenService,
      VerificationPropertiesConfig properties) {
    this.repository = repository;
    this.registrationRepository = registrationRepository;
    this.tokenService = tokenService;
    validity = properties.validity();
  }

  /**
   * Invalida provas abertas do mesmo propósito e emite uma substituta.
   *
   * @param registration cadastro persistido proprietário
   * @param purpose finalidade da comprovação
   * @param issuedAt instante UTC da emissão
   * @return transporte efêmero contendo o token somente para envio pós-commit
   * @throws NullPointerException quando qualquer argumento é nulo
   * @throws IllegalArgumentException quando o cadastro ainda não está persistido
   * @throws EntityNotFoundException quando o cadastro deixou de existir
   */
  @Transactional
  public IssuedVerificationVO issue(
      RegistrationEntity registration,
      VerificationPurposeEnum purpose,
      Instant issuedAt) {
    Objects.requireNonNull(registration, "registration must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Long registrationId = requirePersistentRegistration(registration);
    RegistrationEntity lockedRegistration = registrationRepository.findByIdForUpdate(registrationId)
        .orElseThrow(() -> new EntityNotFoundException(
            "Registration not found for verification operation"));

    invalidate(
        repository.findByRegistrationIdAndPurposeAndStatusForUpdate(
            registrationId,
            purpose,
            VerificationStatusEnum.OPEN),
        issuedAt);

    String token = tokenService.generate();
    VerificationEntity verification = new VerificationEntity(
        lockedRegistration,
        purpose,
        tokenService.hash(token),
        issuedAt,
        issuedAt.plus(validity));
    VerificationEntity persisted = repository.saveAndFlush(verification);
    return new IssuedVerificationVO(persisted.getId(), token, persisted.getExpiresAt());
  }

  /**
   * Consome uma comprovação exatamente uma vez no cadastro e propósito corretos.
   *
   * @param registrationId identificador persistido do cadastro esperado
   * @param purpose finalidade esperada
   * @param token token bruto apresentado
   * @param occurredAt instante UTC da tentativa
   * @return resultado seguro sem token, hash ou dados pessoais
   */
  @Transactional
  public VerificationConsumptionStatusEnum consume(
      Long registrationId,
      VerificationPurposeEnum purpose,
      String token,
      Instant occurredAt) {
    validateRegistrationId(registrationId);
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (token == null || token.isBlank()) {
      return VerificationConsumptionStatusEnum.REJECTED;
    }

    byte[] tokenHash = tokenService.hash(token);
    return repository.findByTokenHashForUpdate(tokenHash)
        .map(verification -> consumeLocked(
            verification,
            registrationId,
            purpose,
            token,
            occurredAt))
        .orElse(VerificationConsumptionStatusEnum.REJECTED);
  }

  /**
   * Inspeciona uma prova sem consumi-la, preservando-a para eventual aceite legal complementar.
   *
   * <p>O método localiza primeiro a relação, bloqueia o cadastro e somente então bloqueia a prova,
   * mantendo a mesma ordem usada por emissão e invalidação.
   *
   * @param purpose finalidade esperada
   * @param token token bruto apresentado
   * @param occurredAt instante UTC da tentativa
   * @return estado seguro e cadastro interno quando a chave foi localizada
   */
  @Transactional
  public VerificationInspectionVO inspect(
      VerificationPurposeEnum purpose,
      String token,
      Instant occurredAt) {
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (token == null || token.isBlank()) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.REJECTED,
          null,
          null);
    }

    byte[] tokenHash = tokenService.hash(token);
    Long registrationId = repository.findRegistrationIdByTokenHash(tokenHash).orElse(null);
    if (registrationId == null) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.REJECTED,
          null,
          null);
    }
    RegistrationEntity registration = registrationRepository
        .findByIdForUpdate(registrationId)
        .orElse(null);
    if (registration == null) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.REJECTED,
          null,
          null);
    }
    VerificationEntity verification = repository.findByTokenHashForUpdate(tokenHash)
        .orElse(null);
    if (verification == null
        || verification.getPurpose() != purpose
        || !tokenService.matches(token, verification.getTokenHash())) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.REJECTED,
          null,
          null);
    }
    if (verification.getStatus() == VerificationStatusEnum.USED) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.ALREADY_USED,
          registration,
          verification.getExpiresAt());
    }
    if (verification.getStatus() != VerificationStatusEnum.OPEN) {
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.REJECTED,
          registration,
          verification.getExpiresAt());
    }
    if (!occurredAt.isBefore(verification.getExpiresAt())) {
      verification.setStatus(VerificationStatusEnum.EXPIRED);
      return new VerificationInspectionVO(
          VerificationConsumptionStatusEnum.EXPIRED,
          registration,
          verification.getExpiresAt());
    }
    return new VerificationInspectionVO(
        VerificationConsumptionStatusEnum.VERIFIED,
        registration,
        verification.getExpiresAt());
  }

  /**
   * Invalida todas as comprovações abertas de um cadastro.
   *
   * @param registrationId identificador persistido do cadastro
   * @param invalidatedAt instante UTC da invalidação
   * @return quantidade de provas alteradas
   * @throws EntityNotFoundException quando o cadastro deixou de existir
   */
  @Transactional
  public int invalidateAllOpen(Long registrationId, Instant invalidatedAt) {
    validateRegistrationId(registrationId);
    Objects.requireNonNull(invalidatedAt, "invalidatedAt must not be null");
    registrationRepository.findByIdForUpdate(registrationId)
        .orElseThrow(() -> new EntityNotFoundException(
            "Registration not found for verification operation"));
    List<VerificationEntity> openVerifications =
        repository.findByRegistrationIdAndStatusForUpdate(
            registrationId,
            VerificationStatusEnum.OPEN);
    invalidate(openVerifications, invalidatedAt);
    return openVerifications.size();
  }

  /**
   * Valida e altera uma comprovação já bloqueada pela transação corrente.
   *
   * @param verification comprovação bloqueada
   * @param registrationId cadastro esperado
   * @param purpose finalidade esperada
   * @param token prova apresentada
   * @param occurredAt instante UTC da tentativa
   * @return resultado seguro do consumo
   */
  private VerificationConsumptionStatusEnum consumeLocked(
      VerificationEntity verification,
      Long registrationId,
      VerificationPurposeEnum purpose,
      String token,
      Instant occurredAt) {
    if (!registrationId.equals(verification.getRegistration().getId())
        || purpose != verification.getPurpose()
        || verification.getStatus() != VerificationStatusEnum.OPEN
        || !tokenService.matches(token, verification.getTokenHash())) {
      return VerificationConsumptionStatusEnum.REJECTED;
    }
    if (!occurredAt.isBefore(verification.getExpiresAt())) {
      verification.setStatus(VerificationStatusEnum.EXPIRED);
      return VerificationConsumptionStatusEnum.EXPIRED;
    }
    verification.setStatus(VerificationStatusEnum.USED);
    verification.setUsedAt(occurredAt);
    return VerificationConsumptionStatusEnum.VERIFIED;
  }

  /**
   * Marca uma coleção já bloqueada como invalidada.
   *
   * @param verifications provas abertas
   * @param invalidatedAt instante UTC comum da invalidação
   */
  private void invalidate(List<VerificationEntity> verifications, Instant invalidatedAt) {
    verifications.forEach(verification -> {
      verification.setStatus(VerificationStatusEnum.INVALIDATED);
      verification.setInvalidatedAt(invalidatedAt);
    });
    repository.saveAll(verifications);
  }

  /**
   * Obtém a identidade persistente necessária às operações bloqueáveis.
   *
   * @param registration cadastro informado
   * @return identificador positivo
   */
  private static Long requirePersistentRegistration(RegistrationEntity registration) {
    Long registrationId = registration.getId();
    validateRegistrationId(registrationId);
    return registrationId;
  }

  /**
   * Protege consultas persistentes contra identificadores ausentes ou transitórios.
   *
   * @param registrationId identificador a validar
   */
  private static void validateRegistrationId(Long registrationId) {
    Objects.requireNonNull(registrationId, "registrationId must not be null");
    if (registrationId <= 0) {
      throw new IllegalArgumentException("registrationId must be positive");
    }
  }
}
