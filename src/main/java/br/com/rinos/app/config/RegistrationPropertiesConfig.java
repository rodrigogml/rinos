package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define retenção e limitações operacionais do ciclo de cadastro.
 *
 * @param pendingRetention prazo para excluir cadastros que nunca foram ativados
 * @param resendLimit quantidade máxima de reenvios dentro da janela
 * @param resendWindow janela móvel usada para limitar reenvios
 * @param cancellationRequestLimit quantidade máxima de provas de cancelamento emitidas na janela
 * @param cancellationRequestWindow janela móvel usada para limitar provas de cancelamento
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.registration")
public record RegistrationPropertiesConfig(
    @DefaultValue("15d") Duration pendingRetention,
    @DefaultValue("3") int resendLimit,
    @DefaultValue("15m") Duration resendWindow,
    @DefaultValue("3") int cancellationRequestLimit,
    @DefaultValue("15m") Duration cancellationRequestWindow) {

  /**
   * Rejeita limites sem significado operacional.
   */
  public RegistrationPropertiesConfig {
    if (pendingRetention == null || pendingRetention.isNegative() || pendingRetention.isZero()) {
      throw new IllegalArgumentException("pendingRetention deve ser maior que zero.");
    }
    if (resendLimit <= 0) {
      throw new IllegalArgumentException("resendLimit deve ser maior que zero.");
    }
    if (resendWindow == null || resendWindow.isNegative() || resendWindow.isZero()) {
      throw new IllegalArgumentException("resendWindow deve ser maior que zero.");
    }
    if (cancellationRequestLimit <= 0) {
      throw new IllegalArgumentException("cancellationRequestLimit deve ser maior que zero.");
    }
    if (cancellationRequestWindow == null
        || cancellationRequestWindow.isNegative()
        || cancellationRequestWindow.isZero()) {
      throw new IllegalArgumentException("cancellationRequestWindow deve ser maior que zero.");
    }
  }
}
