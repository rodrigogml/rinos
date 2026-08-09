package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.dto.AuthenticationSessionPreparationRequestDTO;
import br.com.rinos.app.api.vo.AuthenticationSessionLifecycleResultVO;

/**
 * Fronteira pública do lifecycle transacional usado pela integração RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface AuthenticationSessionLifecycleFacade {

  AuthenticationSessionLifecycleResultVO prepare(
      AuthenticationSessionPreparationRequestDTO request);

  AuthenticationSessionLifecycleResultVO publish(String sessionReference, Instant occurredAt);

  AuthenticationSessionLifecycleResultVO validate(String sessionReference, Instant occurredAt);

  void abort(String sessionReference, Instant occurredAt);

  void close(String sessionReference, Instant occurredAt);
}
