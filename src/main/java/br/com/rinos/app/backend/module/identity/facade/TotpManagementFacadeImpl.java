package br.com.rinos.app.backend.module.identity.facade;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.TotpEnrollmentCancellationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentConfirmationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentRequestDTO;
import br.com.rinos.app.api.enums.TotpEnrollmentStatusEnum;
import br.com.rinos.app.api.facade.TotpManagementFacade;
import br.com.rinos.app.api.vo.TotpEnrollmentResultVO;
import br.com.rinos.app.api.vo.TotpFactorVO;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.TotpFactorService;
import br.com.rinos.app.backend.module.identity.vo.IssuedTotpEnrollmentVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Converte contratos públicos em operações TOTP transacionais sem expor entities à interface.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class TotpManagementFacadeImpl implements TotpManagementFacade {

  private final TotpFactorService service;

  /** Cria a fachada sobre a autoridade TOTP. */
  public TotpManagementFacadeImpl(TotpFactorService service) {
    this.service = service;
  }

  /** {@inheritDoc} */
  @Override
  public TotpEnrollmentResultVO begin(TotpEnrollmentRequestDTO request) {
    if (request == null || request.userId() <= 0 || request.occurredAt() == null) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.REJECTED);
    }
    try {
      IssuedTotpEnrollmentVO issued = service.begin(request.userId(), request.occurredAt());
      return new TotpEnrollmentResultVO(
          TotpEnrollmentStatusEnum.PENDING,
          issued.reference().toString(),
          issued.expiresAt(),
          issued.provisioningUri(),
          issued.manualSecret());
    } catch (EntityNotFoundException | SecurityException denied) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.ACCESS_DENIED);
    }
  }

  /** {@inheritDoc} */
  @Override
  public TotpEnrollmentResultVO confirm(TotpEnrollmentConfirmationDTO request) {
    UUID reference = request == null ? null : parse(request.enrollmentReference());
    if (request == null || request.userId() <= 0 || reference == null
        || request.proof() == null || request.proof().isBlank() || request.occurredAt() == null) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.REJECTED);
    }
    try {
      var status = service.confirm(
          request.userId(), reference, request.proof(), UUID.randomUUID(), request.occurredAt());
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.valueOf(status.name()));
    } catch (EntityNotFoundException | SecurityException denied) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.ACCESS_DENIED);
    }
  }

  /** {@inheritDoc} */
  @Override
  public TotpEnrollmentResultVO cancel(TotpEnrollmentCancellationDTO request) {
    UUID reference = request == null ? null : parse(request.enrollmentReference());
    if (request == null || request.userId() <= 0 || reference == null
        || request.occurredAt() == null) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.REJECTED);
    }
    try {
      FactorOperationStatusEnum status = service.cancel(
          request.userId(), reference, request.occurredAt());
      return TotpEnrollmentResultVO.terminal(
          status == FactorOperationStatusEnum.REVOKED
              ? TotpEnrollmentStatusEnum.STALE : TotpEnrollmentStatusEnum.REJECTED);
    } catch (EntityNotFoundException | SecurityException denied) {
      return TotpEnrollmentResultVO.terminal(TotpEnrollmentStatusEnum.ACCESS_DENIED);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<TotpFactorVO> listActive(long userId) {
    return service.listActive(userId).stream()
        .map(factor -> new TotpFactorVO(
            factor.reference().toString(),
            factor.label(),
            factor.createdAt(),
            factor.lastUsedAt()))
        .toList();
  }

  private static UUID parse(String reference) {
    try {
      return reference == null || reference.isBlank() ? null : UUID.fromString(reference);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }
}
