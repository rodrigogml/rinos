package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define os prazos e limites fixos da recuperação de senha local.
 *
 * @param validity validade da prova opaca
 * @param requestLimit emissões máximas por usuário e origem na janela
 * @param requestWindow janela de emissão
 * @param attemptLimit tentativas máximas por origem na janela
 * @param attemptWindow janela de tentativa
 * @param retention retenção máxima das provas encerradas
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@ConfigurationProperties("rinos.password-recovery")
public record PasswordRecoveryPropertiesConfig(
    @DefaultValue("1h") Duration validity,
    @DefaultValue("3") int requestLimit,
    @DefaultValue("15m") Duration requestWindow,
    @DefaultValue("20") int attemptLimit,
    @DefaultValue("15m") Duration attemptWindow,
    @DefaultValue("30d") Duration retention) {

  /**
   * Rejeita configurações incapazes de proteger o fluxo.
   */
  public PasswordRecoveryPropertiesConfig {
    requirePositive(validity, "validity");
    requirePositive(requestWindow, "requestWindow");
    requirePositive(attemptWindow, "attemptWindow");
    requirePositive(retention, "retention");
    if (requestLimit <= 0 || attemptLimit <= 0) {
      throw new IllegalArgumentException("password recovery limits must be positive");
    }
  }

  private static void requirePositive(Duration duration, String name) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
