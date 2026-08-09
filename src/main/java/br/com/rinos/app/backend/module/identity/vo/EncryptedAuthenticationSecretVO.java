package br.com.rinos.app.backend.module.identity.vo;

import java.util.Arrays;

/**
 * Envelope AEAD persistível sem expor segredo ou chave da instalação.
 *
 * @param ciphertext texto cifrado junto da tag GCM
 * @param nonce nonce aleatório de 96 bits
 * @param keyVersion versão necessária para leitura e rotação
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EncryptedAuthenticationSecretVO(
    byte[] ciphertext,
    byte[] nonce,
    String keyVersion) {

  /** Preserva cópias defensivas e os limites físicos do fator TOTP. */
  public EncryptedAuthenticationSecretVO {
    if (ciphertext == null || ciphertext.length < 17 || ciphertext.length > 512) {
      throw new IllegalArgumentException("ciphertext length is invalid");
    }
    if (nonce == null || nonce.length != 12) {
      throw new IllegalArgumentException("nonce must contain 12 bytes");
    }
    if (keyVersion == null || !keyVersion.matches("[A-Za-z0-9_-]{1,32}")) {
      throw new IllegalArgumentException("keyVersion is invalid");
    }
    ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
    nonce = Arrays.copyOf(nonce, nonce.length);
  }

  @Override
  public byte[] ciphertext() {
    return Arrays.copyOf(ciphertext, ciphertext.length);
  }

  @Override
  public byte[] nonce() {
    return Arrays.copyOf(nonce, nonce.length);
  }

  /** Oculta todo o material criptográfico em diagnósticos. */
  @Override
  public String toString() {
    return "EncryptedAuthenticationSecretVO[ciphertext=REDACTED, nonce=REDACTED, "
        + "keyVersion=" + keyVersion + "]";
  }
}
