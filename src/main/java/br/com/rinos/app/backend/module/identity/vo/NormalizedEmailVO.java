package br.com.rinos.app.backend.module.identity.vo;

/**
 * Representa o e-mail preservado para apresentação e sua chave normalizada.
 *
 * @param email valor sem espaços externos
 * @param normalizedEmail chave usada para comparação e unicidade
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record NormalizedEmailVO(String email, String normalizedEmail) {

  /**
   * Produz representação segura sem nenhuma forma do endereço de e-mail.
   *
   * @return descrição redigida
   */
  @Override
  public String toString() {
    return "NormalizedEmailVO[email=REDACTED, normalizedEmail=REDACTED]";
  }
}
