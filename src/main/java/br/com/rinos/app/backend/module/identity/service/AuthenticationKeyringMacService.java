package br.com.rinos.app.backend.module.identity.service;

import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;

/**
 * Fachada de MAC do keyring compartilhado pela proteção contra abuso e pelos OTPs.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationKeyringMacService {

  private final AuthenticationKeyringService keyring;

  /** Reutiliza a autoridade criptográfica única da instalação. */
  public AuthenticationKeyringMacService(AuthenticationKeyringService keyring) {
    this.keyring = Objects.requireNonNull(keyring, "keyring must not be null");
  }

  /**
   * Protege um valor canônico sem persistir ou devolver a entrada.
   *
   * @param domain domínio estável que impede colisão semântica entre e-mail e IP
   * @param canonicalValue bytes canônicos
   * @return digest de 32 bytes e versão ativa
   * @throws IllegalStateException quando o keyring não está habilitado ou o provedor JCE falha
   */
  public ProtectedAuthenticationKeyVO protect(String domain, byte[] canonicalValue) {
    return keyring.protectMac(domain, canonicalValue);
  }

  /** Verifica um valor contra a versão persistida, inclusive durante rotação. */
  public boolean matches(
      String domain,
      byte[] canonicalValue,
      ProtectedAuthenticationKeyVO protectedValue) {
    return keyring.matchesMac(domain, canonicalValue, protectedValue);
  }
}
