package br.com.rinos.app.backend.module.identity.vo;

/**
 * Inventário agregado sem identificadores ou material criptográfico.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationMethodInventoryVO(
    boolean localPassword,
    long activeExternalIdentities,
    long activePasskeys,
    long activeTotpFactors,
    boolean activeEmailFactor,
    long administrativePasskeys) {

  /**
   * Preserva a construção binária anterior quando o catálogo só distinguia presença de Google.
   *
   * @param localPassword presença de senha local
   * @param google presença de ao menos uma identidade Google
   * @param activePasskeys passkeys ativas
   * @param activeTotpFactors fatores TOTP ativos
   * @param activeEmailFactor fator de e-mail ativo
   * @param administrativePasskeys passkeys administrativas
   */
  public AuthenticationMethodInventoryVO(
      boolean localPassword,
      boolean google,
      long activePasskeys,
      long activeTotpFactors,
      boolean activeEmailFactor,
      long administrativePasskeys) {
    this(
        localPassword,
        google ? 1 : 0,
        activePasskeys,
        activeTotpFactors,
        activeEmailFactor,
        administrativePasskeys);
  }

  /**
   * Conta cada credencial que pode iniciar uma autenticação independentemente.
   *
   * @return quantidade de métodos iniciais utilizáveis
   */
  public long initialMethodCount() {
    return (localPassword ? 1 : 0) + activeExternalIdentities + activePasskeys;
  }

  /**
   * Conta fatores aceitos para exigências administrativas.
   *
   * @return quantidade de fatores administrativos
   */
  public long administrativeFactorCount() {
    return activeTotpFactors + administrativePasskeys;
  }
}
