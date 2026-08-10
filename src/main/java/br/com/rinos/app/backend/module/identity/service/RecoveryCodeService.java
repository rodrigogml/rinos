package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeEntity;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedRecoveryCodeSetVO;
import br.com.rinos.app.backend.module.identity.vo.RecoveryCodeSetSummaryVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWRecoveryCodeService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWRecoveryCodesVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Gera, substitui e consome códigos de recuperação sob lock da identidade.
 *
 * <p>Os valores legíveis existem somente no retorno transitório da geração. A persistência recebe
 * hashes Argon2id independentes produzidos pela RFW e serializa geração, substituição e consumo pela
 * ordem de lock usuário → conjunto → códigos.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class RecoveryCodeService {

  private static final int REQUIRED_CODE_COUNT = 10;

  private final UserRepository users;
  private final RecoveryCodeSetRepository sets;
  private final RecoveryCodeRepository codes;
  private final IdentityReferenceService references;
  private final IdentityAuditService audit;
  private final RFWRecoveryCodeService protocol;

  /** Cria a autoridade transacional dos códigos de recuperação. */
  public RecoveryCodeService(UserRepository users, RecoveryCodeSetRepository sets,
      RecoveryCodeRepository codes, IdentityReferenceService references,
      IdentityAuditService audit, RFWRecoveryCodeService protocol) {
    this.users = users;
    this.sets = sets;
    this.codes = codes;
    this.references = references;
    this.audit = audit;
    this.protocol = protocol;
  }

  /**
   * Gera dez códigos e substitui atomicamente qualquer conjunto ativo anterior.
   *
   * @param userId identidade ativa proprietária
   * @param correlationId correlação segura do evento de alteração de MFA
   * @param occurredAt instante UTC da geração
   * @return apresentação única do novo conjunto
   * @throws EntityNotFoundException quando a identidade não existe
   * @throws SecurityException quando a identidade não está ativa
   */
  @Transactional
  public IssuedRecoveryCodeSetVO generate(Long userId, UUID correlationId, Instant occurredAt) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UserEntity user = lockActiveUser(userId);
    RFWRecoveryCodesVO generated = protocol.generate();
    List<String> rawCodes = validateRawCodes(generated.codes());
    List<String> protectedCodes = validateProtectedCodes(protocol.encode(rawCodes));

    invalidateCurrentSet(userId, occurredAt);
    RecoveryCodeSetEntity codeSet = sets.saveAndFlush(
        new RecoveryCodeSetEntity(user, references.generate(), occurredAt));
    for (int index = 0; index < protectedCodes.size(); index++) {
      codes.save(new RecoveryCodeEntity(codeSet, protectedCodes.get(index), index + 1));
    }
    codes.flush();
    audit.record(user, null, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED,
        null, null, IdentityTransitionOriginEnum.SELF_SERVICE, "RECOVERY_CODES", occurredAt);
    return new IssuedRecoveryCodeSetVO(codeSet.getReference(), occurredAt, rawCodes);
  }

  /**
   * Consome no máximo um código ainda disponível do conjunto ativo.
   *
   * @param userId identidade proprietária
   * @param rawCode código apresentado pelo usuário; valores nulos ou vazios são rejeitados
   * @param occurredAt instante UTC da tentativa
   * @return {@code USED}, {@code EXHAUSTED} ou {@code REJECTED}
   * @throws EntityNotFoundException quando a identidade não existe
   * @throws SecurityException quando a identidade não está ativa
   */
  @Transactional
  public FactorOperationStatusEnum consume(Long userId, String rawCode, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    lockActiveUser(userId);
    if (rawCode == null || rawCode.isBlank()) {
      return FactorOperationStatusEnum.REJECTED;
    }
    RecoveryCodeSetEntity set = sets.findByUserIdAndStatusForUpdate(
        userId, RecoveryCodeSetStatusEnum.ACTIVE).orElse(null);
    if (set == null) {
      return FactorOperationStatusEnum.REJECTED;
    }
    List<RecoveryCodeEntity> current = codes.findByCodeSetIdForUpdate(set.getId());
    List<RecoveryCodeEntity> available = current.stream()
        .filter(code -> code.getStatus() == RecoveryCodeStatusEnum.AVAILABLE)
        .toList();
    int matchingIndex = protocol.findMatchingIndex(
        rawCode, available.stream().map(RecoveryCodeEntity::getCodeHash).toList());
    if (matchingIndex < 0) {
      return FactorOperationStatusEnum.REJECTED;
    }
    available.get(matchingIndex).use(occurredAt);
    if (available.size() == 1) {
      set.exhaust(occurredAt);
      return FactorOperationStatusEnum.EXHAUSTED;
    }
    return FactorOperationStatusEnum.USED;
  }

  /**
   * Retorna apenas referência, estado e quantidade restante do conjunto ativo.
   *
   * @param userId identidade proprietária
   * @return resumo seguro, vazio quando não existe conjunto ativo
   */
  @Transactional(readOnly = true)
  public java.util.Optional<RecoveryCodeSetSummaryVO> inspect(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return sets.findByUserIdAndStatus(userId, RecoveryCodeSetStatusEnum.ACTIVE)
        .map(set -> summary(set, (int) codes.countByCodeSetIdAndStatus(
            set.getId(), RecoveryCodeStatusEnum.AVAILABLE)));
  }

  private void invalidateCurrentSet(Long userId, Instant occurredAt) {
    sets.findByUserIdAndStatusForUpdate(userId, RecoveryCodeSetStatusEnum.ACTIVE)
        .ifPresent(current -> {
          List<RecoveryCodeEntity> previous = codes.findByCodeSetIdForUpdate(current.getId());
          previous.forEach(RecoveryCodeEntity::invalidate);
          codes.saveAll(previous);
          current.invalidate(occurredAt);
          sets.saveAndFlush(current);
        });
  }

  private UserEntity lockActiveUser(Long userId) {
    UserEntity user = lockUser(userId);
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      throw new SecurityException("Active user is required");
    }
    return user;
  }

  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return users.findByIdForUpdate(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
  }

  private static List<String> validateRawCodes(List<String> values) {
    if (values == null || values.size() != REQUIRED_CODE_COUNT
        || values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalStateException("RFW must generate exactly 10 non-blank recovery codes");
    }
    Set<String> normalized = new HashSet<>();
    for (String value : values) {
      normalized.add(value.trim().toUpperCase(Locale.ROOT));
    }
    if (normalized.size() != REQUIRED_CODE_COUNT) {
      throw new IllegalStateException("RFW must generate 10 distinct recovery codes");
    }
    return List.copyOf(values);
  }

  private static List<String> validateProtectedCodes(List<String> values) {
    if (values == null || values.size() != REQUIRED_CODE_COUNT
        || values.stream().anyMatch(value -> value == null || value.isBlank()
            || value.length() > 255)
        || new HashSet<>(values).size() != REQUIRED_CODE_COUNT) {
      throw new IllegalStateException("RFW must protect every recovery code independently");
    }
    return List.copyOf(values);
  }

  private static RecoveryCodeSetSummaryVO summary(RecoveryCodeSetEntity set, int available) {
    return new RecoveryCodeSetSummaryVO(
        set.getReference(), set.getStatus(), available, set.getIssuedAt());
  }
}
