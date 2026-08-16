package br.com.rinos.app.api.module.access.vo;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;

/** Resumo editável de um grupo do contexto corrente. */
public record AccessGroupItem(
    long id,
    String name,
    String description,
    AccessAdministrationState state,
    boolean protectedGroup,
    Integer baselineVersion,
    long version,
    long subjectCount) {
}
