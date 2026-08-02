package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.PasswordRecoveryRequestDTO;
import br.com.rinos.app.api.dto.PasswordResetRequestDTO;
import br.com.rinos.app.api.enums.PasswordRecoveryRequestStatusEnum;
import br.com.rinos.app.api.enums.PasswordResetStatusEnum;
import br.com.rinos.app.api.facade.PasswordRecoveryFacade;
import br.com.rinos.app.api.vo.PasswordRecoveryRequestResultVO;
import br.com.rinos.app.api.vo.PasswordResetResultVO;
import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.service.PasswordPreparationService;
import br.com.rinos.app.backend.module.identity.service.PasswordRecoveryService;
import br.com.rinos.app.backend.module.identity.vo.PasswordPreparationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordRecoveryOperationVO;

/**
 * Orquestra validação de senha, transação de recuperação e resposta neutra da borda pública.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Service
@Lazy
public class PasswordRecoveryFacadeImpl implements PasswordRecoveryFacade {

  private final PasswordRecoveryService recoveryService;
  private final PasswordPreparationService passwordPreparationService;
  private final Clock clock;

  /**
   * Cria a fachada com relógio UTC.
   *
   * @param recoveryService transações de emissão e consumo
   * @param passwordPreparationService política e hash da senha
   */
  public PasswordRecoveryFacadeImpl(
      PasswordRecoveryService recoveryService,
      PasswordPreparationService passwordPreparationService) {
    this(recoveryService, passwordPreparationService, Clock.systemUTC());
  }

  /**
   * Cria uma instância com relógio controlável para testes.
   *
   * @param recoveryService transações de recuperação
   * @param passwordPreparationService preparação da nova senha
   * @param clock relógio da operação
   */
  PasswordRecoveryFacadeImpl(
      PasswordRecoveryService recoveryService,
      PasswordPreparationService passwordPreparationService,
      Clock clock) {
    this.recoveryService = recoveryService;
    this.passwordPreparationService = passwordPreparationService;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<PasswordRecoveryRequestResultVO> requestRecovery(
      PasswordRecoveryRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    try {
      PasswordRecoveryOperationVO result = recoveryService.issue(
          request.identifier(),
          request.canonicalOrigin(),
          request.locale(),
          request.correlationId(),
          clock.instant());
      PasswordRecoveryRequestResultVO publicResult = switch (result.status()) {
        case RATE_LIMITED -> new PasswordRecoveryRequestResultVO(
            PasswordRecoveryRequestStatusEnum.RATE_LIMITED,
            retryAfter(result.retryAfter()));
        default -> new PasswordRecoveryRequestResultVO(
            PasswordRecoveryRequestStatusEnum.ACCEPTED,
            null);
      };
      if (result.dispatch() == null) {
        return CompletableFuture.completedFuture(publicResult);
      }
      return result.dispatch().handle((ignored, failure) -> publicResult);
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(new PasswordRecoveryRequestResultVO(
          PasswordRecoveryRequestStatusEnum.UNAVAILABLE,
          null));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<PasswordResetResultVO> resetPassword(PasswordResetRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    PasswordPreparationResultVO preparation = passwordPreparationService.prepare(
        request.consumePassword());
    if (!preparation.getValidation().accepted()) {
      PasswordPolicyViolationEnum violation = preparation.getValidation().violations().getFirst();
      return completed(new PasswordResetResultVO(
          PasswordResetStatusEnum.VALIDATION_REJECTED,
          Map.of("password", passwordMessageKey(violation)),
          null));
    }
    try {
      PasswordRecoveryOperationVO result = recoveryService.reset(
          request.getProof(),
          preparation.getEncodedHash(),
          request.getCanonicalOrigin(),
          request.getCorrelationId(),
          clock.instant());
      return completed(switch (result.status()) {
        case COMPLETED -> new PasswordResetResultVO(
            PasswordResetStatusEnum.COMPLETED, Map.of(), null);
        case EXPIRED_PROOF -> new PasswordResetResultVO(
            PasswordResetStatusEnum.EXPIRED_PROOF, Map.of(), null);
        case RATE_LIMITED -> new PasswordResetResultVO(
            PasswordResetStatusEnum.RATE_LIMITED, Map.of(), retryAfter(result.retryAfter()));
        default -> new PasswordResetResultVO(
            PasswordResetStatusEnum.INVALID_PROOF, Map.of(), null);
      });
    } catch (RuntimeException exception) {
      return completed(new PasswordResetResultVO(
          PasswordResetStatusEnum.UNAVAILABLE, Map.of(), null));
    }
  }

  private static String passwordMessageKey(PasswordPolicyViolationEnum violation) {
    return "registration.error.password."
        + violation.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }

  private Duration retryAfter(Instant blockedUntil) {
    if (blockedUntil == null) {
      return null;
    }
    Duration remaining = Duration.between(clock.instant(), blockedUntil);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  private static CompletionStage<PasswordResetResultVO> completed(
      PasswordResetResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
