package br.com.rinos.app.backend.module.account.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.facade.AccountCreationContextFacade;
import br.com.rinos.app.api.module.account.vo.AccountCreationContext;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
import br.com.rinos.app.backend.module.account.service.AccountCreationAcceptanceService;
import br.com.rinos.app.backend.module.account.service.AccountCreationAdmissionService;
import br.com.rinos.app.backend.module.account.service.AccountCreationStatusService;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationPort;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationResult;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;

@DisplayName("Fachada segura de criação de conta")
class AccountCreationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
  private static final AccountCreationContext CONTEXT = new AccountCreationContext(
      41L, "c3ef7a78-0bb6-4577-8d1d-6f6161047d11", "203.0.113.10");

  private AccountCreationContextFacade contexts;
  private ReauthenticationFacade reauthentication;
  private AccountCreationAdmissionService admission;
  private AccountHumanVerificationPort humanVerification;
  private AccountCreationAcceptanceService acceptance;
  private AccountCreationFacadeImpl facade;

  @BeforeEach
  void setUp() {
    contexts = mock(AccountCreationContextFacade.class);
    reauthentication = mock(ReauthenticationFacade.class);
    admission = mock(AccountCreationAdmissionService.class);
    humanVerification = mock(AccountHumanVerificationPort.class);
    acceptance = mock(AccountCreationAcceptanceService.class);
    facade = new AccountCreationFacadeImpl(
        contexts,
        reauthentication,
        new OriginAddressService(),
        admission,
        humanVerification,
        acceptance,
        mock(AccountCreationStatusService.class),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void request_shouldReject_whenAuthenticatedContextIsUnavailable() {
    when(contexts.current()).thenReturn(Optional.empty());

    AccountCreationResult result = facade.request(request());

    assertThat(result.status()).isEqualTo(AccountCreationResultStatus.REJECTED);
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_IDENTITY_INACTIVE");
    verifyNoInteractions(reauthentication, admission, humanVerification, acceptance);
  }

  @Test
  void request_shouldReject_whenRecentAuthenticationIsMissing() {
    AccountCreationRequest request = request();
    when(contexts.current()).thenReturn(Optional.of(CONTEXT));
    when(reauthentication.isRecentlyAuthorized(
        CONTEXT.userId(), CONTEXT.sessionReference(), "create-account", NOW)).thenReturn(false);

    AccountCreationResult result = facade.request(request);

    assertThat(result.status()).isEqualTo(AccountCreationResultStatus.REJECTED);
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_RECENT_AUTH_REQUIRED");
    verifyNoInteractions(admission, humanVerification, acceptance);
  }

  @Test
  void request_shouldReturnReplayWithoutConsumingAnotherTurnstileToken() {
    AccountCreationRequest request = request();
    AccountCreationResult replay = new AccountCreationResult(
        AccountCreationResultStatus.REPLAYED,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        null,
        null);
    authorize(CONTEXT);
    when(acceptance.findExisting(CONTEXT.userId(), request)).thenReturn(replay);

    AccountCreationResult result = facade.request(request);

    assertThat(result).isSameAs(replay);
    verifyNoInteractions(admission, humanVerification);
  }

  @Test
  void request_shouldAccept_whenProofIsValidAndAdmissionSucceeds() {
    AccountCreationRequest request = request();
    AccountCreationResult accepted = new AccountCreationResult(
        AccountCreationResultStatus.ACCEPTED,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        null,
        null);
    authorize(CONTEXT);
    when(acceptance.findExisting(CONTEXT.userId(), request)).thenReturn(null);
    when(admission.requiresHumanVerification(any())).thenReturn(true);
    when(humanVerification.verify(request.humanVerificationToken(), CONTEXT.canonicalOrigin(), request.idempotencyKey()))
        .thenReturn(new AccountHumanVerificationResult(true, true));
    when(acceptance.accept(
        eq(CONTEXT.userId()), eq(request), any(), eq(NOW), any(), eq(true))).thenReturn(accepted);

    AccountCreationResult result = facade.request(request);

    assertThat(result).isSameAs(accepted);
    verify(humanVerification).verify(
        request.humanVerificationToken(), CONTEXT.canonicalOrigin(), request.idempotencyKey());
    verify(acceptance).accept(eq(CONTEXT.userId()), eq(request), any(), eq(NOW), any(), eq(true));
  }

  @Test
  void request_shouldFailClosed_whenTurnstileProviderIsUnavailable() {
    AccountCreationRequest request = request();
    authorize(CONTEXT);
    when(acceptance.findExisting(CONTEXT.userId(), request)).thenReturn(null);
    when(admission.requiresHumanVerification(any())).thenReturn(true);
    when(humanVerification.verify(request.humanVerificationToken(), CONTEXT.canonicalOrigin(), request.idempotencyKey()))
        .thenReturn(AccountHumanVerificationResult.unavailable());

    AccountCreationResult result = facade.request(request);

    assertThat(result.status()).isEqualTo(AccountCreationResultStatus.UNAVAILABLE);
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_CREATION_UNAVAILABLE");
    verify(acceptance).findExisting(CONTEXT.userId(), request);
    verifyNoMoreInteractions(acceptance);
  }

  private void authorize(AccountCreationContext context) {
    when(contexts.current()).thenReturn(Optional.of(context));
    when(reauthentication.isRecentlyAuthorized(
        context.userId(), context.sessionReference(), "create-account", NOW)).thenReturn(true);
  }

  private static AccountCreationRequest request() {
    return new AccountCreationRequest(
        UUID.randomUUID(), "Conta de teste", "BRL", "America/Sao_Paulo", "turnstile-token", true);
  }
}
