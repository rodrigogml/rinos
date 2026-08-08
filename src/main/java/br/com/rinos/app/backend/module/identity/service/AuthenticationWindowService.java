package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationWindowEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationWindowRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationCleanupResultVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationWindowDecisionVO;
import br.com.rinos.app.config.AuthenticationAbusePropertiesConfig;
import jakarta.persistence.EntityNotFoundException;

/**
 * Controla falhas por identificador protegido em janelas compartilhadas por todas as instâncias.
 *
 * <p>O serviço nunca recebe o e-mail: o chamador deve fornecer MAC de 32 bytes e sua versão de
 * chave. A UK cria a janela uma única vez; a contagem é serializada por lock pessimista.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationWindowService {

  private final AuthenticationWindowRepository repository;
  private final AuthenticationAbusePropertiesConfig properties;

  /** Cria o controle sobre a persistência global e sua política fixa. */
  public AuthenticationWindowService(
      AuthenticationWindowRepository repository,
      AuthenticationAbusePropertiesConfig properties) {
    this.repository = repository;
    this.properties = properties;
  }

  /** Registra uma falha atômica e devolve somente a decisão pública da política. */
  @Transactional
  public AuthenticationWindowDecisionVO registerFailure(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant occurredAt) {
    validateKey(identifierDigest, keyVersion, operation, occurredAt);
    AuthenticationWindowEntity window = currentWindow(
        identifierDigest, keyVersion, operation, occurredAt, true);
    Instant turnstileUntil = occurredAt.plus(properties.turnstileDuration());
    window.registerFailure(properties.failureThreshold(), turnstileUntil);
    repository.flush();
    return decision(window, occurredAt);
  }

  /** Consulta a decisão vigente sem incrementar o contador de falhas. */
  @Transactional
  public AuthenticationWindowDecisionVO inspect(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant occurredAt) {
    validateKey(identifierDigest, keyVersion, operation, occurredAt);
    AuthenticationWindowEntity window = repository.findActiveForUpdate(
        identifierDigest, keyVersion, operation).orElse(null);
    if (window == null || !occurredAt.isBefore(window.getWindowEndsAt())) {
      if (window != null) {
        window.close();
      }
      return new AuthenticationWindowDecisionVO(
          0, false, Duration.ZERO, occurredAt.plus(properties.window()), null);
    }
    return decision(window, occurredAt);
  }

  /** Encerra a janela depois de uma autenticação bem-sucedida, sem apagar seu histórico. */
  @Transactional
  public boolean clear(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant occurredAt) {
    validateKey(identifierDigest, keyVersion, operation, occurredAt);
    AuthenticationWindowEntity window = repository.findActiveForUpdate(
        identifierDigest, keyVersion, operation).orElse(null);
    if (window == null) {
      return false;
    }
    window.close();
    return true;
  }

  /** Encerra janelas vencidas e remove histórico além da retenção configurada. */
  @Transactional
  public AuthenticationCleanupResultVO cleanup(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    List<AuthenticationWindowEntity> expired = repository.findExpiredActiveForUpdate(occurredAt);
    expired.forEach(AuthenticationWindowEntity::close);
    repository.flush();
    int deleted = repository.deleteClosedBefore(occurredAt.minus(properties.retention()));
    return new AuthenticationCleanupResultVO(expired.size(), deleted);
  }

  private AuthenticationWindowEntity currentWindow(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant occurredAt,
      boolean recreateExpired) {
    Instant windowEndsAt = occurredAt.plus(properties.window());
    repository.ensureActive(
        identifierDigest, keyVersion, operation.name(), occurredAt, windowEndsAt);
    AuthenticationWindowEntity window = repository.findActiveForUpdate(
        identifierDigest, keyVersion, operation)
        .orElseThrow(() -> new EntityNotFoundException(
            "Authentication window was not created"));
    if (recreateExpired && !occurredAt.isBefore(window.getWindowEndsAt())) {
      window.close();
      repository.flush();
      repository.ensureActive(
          identifierDigest, keyVersion, operation.name(), occurredAt, windowEndsAt);
      window = repository.findActiveForUpdate(identifierDigest, keyVersion, operation)
          .orElseThrow(() -> new EntityNotFoundException(
              "Authentication window was not recreated"));
    }
    return window;
  }

  private AuthenticationWindowDecisionVO decision(
      AuthenticationWindowEntity window,
      Instant occurredAt) {
    Instant requiredUntil = window.getTurnstileRequiredUntil();
    boolean turnstileRequired = requiredUntil != null && occurredAt.isBefore(requiredUntil);
    return new AuthenticationWindowDecisionVO(
        window.getFailureCount(),
        turnstileRequired,
        progressiveDelay(window.getFailureCount()),
        window.getWindowEndsAt(),
        requiredUntil);
  }

  private Duration progressiveDelay(int failureCount) {
    if (failureCount <= 0) {
      return Duration.ZERO;
    }
    int exponent = Math.min(failureCount - 1, 30);
    Duration delay = properties.initialDelay().multipliedBy(1L << exponent);
    return delay.compareTo(properties.maximumDelay()) > 0
        ? properties.maximumDelay() : delay;
  }

  private static void validateKey(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant occurredAt) {
    if (identifierDigest == null || identifierDigest.length != 32) {
      throw new IllegalArgumentException("identifierDigest must contain 32 bytes");
    }
    if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 32) {
      throw new IllegalArgumentException("keyVersion is invalid");
    }
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
