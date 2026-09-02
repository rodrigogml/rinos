package br.com.rinos.app.api.facade;

/**
 * Consulta a restrição transitória do primeiro administrador global.
 *
 * <p>A fachada não revela o e-mail configurado nem a razão da seleção. A interface a utiliza
 * somente para limitar a navegação da própria sessão até a confirmação do TOTP.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
public interface FounderTotpEnrollmentFacade {

  /**
   * Indica se a identidade ainda deve concluir o enrollment TOTP antes de navegar no sistema.
   *
   * @param userId identidade global autenticada
   * @return {@code true} somente enquanto o fator TOTP fundador estiver ausente
   */
  boolean requiresEnrollment(long userId);
}
