package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.vo.VerifiedReauthenticationProofVO;

/**
 * Catálogo dos verificadores de prova realmente implementados para reautenticação.
 *
 * <p>Um método somente pode aparecer no desafio quando estiver ativo para a identidade e também
 * possuir um verificador neste serviço. TOTP e passkey serão acrescentados junto das respectivas
 * cerimônias, sem anunciar antecipadamente uma prova impossível.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class ReauthenticationProofService {

  private static final Set<AuthenticationMethodEnum> SUPPORTED_METHODS =
      Set.of(AuthenticationMethodEnum.PASSWORD);

  private final PasswordCredentialAuthenticationService passwordAuthenticationService;

  /** Cria o catálogo com o verificador local de senha. */
  public ReauthenticationProofService(
      PasswordCredentialAuthenticationService passwordAuthenticationService) {
    this.passwordAuthenticationService = Objects.requireNonNull(
        passwordAuthenticationService, "passwordAuthenticationService must not be null");
  }

  /** @return fotografia imutável dos métodos que podem concluir uma prova agora */
  public Set<AuthenticationMethodEnum> supportedMethods() {
    return SUPPORTED_METHODS;
  }

  /**
   * Valida a prova transitória para a identidade autenticada sem persistir seu conteúdo.
   *
   * @param userId identidade da sessão corrente
   * @param method método explicitamente escolhido
   * @param proof prova efêmera recebida do renderer RFW
   * @param verifiedAt instante UTC da tentativa
   * @return evidência sanitizada somente quando o verificador real aceitou a prova
   */
  public Optional<VerifiedReauthenticationProofVO> verify(
      Long userId,
      AuthenticationMethodEnum method,
      String proof,
      Instant verifiedAt) {
    if (method == null || proof == null || proof.isBlank() || verifiedAt == null) {
      return Optional.empty();
    }
    if (method != AuthenticationMethodEnum.PASSWORD) {
      return Optional.empty();
    }
    return passwordAuthenticationService.verifyUser(userId, proof.toCharArray(), verifiedAt)
        ? Optional.of(new VerifiedReauthenticationProofVO(method, null))
        : Optional.empty();
  }
}
