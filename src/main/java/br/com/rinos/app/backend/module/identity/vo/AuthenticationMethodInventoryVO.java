package br.com.rinos.app.backend.module.identity.vo;

/**
 * Inventário agregado sem identificadores ou material criptográfico.
 *
 * @author Rodrigo Leitão
 */
public record AuthenticationMethodInventoryVO(
    boolean localPassword,
    boolean google,
    long activePasskeys,
    long activeTotpFactors,
    boolean activeEmailFactor,
    long administrativePasskeys) {
  public long initialMethodCount() { return (localPassword ? 1 : 0) + (google ? 1 : 0) + activePasskeys; }
  public long administrativeFactorCount() { return activeTotpFactors + administrativePasskeys; }
}
