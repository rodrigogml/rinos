package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;

/**
 * Projeção de gestão que não expõe credential ID, chave ou attestation.
 *
 * @author Rodrigo Leitão
 */
public record PasskeyCredentialSummaryVO(UUID reference, String label,
    PasskeyCredentialStatusEnum status, Instant createdAt, Instant lastUsedAt) { }
