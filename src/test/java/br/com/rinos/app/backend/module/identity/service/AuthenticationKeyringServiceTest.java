package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;

@DisplayName("Keyring AEAD e MAC versionado")
class AuthenticationKeyringServiceTest {

  private static final String V1 = key((byte) 1);
  private static final String V2 = key((byte) 2);
  private static final byte[] VALUE = "123456".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SECRET = "totp-secret-material".getBytes(StandardCharsets.UTF_8);

  @Test
  void mac_shouldWriteWithActiveVersionAndReadPreviousVersionAfterRotation() {
    AuthenticationKeyringService initial = keyring("v1", Map.of("v1", V1));
    ProtectedAuthenticationKeyVO protectedV1 =
        initial.protectMac("email-otp", VALUE);
    AuthenticationKeyringService rotated = keyring(
        "v2", Map.of("v1", V1, "v2", V2));

    ProtectedAuthenticationKeyVO protectedV2 =
        rotated.protectMac("email-otp", VALUE);

    assertThat(protectedV1.keyVersion()).isEqualTo("v1");
    assertThat(protectedV2.keyVersion()).isEqualTo("v2");
    assertThat(rotated.matchesMac("email-otp", VALUE, protectedV1)).isTrue();
    assertThat(rotated.matchesMac("email-otp", VALUE, protectedV2)).isTrue();
    assertThat(rotated.matchesMac(
        "email-otp", "654321".getBytes(StandardCharsets.UTF_8), protectedV1)).isFalse();
    assertThat(rotated.matchesMac("another-domain", VALUE, protectedV1)).isFalse();
  }

  @Test
  void mac_shouldPreservePreviouslyPersistedDigestFormat() {
    ProtectedAuthenticationKeyVO protectedValue =
        keyring("v1", Map.of("v1", V1)).protectMac("email-otp", VALUE);

    assertThat(protectedValue.digest()).containsExactly(HexFormat.of().parseHex(
        "82828B73760E8E57C0901E81CCCFE8F94F6338B533EB8AD2C0C1EFBCD1032C6D"));
  }

  @Test
  void mac_shouldFailClosedWhenPersistedVersionIsNoLongerAvailable() {
    AuthenticationKeyringService rotatedWithoutOldKey = keyring("v2", Map.of("v2", V2));
    ProtectedAuthenticationKeyVO oldValue = new ProtectedAuthenticationKeyVO(
        new byte[32], "v1");

    assertThat(rotatedWithoutOldKey.matchesMac("email-otp", VALUE, oldValue)).isFalse();
  }

  @Test
  void aead_shouldUseFreshNonceAndDecryptWithPreviousVersionAfterRotation() {
    AuthenticationKeyringService initial = keyring("v1", Map.of("v1", V1));
    EncryptedAuthenticationSecretVO first = initial.encrypt("totp-secret", SECRET);
    EncryptedAuthenticationSecretVO second = initial.encrypt("totp-secret", SECRET);
    AuthenticationKeyringService rotated = keyring(
        "v2", Map.of("v1", V1, "v2", V2));

    assertThat(first.keyVersion()).isEqualTo("v1");
    assertThat(first.nonce()).isNotEqualTo(second.nonce());
    assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    assertThat(rotated.decrypt("totp-secret", first)).containsExactly(SECRET);
    assertThat(rotated.encrypt("totp-secret", SECRET).keyVersion()).isEqualTo("v2");
  }

  @Test
  void aead_shouldRejectTamperingAndSemanticDomainSwap() {
    AuthenticationKeyringService service = keyring("v1", Map.of("v1", V1));
    EncryptedAuthenticationSecretVO encrypted = service.encrypt("totp-secret", SECRET);
    byte[] tamperedCiphertext = encrypted.ciphertext();
    tamperedCiphertext[0] ^= 1;
    EncryptedAuthenticationSecretVO tampered = new EncryptedAuthenticationSecretVO(
        tamperedCiphertext, encrypted.nonce(), encrypted.keyVersion());

    assertThatThrownBy(() -> service.decrypt("totp-secret", tampered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("encrypted authentication secret is invalid");
    assertThatThrownBy(() -> service.decrypt("another-domain", encrypted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("encrypted authentication secret is invalid");
  }

  @Test
  void aead_shouldFailClosedWhenReadVersionIsUnavailable() {
    AuthenticationKeyringService initial = keyring("v1", Map.of("v1", V1));
    EncryptedAuthenticationSecretVO encrypted = initial.encrypt("totp-secret", SECRET);
    AuthenticationKeyringService withoutV1 = keyring("v2", Map.of("v2", V2));

    assertThatThrownBy(() -> withoutV1.decrypt("totp-secret", encrypted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("authentication key version is unavailable");
  }

  @Test
  void disabledKeyring_shouldFailClosedForMacAndAead() {
    AuthenticationKeyringService disabled = new AuthenticationKeyringService(
        new AuthenticationKeyringPropertiesConfig(false, "", Map.of()));

    assertThatThrownBy(() -> disabled.protectMac("email-otp", VALUE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("authentication keyring is not enabled");
    assertThatThrownBy(() -> disabled.encrypt("totp-secret", SECRET))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("authentication keyring is not enabled");
  }

  @Test
  void encryptedEnvelope_shouldDefensivelyCopyAndRedactMaterial() {
    byte[] ciphertext = new byte[32];
    byte[] nonce = new byte[12];
    EncryptedAuthenticationSecretVO envelope =
        new EncryptedAuthenticationSecretVO(ciphertext, nonce, "v1");
    ciphertext[0] = 1;
    nonce[0] = 1;
    byte[] returnedCiphertext = envelope.ciphertext();
    byte[] returnedNonce = envelope.nonce();
    returnedCiphertext[1] = 1;
    returnedNonce[1] = 1;

    assertThat(envelope.ciphertext()).containsOnly((byte) 0);
    assertThat(envelope.nonce()).containsOnly((byte) 0);
    assertThat(envelope.toString()).doesNotContain(
        Base64.getEncoder().encodeToString(ciphertext),
        Base64.getEncoder().encodeToString(nonce));
  }

  @Test
  void configuration_shouldRejectAliasedVersionsAndRedactKeys() {
    AuthenticationKeyringPropertiesConfig valid =
        new AuthenticationKeyringPropertiesConfig(true, "v1", Map.of("v1", V1));

    assertThat(valid.toString()).doesNotContain(V1).contains("keys=REDACTED(1)");
    assertThatThrownBy(() -> new AuthenticationKeyringPropertiesConfig(
        true, "v2", Map.of("v1", V1, "v2", V1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cada versão deve ser canônica e distinta");
  }

  private static AuthenticationKeyringService keyring(
      String activeVersion,
      Map<String, String> keys) {
    return new AuthenticationKeyringService(
        new AuthenticationKeyringPropertiesConfig(true, activeVersion, keys));
  }

  private static String key(byte value) {
    byte[] key = new byte[32];
    Arrays.fill(key, value);
    return Base64.getEncoder().encodeToString(key);
  }
}
