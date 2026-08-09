package br.com.rinos.app.backend.module.identity.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationAbuseDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationWindowDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;

/**
 * Coordena janelas independentes de login por identificador e origem canônica.
 *
 * <p>A ordem invariável identificador → origem e a separação de domínio dos MACs impedem ciclos de
 * lock entre tentativas concorrentes. Nenhum e-mail ou IP atravessa a fronteira da persistência.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationAbuseProtectionService {

  private static final String IDENTIFIER_DOMAIN = "sign-in/identifier";
  private static final String ORIGIN_DOMAIN = "sign-in/origin";

  private final EmailNormalizationService emailNormalizationService;
  private final OriginAddressService originAddressService;
  private final AuthenticationKeyringMacService macService;
  private final AuthenticationWindowService windowService;

  /** Cria a coordenação sobre normalização, proteção e janela compartilhada. */
  public AuthenticationAbuseProtectionService(
      EmailNormalizationService emailNormalizationService,
      OriginAddressService originAddressService,
      AuthenticationKeyringMacService macService,
      AuthenticationWindowService windowService) {
    this.emailNormalizationService = emailNormalizationService;
    this.originAddressService = originAddressService;
    this.macService = macService;
    this.windowService = windowService;
  }

  /** Registra uma falha atomicamente nas duas dimensões e combina a política mais restritiva. */
  @Transactional
  public AuthenticationAbuseDecisionVO registerFailure(
      String identifier,
      String canonicalOrigin,
      Instant occurredAt) {
    ProtectedAuthenticationKeyVO identifierKey = identifierKey(identifier);
    ProtectedAuthenticationKeyVO originKey = originKey(canonicalOrigin);
    AuthenticationWindowDecisionVO identifierDecision = windowService.registerFailure(
        identifierKey.digest(), identifierKey.keyVersion(),
        AuthenticationWindowOperationEnum.SIGN_IN, occurredAt);
    AuthenticationWindowDecisionVO originDecision = windowService.registerFailure(
        originKey.digest(), originKey.keyVersion(),
        AuthenticationWindowOperationEnum.SIGN_IN, occurredAt);
    return combine(identifierDecision, originDecision);
  }

  /** Consulta somente a origem para o requisito prévio apresentado pelo RFW. */
  @Transactional
  public boolean isOriginTurnstileRequired(String canonicalOrigin, Instant occurredAt) {
    ProtectedAuthenticationKeyVO key = originKey(canonicalOrigin);
    return windowService.inspect(
        key.digest(), key.keyVersion(), AuthenticationWindowOperationEnum.SIGN_IN, occurredAt)
        .turnstileRequired();
  }

  /**
   * Consulta as dimensões disponíveis sem incrementar contadores.
   *
   * <p>Quando o identificador ainda não foi informado, somente a origem participa da decisão. Com
   * identificador presente, a ordem invariável de lock continua identificador → origem.
   *
   * @param identifier identificador efêmero informado, quando disponível
   * @param canonicalOrigin origem canônica obrigatória
   * @param occurredAt instante da decisão
   * @return {@code true} se qualquer dimensão exigir comprovação humana
   */
  @Transactional
  public boolean isTurnstileRequired(
      String identifier,
      String canonicalOrigin,
      Instant occurredAt) {
    if (identifier == null || identifier.isBlank()) {
      return isOriginTurnstileRequired(canonicalOrigin, occurredAt);
    }
    ProtectedAuthenticationKeyVO identifierKey = identifierKey(identifier);
    ProtectedAuthenticationKeyVO originKey = originKey(canonicalOrigin);
    AuthenticationWindowDecisionVO identifierDecision = windowService.inspect(
        identifierKey.digest(), identifierKey.keyVersion(),
        AuthenticationWindowOperationEnum.SIGN_IN, occurredAt);
    AuthenticationWindowDecisionVO originDecision = windowService.inspect(
        originKey.digest(), originKey.keyVersion(),
        AuthenticationWindowOperationEnum.SIGN_IN, occurredAt);
    return identifierDecision.turnstileRequired() || originDecision.turnstileRequired();
  }

  private ProtectedAuthenticationKeyVO identifierKey(String identifier) {
    String canonical;
    try {
      canonical = emailNormalizationService.normalize(identifier).normalizedEmail();
    } catch (NullPointerException | IllegalArgumentException invalid) {
      canonical = identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
      if (canonical.length() > 320) {
        canonical = canonical.substring(0, 320);
      }
    }
    return macService.protect(IDENTIFIER_DOMAIN, canonical.getBytes(StandardCharsets.UTF_8));
  }

  private ProtectedAuthenticationKeyVO originKey(String canonicalOrigin) {
    byte[] address = originAddressService.normalize(canonicalOrigin).getAddress();
    return macService.protect(ORIGIN_DOMAIN, address);
  }

  private static AuthenticationAbuseDecisionVO combine(
      AuthenticationWindowDecisionVO identifier,
      AuthenticationWindowDecisionVO origin) {
    return new AuthenticationAbuseDecisionVO(
        Math.max(identifier.failureCount(), origin.failureCount()),
        identifier.turnstileRequired() || origin.turnstileRequired(),
        maximum(identifier.retryDelay(), origin.retryDelay()),
        latest(identifier.turnstileRequiredUntil(), origin.turnstileRequiredUntil()));
  }

  private static Duration maximum(Duration first, Duration second) {
    return first.compareTo(second) >= 0 ? first : second;
  }

  private static Instant latest(Instant first, Instant second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return first.isAfter(second) ? first : second;
  }
}
