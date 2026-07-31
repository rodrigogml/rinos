package br.com.rinos.app.backend.module.identity.service;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.PasswordPreparationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordValidationResultVO;

/**
 * Encadeia validação e Argon2id e apaga o array de senha em todos os resultados.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class PasswordPreparationService {

  private final PasswordPolicyService policyService;
  private final PasswordEncoder passwordEncoder;

  /**
   * Cria o preparador da credencial local.
   *
   * @param policyService política completa
   * @param passwordEncoder encoder Argon2id identificado
   */
  public PasswordPreparationService(
      PasswordPolicyService policyService,
      PasswordEncoder passwordEncoder) {
    this.policyService = policyService;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Valida, codifica quando permitido e sempre sobrescreve a entrada fornecida.
   *
   * @param password array de propriedade transferida ao método
   * @return resultado protegido para persistência ou apresentação das violações
   */
  public PasswordPreparationResultVO prepare(char[] password) {
    Objects.requireNonNull(password, "password must not be null");
    try {
      PasswordValidationResultVO validation = policyService.validate(password);
      if (!validation.accepted()) {
        return new PasswordPreparationResultVO(validation, null);
      }
      String encodedHash = passwordEncoder.encode(CharBuffer.wrap(password));
      return new PasswordPreparationResultVO(validation, encodedHash);
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
