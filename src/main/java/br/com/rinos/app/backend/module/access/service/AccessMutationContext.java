package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.enums.AccessScope;

public record AccessMutationContext(AccessScope scope, Long tenantId) {}
