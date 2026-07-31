package br.com.rinos.app.backend.module.identity.service;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Produz a representação persistida e a chave comparável de um e-mail.
 *
 * <p>A normalização remove somente espaços externos e aplica lowercase com
 * {@link Locale#ROOT}. Regras específicas de provedores não são aplicadas.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class EmailNormalizationService {

  private static final int MAXIMUM_EMAIL_LENGTH = 320;

  /**
   * Normaliza um e-mail segundo o contrato global de identidade.
   *
   * @param email valor informado pela pessoa
   * @return valor de apresentação e chave normalizada
   * @throws NullPointerException quando o e-mail é nulo
   * @throws IllegalArgumentException quando o valor é vazio, excede 320 caracteres ou não
   *     representa um endereço estruturalmente válido
   */
  public NormalizedEmailVO normalize(String email) {
    Objects.requireNonNull(email, "email must not be null");
    String displayEmail = email.trim();
    if (displayEmail.isEmpty()) {
      throw new IllegalArgumentException("email must not be blank");
    }
    if (displayEmail.length() > MAXIMUM_EMAIL_LENGTH) {
      throw new IllegalArgumentException("email must not exceed 320 characters");
    }
    validateStructure(displayEmail);
    return new NormalizedEmailVO(displayEmail, displayEmail.toLowerCase(Locale.ROOT));
  }

  private static void validateStructure(String email) {
    if (email.codePoints().anyMatch(Character::isWhitespace)) {
      throw invalidEmail();
    }
    try {
      InternetAddress address = new InternetAddress(email, true);
      address.validate();
      if (!email.equals(address.getAddress())) {
        throw invalidEmail();
      }
    } catch (AddressException exception) {
      throw invalidEmail();
    }
  }

  private static IllegalArgumentException invalidEmail() {
    return new IllegalArgumentException("email must be structurally valid");
  }
}
