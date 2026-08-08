package br.com.rinos.app.backend.module.identity.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;

/**
 * Calcula garantia sem transformar método de autenticação em authority.
 *
 * <p>Código por e-mail não eleva uma identidade Google do mesmo canal a MFA. Passkey somente é
 * phishing-resistant quando a cerimônia confirmou user verification.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
public class AuthenticationAssurancePolicyService {

  /** Retorna a maior garantia demonstrada pela fotografia imutável do fluxo. */
  public AuthenticationAssuranceEnum calculate(
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods) {
    Objects.requireNonNull(verifiedMethods, "verifiedMethods must not be null");
    if (verifiedMethods.isEmpty() || verifiedMethods.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("verifiedMethods must not be empty or contain null");
    }
    boolean phishingResistant = verifiedMethods.stream().anyMatch(method ->
        method.method() == AuthenticationMethodEnum.PASSKEY
            && Boolean.TRUE.equals(method.userVerification()));
    if (phishingResistant) {
      return AuthenticationAssuranceEnum.PHISHING_RESISTANT;
    }
    Set<AuthenticationMethodEnum> independent = EnumSet.noneOf(AuthenticationMethodEnum.class);
    verifiedMethods.forEach(method -> independent.add(method.method()));
    if (independent.contains(AuthenticationMethodEnum.GOOGLE)) {
      independent.remove(AuthenticationMethodEnum.EMAIL_CODE);
    }
    return independent.size() >= 2
        ? AuthenticationAssuranceEnum.MULTI_FACTOR
        : AuthenticationAssuranceEnum.SINGLE_FACTOR;
  }

  /** Indica se a garantia alcançada satisfaz a exigência do fluxo. */
  public boolean satisfies(
      AuthenticationAssuranceEnum achieved,
      AuthenticationAssuranceEnum required) {
    Objects.requireNonNull(achieved, "achieved must not be null");
    Objects.requireNonNull(required, "required must not be null");
    return rank(achieved) >= rank(required);
  }

  private static int rank(AuthenticationAssuranceEnum assurance) {
    return switch (assurance) {
      case SINGLE_FACTOR -> 1;
      case MULTI_FACTOR -> 2;
      case PHISHING_RESISTANT -> 3;
    };
  }
}
