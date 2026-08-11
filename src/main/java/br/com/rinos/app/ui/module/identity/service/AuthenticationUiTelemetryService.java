package br.com.rinos.app.ui.module.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.eng.rodrigogml.rfw.ui.access.RFWAccessTelemetryEvent;

/**
 * Registra somente eventos agregáveis e sanitizados da jornada de acesso.
 * O serviço não recebe identificadores, conteúdo de campos, tokens ou endereços de origem.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
@Service
public class AuthenticationUiTelemetryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationUiTelemetryService.class);

  /**
   * Registra um evento de interface sem interromper a jornada principal.
   *
   * @param event evento sanitizado do RFW
   */
  public void record(RFWAccessTelemetryEvent event) {
    if (event != null) {
      LOGGER.debug("authentication-ui eventType={} step={} status={}",
          event.type(), event.step(), event.status());
    }
  }
}
