package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.backend.module.access.enums.GlobalAccessBootstrapStatus;

public record GlobalAccessBootstrapResult(
    GlobalAccessBootstrapStatus status, Long administratorUserId, Long protectedGroupId) {
}
