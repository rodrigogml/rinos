package br.com.rinos.app.backend.module.access.service;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.dto.UserInitiatedAuthorizationJob;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;

/** Reautoriza um trabalho originado por usuário imediatamente antes do primeiro efeito. */
@Service
@org.springframework.context.annotation.Lazy
public class UserInitiatedJobAuthorizationService {

  private final AuthorizationFacade authorization;

  public UserInitiatedJobAuthorizationService(AuthorizationFacade authorization) {
    this.authorization = authorization;
  }

  public AuthorizationDecision requireStart(UserInitiatedAuthorizationJob job) {
    Objects.requireNonNull(job, "job must not be null");
    AuthorizationRequest request = new AuthorizationRequest(
        job.actor(), job.workspace().membershipId(), job.workspace().context(),
        job.operation().code(), job.operation().requiredKeys(), job.submittedAssurance(),
        job.operation().sensitive(), AuthorizationExplanationMode.NONE);
    return authorization.require(request);
  }

  /** Reautoriza e inicia o primeiro efeito na mesma chamada do executor. */
  public <T> T execute(
      UserInitiatedAuthorizationJob job, Supplier<T> firstEffect) {
    Objects.requireNonNull(firstEffect, "firstEffect must not be null");
    requireStart(job);
    return firstEffect.get();
  }
}
