package br.com.rinos.app.backend.module.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Produz e verifica o SHA-256 do conteúdo imutável de uma versão legal.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class LegalDocumentIntegrityService {

  private static final String HASH_ALGORITHM = "SHA-256";

  /**
   * Calcula a evidência de integridade do texto apresentado.
   *
   * @param content conteúdo integral obrigatório
   * @return SHA-256 com 32 bytes
   */
  public byte[] hash(String content) {
    Objects.requireNonNull(content, "content must not be null");
    try {
      return MessageDigest.getInstance(HASH_ALGORITHM)
          .digest(content.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  /**
   * Verifica o conteúdo contra a evidência persistida.
   *
   * @param content conteúdo integral
   * @param expectedHash hash esperado
   * @return {@code true} somente quando o conteúdo permanece íntegro
   */
  public boolean matches(String content, byte[] expectedHash) {
    Objects.requireNonNull(expectedHash, "expectedHash must not be null");
    return MessageDigest.isEqual(hash(content), expectedHash);
  }
}
