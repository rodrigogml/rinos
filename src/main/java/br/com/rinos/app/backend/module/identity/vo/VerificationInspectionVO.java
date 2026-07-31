package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;

/**
 * Resultado interno da inspeção bloqueável de uma prova, sem expor seu token ou hash.
 *
 * @param status estado seguro da prova
 * @param registration cadastro relacionado quando a chave foi localizada
 * @param expiresAt validade persistida quando localizada
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record VerificationInspectionVO(
    VerificationConsumptionStatusEnum status,
    RegistrationEntity registration,
    Instant expiresAt) {
}
