package br.com.rinos.app.backend.module.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Gera tokens de 256 bits e produz suas evidências SHA-256 não recuperáveis.
 *
 * <p>Cada digest usa uma instância própria. A comparação emprega
 * {@link MessageDigest#isEqual(byte[], byte[])} para evitar comparação antecipadamente variável.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
// TODO Substituir esta implementação por RFWOpaqueTokenService ao migrar o Rinos para a RFW 2.0.
@Service
public class VerificationTokenService {

  private static final int TOKEN_BYTES = 32;
  private static final String HASH_ALGORITHM = "SHA-256";

  private final SecureRandom secureRandom;

  /**
   * Cria o gerador com a fonte criptográfica padrão da JVM.
   */
  public VerificationTokenService() {
    this(new SecureRandom());
  }

  /**
   * Permite fornecer uma fonte criptográfica controlada em testes do mesmo package.
   *
   * @param secureRandom fonte de entropia obrigatória
   */
  VerificationTokenService(SecureRandom secureRandom) {
    this.secureRandom =
        Objects.requireNonNull(secureRandom, "secureRandom must not be null");
  }

  /**
   * Gera um token URL-safe sem padding.
   *
   * @return token bruto com 256 bits de entropia
   */
  public String generate() {
    byte[] randomBytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * Produz a chave persistente de um token.
   *
   * @param token token bruto obrigatório
   * @return SHA-256 com 32 bytes
   */
  public byte[] hash(String token) {
    Objects.requireNonNull(token, "token must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      return digest.digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  /**
   * Compara o token apresentado à evidência persistida em tempo constante.
   *
   * @param token token bruto apresentado
   * @param expectedHash hash persistido esperado
   * @return {@code true} somente quando a prova corresponde
   */
  public boolean matches(String token, byte[] expectedHash) {
    Objects.requireNonNull(expectedHash, "expectedHash must not be null");
    return MessageDigest.isEqual(hash(token), expectedHash);
  }
}
