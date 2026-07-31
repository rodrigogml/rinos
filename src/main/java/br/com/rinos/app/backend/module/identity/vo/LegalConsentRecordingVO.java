package br.com.rinos.app.backend.module.identity.vo;

/**
 * Resume uma gravação idempotente de decisões legais.
 *
 * @param recorded quantidade de novas evidências
 * @param alreadyExisting quantidade de decisões idênticas já persistidas
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record LegalConsentRecordingVO(int recorded, int alreadyExisting) {
}
