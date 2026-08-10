package br.com.rinos.app.backend.module.identity.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

/**
 * Seleciona fatores adicionais implementados e independentes do primeiro fator corrente.
 *
 * <p>A política não concede autorização. Ela apenas impede combinações sem independência, como
 * Google seguido de código enviado ao mesmo e-mail, e não anuncia capacidades cujo verificador
 * ainda não esteja disponível na aplicação. A passkey pode provar um segundo fator sem, por sua
 * presença isolada, ativar o 2FA voluntário.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class AuthenticationSecondFactorPolicyService {

  private static final Set<AuthenticationMethodEnum> PERMITTED_FACTORS = Set.of(
      AuthenticationMethodEnum.TOTP,
      AuthenticationMethodEnum.EMAIL_CODE,
      AuthenticationMethodEnum.RECOVERY_CODE,
      AuthenticationMethodEnum.PASSKEY);

  private static final Set<AuthenticationMethodEnum> VOLUNTARY_MFA_FACTORS = Set.of(
      AuthenticationMethodEnum.TOTP,
      AuthenticationMethodEnum.EMAIL_CODE,
      AuthenticationMethodEnum.RECOVERY_CODE);

  private static final Set<AuthenticationMethodEnum> ADMINISTRATIVE_FACTORS = Set.of(
      AuthenticationMethodEnum.TOTP,
      AuthenticationMethodEnum.PASSKEY);

  /**
   * Recalcula o catálogo utilizável para o primeiro fator informado.
   *
   * @param primaryMethod método que iniciou o fluxo
   * @param availableMethods métodos atualmente utilizáveis pela identidade
   * @return fatores adicionais suportados e independentes
   */
  public Set<AuthenticationMethodEnum> permittedMethods(
      AuthenticationMethodEnum primaryMethod,
      Set<AuthenticationMethodEnum> availableMethods) {
    if (primaryMethod == null || availableMethods == null
        || availableMethods.stream().anyMatch(java.util.Objects::isNull)) {
      return Set.of();
    }
    EnumSet<AuthenticationMethodEnum> permitted = EnumSet.copyOf(PERMITTED_FACTORS);
    permitted.retainAll(availableMethods);
    permitted.remove(primaryMethod);
    if (primaryMethod == AuthenticationMethodEnum.GOOGLE) {
      permitted.remove(AuthenticationMethodEnum.EMAIL_CODE);
    }
    return Set.copyOf(permitted);
  }

  /**
   * Informa se a identidade configurou algum segundo fator já suportado.
   *
   * @param availableMethods métodos atualmente utilizáveis
   * @return {@code true} quando o login deve exigir garantia multifator
   */
  public boolean requiresMultiFactor(Set<AuthenticationMethodEnum> availableMethods) {
    return availableMethods != null
        && availableMethods.stream().anyMatch(VOLUNTARY_MFA_FACTORS::contains);
  }

  /**
   * Restringe uma elevação administrativa a TOTP ou passkey com verificação local.
   *
   * <p>A feature de autorização continua responsável por identificar o ator administrativo e
   * solicitar esta política. Código por e-mail e recovery code não constituem a garantia normal
   * exigida para exercer esse acesso.</p>
   *
   * @param primaryMethod primeiro fator já comprovado
   * @param availableMethods métodos atualmente utilizáveis pela identidade
   * @return fatores administrativos independentes disponíveis
   */
  public Set<AuthenticationMethodEnum> administrativePermittedMethods(
      AuthenticationMethodEnum primaryMethod,
      Set<AuthenticationMethodEnum> availableMethods) {
    if (primaryMethod == null || availableMethods == null
        || availableMethods.stream().anyMatch(java.util.Objects::isNull)) {
      return Set.of();
    }
    EnumSet<AuthenticationMethodEnum> permitted = EnumSet.copyOf(ADMINISTRATIVE_FACTORS);
    permitted.retainAll(availableMethods);
    permitted.remove(primaryMethod);
    return Set.copyOf(permitted);
  }
}
