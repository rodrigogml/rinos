package br.com.rinos.app.api.module.account.vo;

import java.time.Duration;
import java.util.UUID;

import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.enums.AccountPublicStage;

public record AccountCreationResult(
    AccountCreationResultStatus status,
    UUID protocolId,
    UUID accountPublicId,
    AccountPublicStage publicStage,
    String safeReasonCode,
    Duration retryAfter) {
}
