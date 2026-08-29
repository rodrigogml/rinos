package br.com.rinos.app.backend.module.account.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.facade.AccountCreationContextFacade;
import br.com.rinos.app.api.module.account.facade.AccountCreationFacade;
import br.com.rinos.app.api.module.account.vo.AccountCreationContext;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
import br.com.rinos.app.api.module.account.vo.AccountCreationStatus;
import br.com.rinos.app.backend.module.account.service.AccountCreationAcceptanceService;
import br.com.rinos.app.backend.module.account.service.AccountCreationAdmissionService;
import br.com.rinos.app.backend.module.account.service.AccountCreationStatusService;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationPort;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationResult;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

/**
 * Publica a criação de contas derivando somente contexto autenticado da fronteira hospedeira.
 *
 * <p>Esta fachada nunca aceita identidade, referência de sessão ou origem como campos livres do
 * pedido. Falhas de infraestrutura ou de prova não atravessam como detalhes internos.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
@Service
@Lazy
public class AccountCreationFacadeImpl implements AccountCreationFacade {

  private static final String REAUTHENTICATION_OPERATION = "create-account";

  private final AccountCreationContextFacade contextFacade;
  private final ReauthenticationFacade reauthenticationFacade;
  private final OriginAddressService originAddressService;
  private final AccountCreationAdmissionService admissionService;
  private final AccountHumanVerificationPort humanVerification;
  private final AccountCreationAcceptanceService acceptanceService;
  private final AccountCreationStatusService statusService;
  private final Clock clock;

  /**
   * Cria a fachada com suas fronteiras de identidade, antiabuso e persistência.
   */
  public AccountCreationFacadeImpl(
      AccountCreationContextFacade contextFacade,
      ReauthenticationFacade reauthenticationFacade,
      OriginAddressService originAddressService,
      AccountCreationAdmissionService admissionService,
      AccountHumanVerificationPort humanVerification,
      AccountCreationAcceptanceService acceptanceService,
      AccountCreationStatusService statusService) {
    this(
        contextFacade,
        reauthenticationFacade,
        originAddressService,
        admissionService,
        humanVerification,
        acceptanceService,
        statusService,
        Clock.systemUTC());
  }

  AccountCreationFacadeImpl(
      AccountCreationContextFacade contextFacade,
      ReauthenticationFacade reauthenticationFacade,
      OriginAddressService originAddressService,
      AccountCreationAdmissionService admissionService,
      AccountHumanVerificationPort humanVerification,
      AccountCreationAcceptanceService acceptanceService,
      AccountCreationStatusService statusService,
      Clock clock) {
    this.contextFacade = Objects.requireNonNull(contextFacade, "contextFacade must not be null");
    this.reauthenticationFacade = Objects.requireNonNull(
        reauthenticationFacade, "reauthenticationFacade must not be null");
    this.originAddressService = Objects.requireNonNull(
        originAddressService, "originAddressService must not be null");
    this.admissionService = Objects.requireNonNull(admissionService, "admissionService must not be null");
    this.humanVerification = Objects.requireNonNull(humanVerification, "humanVerification must not be null");
    this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService must not be null");
    this.statusService = Objects.requireNonNull(statusService, "statusService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public AccountCreationResult request(AccountCreationRequest request) {
    if (request == null) {
      return rejected("ACCOUNT_INPUT_INVALID");
    }
    Optional<AccountCreationContext> current;
    try {
      current = contextFacade.current();
    } catch (RuntimeException unavailableContext) {
      return unavailable();
    }
    if (current.isEmpty()) {
      return rejected("ACCOUNT_IDENTITY_INACTIVE");
    }
    AccountCreationContext context = current.get();
    Instant occurredAt = clock.instant();
    try {
      if (!reauthenticationFacade.isRecentlyAuthorized(
          context.userId(),
          context.sessionReference(),
          REAUTHENTICATION_OPERATION,
          occurredAt)) {
        return rejected("ACCOUNT_RECENT_AUTH_REQUIRED");
      }
      AccountCreationResult existing = acceptanceService.findExisting(context.userId(), request);
      if (existing != null) {
        return existing;
      }
      OriginAddressVO origin = originAddressService.normalize(context.canonicalOrigin());
      boolean humanVerificationValid = verifyWhenRequired(request, context, origin);
      return acceptanceService.accept(
          context.userId(),
          request,
          UUID.randomUUID().toString(),
          occurredAt,
          origin,
          humanVerificationValid);
    } catch (HumanVerificationRejectedException exception) {
      return rejected("ACCOUNT_HUMAN_VERIFICATION_REJECTED");
    } catch (HumanVerificationUnavailableException exception) {
      return unavailable();
    } catch (RuntimeException exception) {
      return unavailable();
    }
  }

  /** {@inheritDoc} */
  @Override
  public AccountCreationStatus status(UUID protocolId) {
    Optional<AccountCreationContext> current = contextFacade.current();
    if (current.isEmpty()) {
      throw new IllegalStateException("ACCOUNT_IDENTITY_INACTIVE");
    }
    return statusService.find(current.get().userId(), protocolId);
  }

  private boolean verifyWhenRequired(
      AccountCreationRequest request,
      AccountCreationContext context,
      OriginAddressVO origin) {
    if (!admissionService.requiresHumanVerification(origin)) {
      return false;
    }
    AccountHumanVerificationResult result = humanVerification.verify(
        request.humanVerificationToken(), context.canonicalOrigin(), request.idempotencyKey());
    if (!result.providerAvailable()) {
      throw new HumanVerificationUnavailableException();
    }
    if (!result.valid()) {
      throw new HumanVerificationRejectedException();
    }
    return true;
  }

  private static AccountCreationResult rejected(String safeReasonCode) {
    return new AccountCreationResult(
        AccountCreationResultStatus.REJECTED,
        null,
        null,
        null,
        safeReasonCode,
        null);
  }

  private static AccountCreationResult unavailable() {
    return new AccountCreationResult(
        AccountCreationResultStatus.UNAVAILABLE,
        null,
        null,
        null,
        "ACCOUNT_CREATION_UNAVAILABLE",
        null);
  }

  private static final class HumanVerificationRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private static final class HumanVerificationUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
