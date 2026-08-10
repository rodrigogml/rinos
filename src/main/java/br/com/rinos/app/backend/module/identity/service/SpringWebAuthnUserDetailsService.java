package br.com.rinos.app.backend.module.identity.service;

import java.util.Base64;
import java.util.Collections;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

/**
 * Resolve somente usuários ativos para a conclusão interna do provider WebAuthn do Spring.
 *
 * <p>A senha sintética é aleatória a cada leitura e nunca é exposta. Ela impede que a presença deste contrato
 * técnico crie uma credencial reutilizável no provider de senha padrão do Spring.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class SpringWebAuthnUserDetailsService implements UserDetailsService {

  private final UserRepository users;
  private final EmailNormalizationService emailNormalization;

  /** Cria o resolvedor sobre a identidade global. */
  public SpringWebAuthnUserDetailsService(
      UserRepository users,
      EmailNormalizationService emailNormalization) {
    this.users = users;
    this.emailNormalization = emailNormalization;
  }

  /** {@inheritDoc} */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String normalized;
    try {
      normalized = emailNormalization.normalize(username).normalizedEmail();
    } catch (RuntimeException invalid) {
      throw notFound();
    }
    UserEntity user = users.findByNormalizedEmailAndStatus(normalized, UserStatusEnum.ACTIVE)
        .orElseThrow(SpringWebAuthnUserDetailsService::notFound);
    byte[] randomPassword = new byte[32];
    SecureRandomHolder.INSTANCE.nextBytes(randomPassword);
    return User.withUsername(user.getEmail())
        .password("{noop}" + Base64.getEncoder().encodeToString(randomPassword))
        .authorities(Collections.emptyList())
        .build();
  }

  private static UsernameNotFoundException notFound() {
    return new UsernameNotFoundException("Active WebAuthn user was not found");
  }

  /** Holder evita inicialização do gerador quando o adapter WebAuthn não for utilizado. */
  private static final class SecureRandomHolder {
    private static final java.security.SecureRandom INSTANCE = new java.security.SecureRandom();

    private SecureRandomHolder() {
    }
  }
}
