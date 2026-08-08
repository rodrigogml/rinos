package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeEntity;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.RecoveryCodeSetSummaryVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Substitui e consome conjuntos de hashes de recuperação sob lock da identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class RecoveryCodeService {
  private final UserRepository users; private final RecoveryCodeSetRepository sets;
  private final RecoveryCodeRepository codes; private final IdentityReferenceService references;
  public RecoveryCodeService(UserRepository users, RecoveryCodeSetRepository sets,
      RecoveryCodeRepository codes, IdentityReferenceService references) {
    this.users = users; this.sets = sets; this.codes = codes; this.references = references;
  }
  @Transactional
  public RecoveryCodeSetSummaryVO replace(Long userId, List<String> codeHashes, Instant occurredAt) {
    UserEntity user = lockUser(userId);
    if (codeHashes == null || codeHashes.size() != 10
        || codeHashes.stream().anyMatch(h -> h == null || h.isBlank())
        || new java.util.HashSet<>(codeHashes).size() != 10)
      throw new IllegalArgumentException("exactly 10 non-blank code hashes are required");
    sets.findByUserIdAndStatusForUpdate(userId, RecoveryCodeSetStatusEnum.ACTIVE).ifPresent(current -> {
      List<RecoveryCodeEntity> previous = codes.findByCodeSetIdForUpdate(current.getId());
      previous.forEach(RecoveryCodeEntity::invalidate); codes.saveAll(previous);
      current.invalidate(occurredAt); sets.saveAndFlush(current);
    });
    RecoveryCodeSetEntity codeSet = sets.saveAndFlush(new RecoveryCodeSetEntity(user, references.generate(), occurredAt));
    for (int index = 0; index < codeHashes.size(); index++) codes.save(new RecoveryCodeEntity(codeSet, codeHashes.get(index), index + 1));
    codes.flush();
    return summary(codeSet, 10);
  }
  @Transactional
  public FactorOperationStatusEnum consume(Long userId, Predicate<String> hashMatcher, Instant occurredAt) {
    lockUser(userId); Objects.requireNonNull(hashMatcher, "hashMatcher must not be null");
    RecoveryCodeSetEntity set = sets.findByUserIdAndStatusForUpdate(userId, RecoveryCodeSetStatusEnum.ACTIVE).orElse(null);
    if (set == null) return FactorOperationStatusEnum.REJECTED;
    List<RecoveryCodeEntity> current = codes.findByCodeSetIdForUpdate(set.getId());
    RecoveryCodeEntity matched = current.stream().filter(c -> c.getStatus() == RecoveryCodeStatusEnum.AVAILABLE)
        .filter(c -> hashMatcher.test(c.getCodeHash())).findFirst().orElse(null);
    if (matched == null) return FactorOperationStatusEnum.REJECTED;
    matched.use(occurredAt);
    if (current.stream().noneMatch(c -> c.getStatus() == RecoveryCodeStatusEnum.AVAILABLE)) {
      set.exhaust(occurredAt); return FactorOperationStatusEnum.EXHAUSTED;
    }
    return FactorOperationStatusEnum.USED;
  }
  @Transactional(readOnly = true)
  public java.util.Optional<RecoveryCodeSetSummaryVO> inspect(Long userId) {
    return sets.findByUserIdAndStatus(userId, RecoveryCodeSetStatusEnum.ACTIVE)
        .map(set -> summary(set, (int) codes.countByCodeSetIdAndStatus(set.getId(), RecoveryCodeStatusEnum.AVAILABLE)));
  }
  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
    return users.findByIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
  }
  private static RecoveryCodeSetSummaryVO summary(RecoveryCodeSetEntity set, int available) {
    return new RecoveryCodeSetSummaryVO(set.getReference(), set.getStatus(), available, set.getIssuedAt());
  }
}
