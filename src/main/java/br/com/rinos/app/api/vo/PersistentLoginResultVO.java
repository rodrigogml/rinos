package br.com.rinos.app.api.vo;

import br.com.rinos.app.api.enums.PersistentLoginStatusEnum;

/**
 * Resultado público da restauração persistente sem transportar material autenticador.
 *
 * @param status decisão terminal da credencial apresentada
 * @param principal identidade vigente, presente somente quando restaurada
 * @param sessionReference referência não autenticadora, presente somente quando restaurada
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record PersistentLoginResultVO(
    PersistentLoginStatusEnum status,
    RinosUserPrincipalVO principal,
    String sessionReference) {

  public PersistentLoginResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    boolean restored = status == PersistentLoginStatusEnum.RESTORED;
    if (restored != (principal != null && sessionReference != null && !sessionReference.isBlank())) {
      throw new IllegalArgumentException(
          "principal and sessionReference must be present only for RESTORED");
    }
  }

  /** Evita expor identidade e referência em diagnósticos. */
  @Override
  public String toString() {
    return "PersistentLoginResultVO[status=" + status
        + ", principal=<redacted>, sessionReference=<redacted>]";
  }
}
