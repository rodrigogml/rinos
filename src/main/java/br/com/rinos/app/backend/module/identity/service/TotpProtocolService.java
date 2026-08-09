package br.com.rinos.app.backend.module.identity.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedTotpEnrollmentVO;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.SecondFactorConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWTotpService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWTotpEnrollmentVO;

/**
 * Aplica o protocolo TOTP do RFW ao envelope criptográfico vinculado à identidade do Rinos.
 *
 * <p>A validação de janela reutiliza um verificador RFW com janela zero para identificar o passo
 * exato aceito. O serviço não registra nem conserva o segredo em claro.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
public class TotpProtocolService {

  private static final String ISSUER = "Rinos";
  private static final String DOMAIN_PREFIX = "totp-secret:";

  private final RFWTotpService enrollmentService;
  private final RFWTotpService exactStepVerifier;
  private final AuthenticationKeyringService keyring;
  private final int periodSeconds;
  private final int acceptedWindow;

  /** Cria o protocolo usando exclusivamente os parâmetros MFA publicados pela RFW. */
  public TotpProtocolService(
      RFWTotpService enrollmentService,
      RFWAuthenticationPropertiesConfig rfwProperties,
      AuthenticationKeyringService keyring) {
    this.enrollmentService = Objects.requireNonNull(
        enrollmentService, "enrollmentService must not be null");
    this.keyring = Objects.requireNonNull(keyring, "keyring must not be null");
    SecondFactorConfig config = Objects.requireNonNull(
        rfwProperties, "rfwProperties must not be null").secondFactor();
    periodSeconds = config.totpPeriodSeconds();
    acceptedWindow = config.totpWindow();
    exactStepVerifier = new RFWTotpService(new SecondFactorConfig(
        config.totpDigits(),
        config.totpPeriodSeconds(),
        0,
        config.emailCodeDigits(),
        config.recoveryCodeCount()));
  }

  /** Gera a apresentação única e cifra o mesmo segredo para persistência. */
  public ProtectedTotpEnrollmentVO create(
      long userId,
      UUID reference,
      String accountName) {
    validateIdentity(userId, reference);
    RFWTotpEnrollmentVO presentation = enrollmentService.createEnrollment(ISSUER, accountName);
    byte[] plaintext = presentation.secret().getBytes(StandardCharsets.US_ASCII);
    try {
      return new ProtectedTotpEnrollmentVO(
          presentation,
          keyring.encrypt(domain(userId, reference), plaintext));
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  /**
   * Localiza o maior passo correspondente dentro da janela aceita pelo RFW.
   *
   * <p>Escolher o maior passo em uma colisão numérica rara impede que o mesmo código volte a ser
   * aceito quando o relógio alcançar uma janela futura também correspondente.
   */
  public OptionalLong acceptedStep(
      long userId,
      UUID reference,
      EncryptedAuthenticationSecretVO encryptedSecret,
      String code,
      Instant occurredAt) {
    validateIdentity(userId, reference);
    Objects.requireNonNull(encryptedSecret, "encryptedSecret must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    byte[] plaintext = keyring.decrypt(domain(userId, reference), encryptedSecret);
    try {
      String secret = new String(plaintext, StandardCharsets.US_ASCII);
      long currentStep = Math.floorDiv(occurredAt.getEpochSecond(), periodSeconds);
      for (int offset = acceptedWindow; offset >= -acceptedWindow; offset--) {
        long candidateStep = currentStep + offset;
        if (candidateStep >= 0 && exactStepVerifier.verify(
            secret,
            code,
            Instant.ofEpochSecond(Math.multiplyExact(candidateStep, periodSeconds)))) {
          return OptionalLong.of(candidateStep);
        }
      }
      return OptionalLong.empty();
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  private static String domain(long userId, UUID reference) {
    return DOMAIN_PREFIX + userId + ":" + reference;
  }

  private static void validateIdentity(long userId, UUID reference) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    Objects.requireNonNull(reference, "reference must not be null");
  }
}
