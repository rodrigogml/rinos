package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;

/**
 * Projeção segura do conjunto sem hashes ou códigos brutos.
 *
 * @author Rodrigo Leitão
 */
public record RecoveryCodeSetSummaryVO(UUID reference, RecoveryCodeSetStatusEnum status,
    int availableCodes, Instant issuedAt) { }
