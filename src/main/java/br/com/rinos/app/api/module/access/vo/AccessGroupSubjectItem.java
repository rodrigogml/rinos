package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;

/** Participação temporal de um sujeito em um grupo do mesmo contexto. */
public record AccessGroupSubjectItem(
    long id,
    long groupId,
    long subjectId,
    AccessAdministrationState state,
    Instant validFrom,
    Instant validUntil,
    long version) {
}
