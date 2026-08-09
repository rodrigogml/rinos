package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;

/**
 * Autoridade criptográfica local para MAC e AEAD com escrita pela versão ativa e leitura das
 * versões anteriores.
 *
 * <p>A chave AES-GCM é derivada da raiz, enquanto o HMAC preserva o formato já persistido pelas
 * janelas antifraude. Domínio e versão autenticam o envelope cifrado; o MAC mantém sua separação
 * de domínio no próprio conteúdo autenticado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
public class AuthenticationKeyringService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String AEAD_ALGORITHM = "AES/GCM/NoPadding";
  private static final String AES_ALGORITHM = "AES";
  private static final int NONCE_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final int MAX_PLAINTEXT_BYTES = 496;
  private static final byte[] DERIVATION_CONTEXT =
      "rinos/authentication/keyring/v1".getBytes(StandardCharsets.UTF_8);

  private final String activeVersion;
  private final Map<String, byte[]> rootKeys;
  private final SecureRandom secureRandom;

  /** Carrega e valida o keyring de origem exclusiva no arquivo de propriedades. */
  @Autowired
  public AuthenticationKeyringService(AuthenticationKeyringPropertiesConfig properties) {
    this(properties, new SecureRandom());
  }

  AuthenticationKeyringService(
      AuthenticationKeyringPropertiesConfig properties,
      SecureRandom secureRandom) {
    Objects.requireNonNull(properties, "properties must not be null");
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    activeVersion = properties.enabled() ? properties.activeVersion() : null;
    rootKeys = properties.enabled()
        ? properties.keys().entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Base64.getDecoder().decode(entry.getValue())))
        : Map.of();
    if (properties.enabled()) {
      verifyCryptographicProvider();
    }
  }

  /** Produz HMAC-SHA-256 com a versão ativa e separação de domínio. */
  public ProtectedAuthenticationKeyVO protectMac(String domain, byte[] canonicalValue) {
    byte[] digest = calculateMac(activeKey(), domain, canonicalValue);
    return new ProtectedAuthenticationKeyVO(digest, activeVersion);
  }

  /** Compara em tempo constante usando a versão gravada no envelope. */
  public boolean matchesMac(
      String domain,
      byte[] canonicalValue,
      ProtectedAuthenticationKeyVO protectedValue) {
    Objects.requireNonNull(protectedValue, "protectedValue must not be null");
    byte[] rootKey = rootKeys.get(protectedValue.keyVersion());
    if (rootKey == null) {
      return false;
    }
    byte[] calculated = calculateMac(rootKey, domain, canonicalValue);
    try {
      return MessageDigest.isEqual(calculated, protectedValue.digest());
    } finally {
      Arrays.fill(calculated, (byte) 0);
    }
  }

  /** Cifra um segredo recuperável com AES-256-GCM e nonce novo. */
  public EncryptedAuthenticationSecretVO encrypt(String domain, byte[] plaintext) {
    validateDomain(domain);
    Objects.requireNonNull(plaintext, "plaintext must not be null");
    if (plaintext.length == 0 || plaintext.length > MAX_PLAINTEXT_BYTES) {
      throw new IllegalArgumentException("plaintext length is invalid");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    secureRandom.nextBytes(nonce);
    byte[] derivedKey = derive(activeKey(), "aead");
    try {
      Cipher cipher = Cipher.getInstance(AEAD_ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(derivedKey, AES_ALGORITHM),
          new GCMParameterSpec(GCM_TAG_BITS, nonce));
      cipher.updateAAD(authenticatedData(domain, activeVersion));
      return new EncryptedAuthenticationSecretVO(
          cipher.doFinal(plaintext), nonce, activeVersion);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication encryption is unavailable", failure);
    } finally {
      Arrays.fill(derivedKey, (byte) 0);
    }
  }

  /**
   * Decifra somente quando versão, domínio, nonce, ciphertext e tag pertencem ao mesmo envelope.
   */
  public byte[] decrypt(String domain, EncryptedAuthenticationSecretVO encrypted) {
    validateDomain(domain);
    Objects.requireNonNull(encrypted, "encrypted must not be null");
    byte[] rootKey = rootKeys.get(encrypted.keyVersion());
    if (rootKey == null) {
      throw new IllegalStateException("authentication key version is unavailable");
    }
    byte[] derivedKey = derive(rootKey, "aead");
    try {
      Cipher cipher = Cipher.getInstance(AEAD_ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(derivedKey, AES_ALGORITHM),
          new GCMParameterSpec(GCM_TAG_BITS, encrypted.nonce()));
      cipher.updateAAD(authenticatedData(domain, encrypted.keyVersion()));
      return cipher.doFinal(encrypted.ciphertext());
    } catch (AEADBadTagException invalidEnvelope) {
      throw new IllegalArgumentException("encrypted authentication secret is invalid");
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication decryption is unavailable", failure);
    } finally {
      Arrays.fill(derivedKey, (byte) 0);
    }
  }

  private byte[] calculateMac(
      byte[] rootKey,
      String domain,
      byte[] canonicalValue) {
    validateDomain(domain);
    Objects.requireNonNull(canonicalValue, "canonicalValue must not be null");
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(rootKey, HMAC_ALGORITHM));
      return mac.doFinal(macInput(domain, canonicalValue));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication MAC is unavailable", failure);
    }
  }

  private static byte[] derive(byte[] rootKey, String purpose) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(rootKey, HMAC_ALGORITHM));
      mac.update(DERIVATION_CONTEXT);
      mac.update((byte) 0);
      return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication key derivation is unavailable", failure);
    }
  }

  private static byte[] authenticatedData(String domain, String keyVersion) {
    return structured(domain, keyVersion, new byte[0]);
  }

  private static byte[] macInput(String domain, byte[] value) {
    byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Integer.BYTES + domainBytes.length + value.length)
        .putInt(domainBytes.length)
        .put(domainBytes)
        .put(value)
        .array();
  }

  private static byte[] structured(String domain, String keyVersion, byte[] value) {
    byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
    byte[] versionBytes = keyVersion.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(
        Integer.BYTES + domainBytes.length
            + Integer.BYTES + versionBytes.length
            + value.length)
        .putInt(domainBytes.length)
        .put(domainBytes)
        .putInt(versionBytes.length)
        .put(versionBytes)
        .put(value)
        .array();
  }

  private byte[] activeKey() {
    byte[] activeKey = activeVersion == null ? null : rootKeys.get(activeVersion);
    if (activeKey == null) {
      throw new IllegalStateException("authentication keyring is not enabled");
    }
    return activeKey;
  }

  private static void validateDomain(String domain) {
    if (domain == null || domain.isBlank() || domain.length() > 128) {
      throw new IllegalArgumentException("domain is invalid");
    }
  }

  private static void verifyCryptographicProvider() {
    try {
      Mac.getInstance(HMAC_ALGORITHM);
      Cipher.getInstance(AEAD_ALGORITHM);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("authentication cryptography is unavailable", failure);
    }
  }
}
