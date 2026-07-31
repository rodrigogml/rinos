package br.com.rinos.app.backend.module.identity.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Rejeita localmente combinações notoriamente comuns antes de consultar serviço externo.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class CommonPasswordService {

  private static final List<String> COMMON_PASSWORDS = List.of(
      "password1!",
      "password123!",
      "qwerty123!",
      "admin123!",
      "welcome123!",
      "letmein123!",
      "iloveyou1!",
      "abc123456!",
      "123456789a!",
      "senha123!");

  /**
   * Compara sem criar uma cópia normalizada da senha.
   *
   * @param password senha efêmera
   * @return {@code true} quando a combinação está na lista local
   */
  public boolean isCommon(char[] password) {
    Objects.requireNonNull(password, "password must not be null");
    return COMMON_PASSWORDS.stream().anyMatch(candidate -> equalsIgnoreCase(password, candidate));
  }

  private static boolean equalsIgnoreCase(char[] password, String candidate) {
    if (password.length != candidate.length()) {
      return false;
    }
    for (int index = 0; index < password.length; index++) {
      if (Character.toLowerCase(password[index]) != candidate.charAt(index)) {
        return false;
      }
    }
    return true;
  }
}
