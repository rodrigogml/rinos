package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Apresentação transitória do conjunto de recuperação recém-persistido.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record IssuedRecoveryCodeSetVO(UUID reference, Instant issuedAt, List<String> codes) {

  /** Garante uma apresentação completa e defensivamente copiada. */
  public IssuedRecoveryCodeSetVO {
    if (reference == null || issuedAt == null || codes == null || codes.size() != 10
        || codes.stream().anyMatch(code -> code == null || code.isBlank())) {
      throw new IllegalArgumentException("a complete set of 10 recovery codes is required");
    }
    codes = List.copyOf(codes);
  }

  @Override
  public String toString() {
    return "IssuedRecoveryCodeSetVO[reference=" + reference
        + ", issuedAt=" + issuedAt + ", codes=REDACTED]";
  }
}
