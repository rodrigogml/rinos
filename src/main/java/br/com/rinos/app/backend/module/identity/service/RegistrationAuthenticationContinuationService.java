package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.vo.RegistrationAuthenticationContinuationVO;
import br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;

/**
 * Emite a única continuação autenticável criada depois da ativação de um cadastro.
 *
 * <p>O método comprovado é a credencial que originou o cadastro, e não a prova de e-mail em si.
 * Assim, a revalidação do lifecycle consulta um método efetivamente disponível na identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
@Service
@Lazy
public class RegistrationAuthenticationContinuationService {

  private final AuthenticationFlowService flows;
  private final AuthenticationSessionPropertiesConfig sessions;

  /** Cria o emissor sobre o catálogo persistente de fluxos. */
  public RegistrationAuthenticationContinuationService(
      AuthenticationFlowService flows,
      AuthenticationSessionPropertiesConfig sessions) {
    this.flows = flows;
    this.sessions = sessions;
  }

  /**
   * Emite uma continuação curta, de uso único e não persistente para a identidade recém-ativada.
   *
   * @param user identidade global já ativa
   * @param registrationMethod origem comprovada do cadastro
   * @param correlationId correlação da ativação
   * @param occurredAt instante UTC da ativação
   * @return principal e fluxo opaco prontos para o lifecycle RFW
   */
  @Transactional
  public RegistrationAuthenticationContinuationVO issue(
      UserEntity user,
      RegistrationMethodEnum registrationMethod,
      UUID correlationId,
      Instant occurredAt) {
    if (user == null || user.getId() == null || user.getId() <= 0
        || registrationMethod == null || correlationId == null || occurredAt == null) {
      throw new IllegalArgumentException("registration activation continuation is incomplete");
    }
    AuthenticationMethodEnum method = registrationMethod == RegistrationMethodEnum.LOCAL
        ? AuthenticationMethodEnum.PASSWORD : AuthenticationMethodEnum.GOOGLE;
    IssuedAuthenticationFlowVO issued = flows.issue(
        user.getId(),
        br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum
            .REGISTRATION_ACTIVATION,
        method,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(method),
        false,
        occurredAt,
        occurredAt.plus(sessions.reauthenticationValidity()),
        correlationId);
    return new RegistrationAuthenticationContinuationVO(
        new RinosUserPrincipalVO(user.getId(), user.getEmail()),
        new RinosAuthenticationCompletionVO(
            issued.reference(), AuthenticationFlowPurposeEnum.REGISTRATION_ACTIVATION));
  }
}
