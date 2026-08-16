package br.com.rinos.app.api.module.account.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.api.module.account.enums.AccountPublicStage;

public record AccountCreationStatus(
    UUID protocolId,
    UUID accountPublicId,
    String displayName,
    AccountPublicStage publicStage,
    String safeReasonCode,
    Instant acceptedAt,
    Instant updatedAt) {
}
