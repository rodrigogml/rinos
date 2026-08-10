package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.backend.module.identity.vo.ExternalIdentitySummaryVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Executa a gestão de identidades externas sob locks de usuário, sessão e vínculo.
 *
 * <p>A chave do provedor nunca é comparada por e-mail. Uma identidade revogada só pode ser
 * reativada pelo mesmo usuário que historicamente a possuía.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class ExternalIdentityManagementService {

  private final UserRepository users;
  private final ExternalIdentityRepository identities;
  private final AuthSessionService sessions;
  private final ReauthenticationService reauthentication;
  private final AuthenticationMethodInventoryService inventory;
  private final IdentityReferenceService references;
  private final IdentityAuditService audit;

  /**
   * Cria a autoridade transacional da gestão externa.
   *
   * @param users usuários globais
   * @param identities vínculos externos
   * @param sessions autoridade das sessões
   * @param reauthentication autoridade da garantia recente
   * @param inventory inventário de métodos utilizáveis
   * @param references gerador e codificador de referências opacas
   * @param audit auditoria sanitizada
   */
  public ExternalIdentityManagementService(
      UserRepository users,
      ExternalIdentityRepository identities,
      AuthSessionService sessions,
      ReauthenticationService reauthentication,
      AuthenticationMethodInventoryService inventory,
      IdentityReferenceService references,
      IdentityAuditService audit) {
    this.users = Objects.requireNonNull(users, "users must not be null");
    this.identities = Objects.requireNonNull(identities, "identities must not be null");
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    this.reauthentication = Objects.requireNonNull(
        reauthentication, "reauthentication must not be null");
    this.inventory = Objects.requireNonNull(inventory, "inventory must not be null");
    this.references = Objects.requireNonNull(references, "references must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
  }

  /**
   * Lista somente vínculos ativos depois de validar a sessão corrente.
   *
   * @param userId usuário autenticado
   * @param currentSessionReference sessão corrente opaca
   * @param occurredAt instante UTC da consulta
   * @return vínculos seguros e ordenados
   */
  @Transactional
  public List<ExternalIdentitySummaryVO> list(
      Long userId,
      UUID currentSessionReference,
      Instant occurredAt) {
    sessions.listManaged(userId, currentSessionReference, occurredAt);
    return identities.findByUserIdAndStatusOrderById(
        userId, ExternalIdentityStatusEnum.ACTIVE).stream()
        .map(ExternalIdentityManagementService::summary)
        .toList();
  }

  /**
   * Vincula uma identidade validada somente após confirmação explícita e garantia recente.
   *
   * @param userId usuário autenticado
   * @param currentSessionReference sessão corrente
   * @param provider provedor validado
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @param explicitlyConfirmed confirmação humana recebida pelo protocolo RFW
   * @param correlationId correlação de auditoria
   * @param occurredAt instante da validação e ativação
   * @return resultado interno da operação
   */
  @Transactional
  public ExternalIdentityOperationStatusEnum link(
      Long userId,
      UUID currentSessionReference,
      ExternalIdentityProviderEnum provider,
      String issuer,
      String subject,
      boolean explicitlyConfirmed,
      UUID correlationId,
      Instant occurredAt) {
    validateExternalKey(issuer, "issuer");
    validateExternalKey(subject, "subject");
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (!explicitlyConfirmed) {
      return ExternalIdentityOperationStatusEnum.REJECTED;
    }
    if (!reauthentication.isRecentlyAuthorized(
        userId,
        currentSessionReference,
        ReauthenticationOperationEnum.LINK_EXTERNAL_IDENTITY,
        occurredAt)) {
      return ExternalIdentityOperationStatusEnum.ACCESS_DENIED;
    }
    UserEntity user = lockActiveUser(userId);
    ExternalIdentityEntity existing = identities.findByIssuerAndSubjectForUpdate(
        issuer, subject).orElse(null);
    if (existing != null) {
      if (!user.getId().equals(existing.getUser().getId())) {
        return ExternalIdentityOperationStatusEnum.CONFLICT;
      }
      if (existing.getStatus() == ExternalIdentityStatusEnum.ACTIVE) {
        return ExternalIdentityOperationStatusEnum.ALREADY_LINKED;
      }
      if (existing.getStatus() != ExternalIdentityStatusEnum.REVOKED) {
        return ExternalIdentityOperationStatusEnum.CONFLICT;
      }
      existing.reactivate(occurredAt, occurredAt);
      record(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, occurredAt);
      return ExternalIdentityOperationStatusEnum.LINKED;
    }
    ExternalIdentityEntity created = new ExternalIdentityEntity(
        user, references.generate(), provider, issuer, subject, occurredAt);
    created.setStatus(ExternalIdentityStatusEnum.ACTIVE);
    created.setActivatedAt(occurredAt);
    identities.saveAndFlush(created);
    record(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, occurredAt);
    return ExternalIdentityOperationStatusEnum.LINKED;
  }

  /**
   * Revoga um vínculo próprio preservando ao menos um método inicial utilizável.
   *
   * @param userId usuário autenticado
   * @param currentSessionReference sessão corrente
   * @param identityReference referência opaca do vínculo
   * @param correlationId correlação de auditoria
   * @param occurredAt instante UTC da operação
   * @return resultado interno da operação
   */
  @Transactional
  public ExternalIdentityOperationStatusEnum unlink(
      Long userId,
      UUID currentSessionReference,
      UUID identityReference,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(identityReference, "identityReference must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (!reauthentication.isRecentlyAuthorized(
        userId,
        currentSessionReference,
        ReauthenticationOperationEnum.UNLINK_EXTERNAL_IDENTITY,
        occurredAt)) {
      return ExternalIdentityOperationStatusEnum.ACCESS_DENIED;
    }
    UserEntity user = lockActiveUser(userId);
    ExternalIdentityEntity identity = identities.findByUserIdAndReferenceForUpdate(
        userId, references.encode(identityReference)).orElse(null);
    if (identity == null || identity.getStatus() != ExternalIdentityStatusEnum.ACTIVE) {
      return ExternalIdentityOperationStatusEnum.STALE;
    }
    AuthenticationMethodInventoryVO current = inventory.inspect(userId);
    if (current.initialMethodCount() <= 1) {
      return ExternalIdentityOperationStatusEnum.LAST_METHOD;
    }
    identity.revoke(occurredAt);
    record(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED, occurredAt);
    return ExternalIdentityOperationStatusEnum.UNLINKED;
  }

  private UserEntity lockActiveUser(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    UserEntity user = users.findByIdForUpdate(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      throw new SecurityException("Active user is required");
    }
    return user;
  }

  private void record(
      UserEntity user,
      UUID correlationId,
      IdentityEventTypeEnum type,
      Instant occurredAt) {
    audit.record(
        user,
        null,
        correlationId,
        type,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "GOOGLE",
        occurredAt);
  }

  private static ExternalIdentitySummaryVO summary(ExternalIdentityEntity identity) {
    return new ExternalIdentitySummaryVO(
        identity.getReference(),
        identity.getProvider(),
        identity.getActivatedAt(),
        identity.getLastUsedAt());
  }

  private static void validateExternalKey(String value, String name) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
