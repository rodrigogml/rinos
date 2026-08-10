package br.com.rinos.app.backend.module.identity.facade;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.RecoveryCodeGenerationRequestDTO;
import br.com.rinos.app.api.enums.RecoveryCodeGenerationStatusEnum;
import br.com.rinos.app.api.facade.RecoveryCodeManagementFacade;
import br.com.rinos.app.api.vo.RecoveryCodeGenerationResultVO;
import br.com.rinos.app.backend.module.identity.service.RecoveryCodeService;
import br.com.rinos.app.backend.module.identity.vo.IssuedRecoveryCodeSetVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Publica a geração de recovery codes sem expor hashes ou entidades persistidas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class RecoveryCodeManagementFacadeImpl implements RecoveryCodeManagementFacade {

  private final RecoveryCodeService service;

  /** Cria a fachada sobre a autoridade transacional dos códigos. */
  public RecoveryCodeManagementFacadeImpl(RecoveryCodeService service) {
    this.service = service;
  }

  /** {@inheritDoc} */
  @Override
  public RecoveryCodeGenerationResultVO generate(RecoveryCodeGenerationRequestDTO request) {
    if (request == null || request.userId() <= 0 || request.correlationId() == null
        || request.occurredAt() == null) {
      return RecoveryCodeGenerationResultVO.terminal(
          RecoveryCodeGenerationStatusEnum.REJECTED);
    }
    try {
      IssuedRecoveryCodeSetVO issued = service.generate(
          request.userId(), request.correlationId(), request.occurredAt());
      return new RecoveryCodeGenerationResultVO(
          RecoveryCodeGenerationStatusEnum.GENERATED,
          issued.reference().toString(),
          issued.issuedAt(),
          issued.codes());
    } catch (EntityNotFoundException | SecurityException denied) {
      return RecoveryCodeGenerationResultVO.terminal(
          RecoveryCodeGenerationStatusEnum.ACCESS_DENIED);
    } catch (RuntimeException unavailable) {
      return RecoveryCodeGenerationResultVO.terminal(
          RecoveryCodeGenerationStatusEnum.UNAVAILABLE);
    }
  }
}
