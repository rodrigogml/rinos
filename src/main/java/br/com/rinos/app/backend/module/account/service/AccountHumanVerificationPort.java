package br.com.rinos.app.backend.module.account.service;
import java.util.UUID;
/** Fronteira fail-safe para validar Turnstile com action account-creation. */
public interface AccountHumanVerificationPort {
  AccountHumanVerificationResult verify(String token,String canonicalOrigin,UUID idempotencyKey);
}
