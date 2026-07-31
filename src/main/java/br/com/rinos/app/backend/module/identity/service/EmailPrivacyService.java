package br.com.rinos.app.backend.module.identity.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Produz representações minimizadas de e-mail para superfícies públicas.
 *
 * <p>O domínio completo é preservado para reconhecimento do destino, enquanto o local nunca
 * é devolvido integralmente.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-30
 */
@Service
public class EmailPrivacyService {

  /**
   * Mascara o local do endereço sem alterar o e-mail persistido.
   *
   * @param email endereço estruturalmente válido
   * @return representação no formato {@code p***@example.com}
   * @throws NullPointerException quando o endereço é nulo
   * @throws IllegalArgumentException quando o endereço não possui local e domínio
   */
  public String maskForPublicDisplay(String email) {
    Objects.requireNonNull(email, "email must not be null");
    int separator = email.lastIndexOf('@');
    if (separator <= 0 || separator != email.indexOf('@')
        || separator == email.length() - 1) {
      throw new IllegalArgumentException("email must contain one local and one domain");
    }

    String local = email.substring(0, separator);
    String domain = email.substring(separator);
    int localLength = local.codePointCount(0, local.length());
    if (localLength <= 2) {
      return "***" + domain;
    }
    int firstCharacterEnd = local.offsetByCodePoints(0, 1);
    return local.substring(0, firstCharacterEnd) + "***" + domain;
  }
}
