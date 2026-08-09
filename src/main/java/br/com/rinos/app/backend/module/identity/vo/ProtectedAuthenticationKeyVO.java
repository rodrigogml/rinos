package br.com.rinos.app.backend.module.identity.vo;

import java.util.Arrays;
import java.util.Objects;

/**
 * Chave de janela protegida por MAC e identificada pela versão criptográfica.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ProtectedAuthenticationKeyVO(byte[] digest, String keyVersion) {

  public ProtectedAuthenticationKeyVO {
    if (digest == null || digest.length != 32) {
      throw new IllegalArgumentException("digest must contain 32 bytes");
    }
    digest = Arrays.copyOf(digest, digest.length);
    Objects.requireNonNull(keyVersion, "keyVersion must not be null");
  }

  @Override
  public byte[] digest() {
    return Arrays.copyOf(digest, digest.length);
  }

  /** Oculta o material correlacionável em diagnósticos. */
  @Override
  public String toString() {
    return "ProtectedAuthenticationKeyVO[digest=REDACTED, keyVersion=" + keyVersion + "]";
  }
}
