package br.com.rinos.app.backend.module.identity.facade;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.facade.FounderTotpEnrollmentFacade;
import br.com.rinos.app.backend.module.identity.service.FounderTotpEnrollmentPolicyService;

/** Publica à interface apenas a decisão transitória de enrollment do fundador. */
@Service
@Lazy
public class FounderTotpEnrollmentFacadeImpl implements FounderTotpEnrollmentFacade {

  private final FounderTotpEnrollmentPolicyService policy;

  /** Cria a fachada sobre a política de segurança persistida. */
  public FounderTotpEnrollmentFacadeImpl(FounderTotpEnrollmentPolicyService policy) {
    this.policy = policy;
  }

  /** {@inheritDoc} */
  @Override
  public boolean requiresEnrollment(long userId) {
    return policy.requiresEnrollment(userId);
  }
}
