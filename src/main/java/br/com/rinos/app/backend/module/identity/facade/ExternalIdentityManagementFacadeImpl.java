package br.com.rinos.app.backend.module.identity.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.ExternalIdentityLinkRequestDTO;
import br.com.rinos.app.api.dto.ExternalIdentityManagementContextDTO;
import br.com.rinos.app.api.dto.ExternalIdentityUnlinkRequestDTO;
import br.com.rinos.app.api.enums.ExternalIdentityManagementStatusEnum;
import br.com.rinos.app.api.facade.ExternalIdentityManagementFacade;
import br.com.rinos.app.api.vo.ExternalIdentityManagementResultVO;
import br.com.rinos.app.api.vo.ExternalIdentityVO;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.service.ExternalIdentityManagementService;

/**
 * Valida o contrato público e traduz conflitos transacionais da identidade externa.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class ExternalIdentityManagementFacadeImpl implements ExternalIdentityManagementFacade {

  private final ExternalIdentityManagementService service;

  /**
   * Cria a fachada sobre a autoridade transacional.
   *
   * @param service gestão interna dos vínculos
   */
  public ExternalIdentityManagementFacadeImpl(ExternalIdentityManagementService service) {
    this.service = Objects.requireNonNull(service, "service must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public List<ExternalIdentityVO> list(ExternalIdentityManagementContextDTO context) {
    ManagementContext validated = context(context);
    return service.list(
        validated.userId(), validated.sessionReference(), validated.occurredAt()).stream()
        .map(identity -> new ExternalIdentityVO(
            identity.reference().toString(),
            identity.provider().name().toLowerCase(java.util.Locale.ROOT),
            identity.linkedAt(),
            identity.lastUsedAt()))
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public ExternalIdentityManagementResultVO link(ExternalIdentityLinkRequestDTO request) {
    if (request == null || request.correlationId() == null) {
      return result(ExternalIdentityManagementStatusEnum.REJECTED);
    }
    try {
      ManagementContext context = context(request.context());
      ExternalIdentityProviderEnum provider = provider(request.providerId());
      ExternalIdentityOperationStatusEnum status = service.link(
          context.userId(),
          context.sessionReference(),
          provider,
          request.issuer(),
          request.subject(),
          request.explicitlyConfirmed(),
          request.correlationId(),
          context.occurredAt());
      return result(map(status));
    } catch (DataIntegrityViolationException conflict) {
      return result(ExternalIdentityManagementStatusEnum.CONFLICT);
    } catch (IllegalArgumentException rejected) {
      return result(ExternalIdentityManagementStatusEnum.REJECTED);
    } catch (SecurityException denied) {
      return result(ExternalIdentityManagementStatusEnum.ACCESS_DENIED);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ExternalIdentityManagementResultVO unlink(ExternalIdentityUnlinkRequestDTO request) {
    if (request == null || request.correlationId() == null) {
      return result(ExternalIdentityManagementStatusEnum.REJECTED);
    }
    try {
      ManagementContext context = context(request.context());
      UUID reference = reference(request.externalIdentityReference());
      return result(map(service.unlink(
          context.userId(),
          context.sessionReference(),
          reference,
          request.correlationId(),
          context.occurredAt())));
    } catch (IllegalArgumentException rejected) {
      return result(ExternalIdentityManagementStatusEnum.REJECTED);
    } catch (SecurityException denied) {
      return result(ExternalIdentityManagementStatusEnum.ACCESS_DENIED);
    }
  }

  private static ManagementContext context(ExternalIdentityManagementContextDTO context) {
    if (context == null || context.userId() <= 0 || context.occurredAt() == null) {
      throw new IllegalArgumentException("external identity management context is invalid");
    }
    return new ManagementContext(
        context.userId(),
        reference(context.currentSessionReference()),
        context.occurredAt());
  }

  private static UUID reference(String value) {
    if (value == null) {
      throw new IllegalArgumentException("external identity reference is invalid");
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("external identity reference is invalid", invalid);
    }
  }

  private static ExternalIdentityProviderEnum provider(String providerId) {
    if (!"google".equals(providerId)) {
      throw new IllegalArgumentException("external identity provider is invalid");
    }
    return ExternalIdentityProviderEnum.GOOGLE;
  }

  private static ExternalIdentityManagementStatusEnum map(
      ExternalIdentityOperationStatusEnum status) {
    return switch (status) {
      case LINKED, ALREADY_LINKED, UNLINKED -> ExternalIdentityManagementStatusEnum.COMPLETED;
      case REJECTED -> ExternalIdentityManagementStatusEnum.REJECTED;
      case CONFLICT -> ExternalIdentityManagementStatusEnum.CONFLICT;
      case LAST_METHOD -> ExternalIdentityManagementStatusEnum.LAST_METHOD;
      case STALE -> ExternalIdentityManagementStatusEnum.STALE;
      case ACCESS_DENIED -> ExternalIdentityManagementStatusEnum.ACCESS_DENIED;
    };
  }

  private static ExternalIdentityManagementResultVO result(
      ExternalIdentityManagementStatusEnum status) {
    return new ExternalIdentityManagementResultVO(status);
  }

  private record ManagementContext(long userId, UUID sessionReference,
      java.time.Instant occurredAt) {
  }
}
