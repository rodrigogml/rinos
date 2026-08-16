package br.com.rinos.app.backend.module.membership.service;

import java.time.Instant;

import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;
import br.com.rinos.app.api.module.membership.enums.MembershipStatus;

public record MembershipContinuityRequest(
    long accountId,
    long tenantId,
    long affectedMembershipId,
    MembershipMutationOperation operation,
    MembershipStatus resultingStatus,
    MembershipRoleType resultingRole,
    Instant effectiveAt) {}
