package br.com.rinos.app.backend.module.identity.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.dto.PasskeyManagementContextDTO;
import br.com.rinos.app.api.dto.PasskeyRenameRequestDTO;
import br.com.rinos.app.api.dto.PasskeyRevocationRequestDTO;
import br.com.rinos.app.api.enums.PasskeyManagementStatusEnum;
import br.com.rinos.app.api.enums.PasskeyStateEnum;
import br.com.rinos.app.api.facade.PasskeyManagementFacade;
import br.com.rinos.app.api.vo.PasskeyManagementResultVO;
import br.com.rinos.app.api.vo.PasskeyVO;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.PasskeyCredentialService;
import br.com.rinos.app.backend.module.identity.service.ReauthenticationService;
import jakarta.persistence.EntityNotFoundException;

/**
 * Aplica sessao, garantia recente e invariantes de identidade a gestao de passkeys.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class PasskeyManagementFacadeImpl implements PasskeyManagementFacade {

  private final AuthSessionService sessionService;
  private final ReauthenticationService reauthenticationService;
  private final PasskeyCredentialService passkeyService;

  /**
   * Cria a fachada sobre as autoridades de sessao, reautenticacao e credencial.
   *
   * @param sessionService autoridade da sessao global
   * @param reauthenticationService autoridade da garantia recente
   * @param passkeyService autoridade das credenciais WebAuthn
   */
  public PasskeyManagementFacadeImpl(
      AuthSessionService sessionService,
      ReauthenticationService reauthenticationService,
      PasskeyCredentialService passkeyService) {
    this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
    this.reauthenticationService = Objects.requireNonNull(
        reauthenticationService, "reauthenticationService must not be null");
    this.passkeyService = Objects.requireNonNull(passkeyService, "passkeyService must not be null");
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public List<PasskeyVO> list(PasskeyManagementContextDTO context) {
    ManagementContext validated = validateContext(context);
    try {
      sessionService.listManaged(
          validated.userId(), validated.sessionReference(), validated.occurredAt());
    } catch (EntityNotFoundException | IllegalStateException denied) {
      throw new SecurityException("current authentication session is unavailable", denied);
    }
    return passkeyService.list(validated.userId()).stream()
        .map(passkey -> new PasskeyVO(
            passkey.reference().toString(),
            passkey.label(),
            passkey.createdAt(),
            passkey.lastUsedAt(),
            PasskeyStateEnum.valueOf(passkey.status().name())))
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public PasskeyManagementResultVO rename(PasskeyRenameRequestDTO request) {
    if (request == null || request.correlationId() == null) {
      return result(PasskeyManagementStatusEnum.REJECTED);
    }
    ManagementContext context;
    UUID reference;
    String label;
    try {
      context = validateContext(request.context());
      reference = parseReference(request.passkeyReference());
      label = validateLabel(request.label());
    } catch (IllegalArgumentException invalid) {
      return result(PasskeyManagementStatusEnum.REJECTED);
    }
    if (!reauthenticationService.isRecentlyAuthorized(
        context.userId(),
        context.sessionReference(),
        ReauthenticationOperationEnum.RENAME_PASSKEY,
        context.occurredAt())) {
      return result(PasskeyManagementStatusEnum.ACCESS_DENIED);
    }
    try {
      passkeyService.rename(
          context.userId(),
          reference,
          label,
          request.correlationId(),
          context.occurredAt());
      return result(PasskeyManagementStatusEnum.COMPLETED);
    } catch (EntityNotFoundException | IllegalStateException stale) {
      return result(PasskeyManagementStatusEnum.STALE);
    } catch (IllegalArgumentException rejected) {
      return result(PasskeyManagementStatusEnum.REJECTED);
    }
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public PasskeyManagementResultVO revoke(PasskeyRevocationRequestDTO request) {
    if (request == null || request.correlationId() == null) {
      return result(PasskeyManagementStatusEnum.REJECTED);
    }
    ManagementContext context;
    UUID reference;
    try {
      context = validateContext(request.context());
      reference = parseReference(request.passkeyReference());
    } catch (IllegalArgumentException invalid) {
      return result(PasskeyManagementStatusEnum.REJECTED);
    }
    if (!reauthenticationService.isRecentlyAuthorized(
        context.userId(),
        context.sessionReference(),
        ReauthenticationOperationEnum.REVOKE_PASSKEY,
        context.occurredAt())) {
      return result(PasskeyManagementStatusEnum.ACCESS_DENIED);
    }
    try {
      FactorOperationStatusEnum status = passkeyService.revoke(
          context.userId(),
          reference,
          false,
          request.correlationId(),
          context.occurredAt());
      return result(switch (status) {
        case REVOKED -> PasskeyManagementStatusEnum.COMPLETED;
        case LAST_METHOD -> PasskeyManagementStatusEnum.LAST_METHOD;
        case ADMIN_FACTOR_REQUIRED -> PasskeyManagementStatusEnum.ADMIN_FACTOR_REQUIRED;
        default -> PasskeyManagementStatusEnum.REJECTED;
      });
    } catch (EntityNotFoundException | IllegalStateException stale) {
      return result(PasskeyManagementStatusEnum.STALE);
    }
  }

  private static ManagementContext validateContext(PasskeyManagementContextDTO context) {
    if (context == null || context.userId() <= 0 || context.occurredAt() == null) {
      throw new IllegalArgumentException("passkey management context is invalid");
    }
    return new ManagementContext(
        context.userId(),
        parseReference(context.currentSessionReference()),
        context.occurredAt());
  }

  private static UUID parseReference(String value) {
    try {
      return UUID.fromString(value);
    } catch (NullPointerException | IllegalArgumentException invalid) {
      throw new IllegalArgumentException("reference is invalid", invalid);
    }
  }

  private static String validateLabel(String value) {
    if (value == null) {
      throw new IllegalArgumentException("label is invalid");
    }
    String normalized = value.strip();
    if (normalized.isEmpty() || normalized.length() > 100) {
      throw new IllegalArgumentException("label is invalid");
    }
    return normalized;
  }

  private static PasskeyManagementResultVO result(PasskeyManagementStatusEnum status) {
    return new PasskeyManagementResultVO(status);
  }

  private record ManagementContext(long userId, UUID sessionReference,
      java.time.Instant occurredAt) {
  }
}
