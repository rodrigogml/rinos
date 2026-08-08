package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;

/**
 * Projeção segura do fator de e-mail sem repetir o endereço.
 *
 * @author Rodrigo Leitão
 */
public record EmailFactorSummaryVO(UUID reference, EmailFactorStatusEnum status,
    Instant activatedAt, Instant lastUsedAt) { }
