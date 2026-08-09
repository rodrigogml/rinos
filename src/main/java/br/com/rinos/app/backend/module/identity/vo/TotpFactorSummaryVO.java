package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;

/**
 * Projeção segura de um fator TOTP para listagem.
 *
 * @author Rodrigo Leitão
 */
public record TotpFactorSummaryVO(UUID reference, String label, TotpFactorStatusEnum status,
    Instant createdAt, Instant confirmedAt, Instant lastUsedAt) { }
