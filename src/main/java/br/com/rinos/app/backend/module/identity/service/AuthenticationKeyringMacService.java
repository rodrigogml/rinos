package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;

/**
 * Produz MACs HMAC-SHA-256 com separação de domínio usando a chave ativa da instalação.
 *
 * <p>Este serviço implementa somente a capacidade de MAC exigida pelas janelas antifraude. O
 * lifecycle completo de AEAD, chaves de leitura anteriores e rotação pertence ao ciclo 4.1.1.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationKeyringMacService {

  private static final String ALGORITHM = "HmacSHA256";

  private final String activeVersion;
  private final byte[] activeKey;

  /** Carrega somente a chave ativa validada pelas propriedades exclusivas. */
  public AuthenticationKeyringMacService(AuthenticationKeyringPropertiesConfig properties) {
    Objects.requireNonNull(properties, "properties must not be null");
    if (!properties.enabled()) {
      activeVersion = null;
      activeKey = null;
      return;
    }
    activeVersion = properties.activeVersion();
    activeKey = Base64.getDecoder().decode(properties.keys().get(activeVersion));
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
    if (activeKey == null || activeVersion == null) {
      throw new IllegalStateException("authentication keyring is not enabled");
    }
    if (domain == null || domain.isBlank()) {
      throw new IllegalArgumentException("domain must not be blank");
    }
    Objects.requireNonNull(canonicalValue, "canonicalValue must not be null");
    byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
    ByteBuffer input = ByteBuffer.allocate(
        Integer.BYTES + domainBytes.length + canonicalValue.length);
    input.putInt(domainBytes.length).put(domainBytes).put(canonicalValue);
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(activeKey, ALGORITHM));
      return new ProtectedAuthenticationKeyVO(mac.doFinal(input.array()), activeVersion);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication MAC is unavailable", failure);
    }
  }
}
