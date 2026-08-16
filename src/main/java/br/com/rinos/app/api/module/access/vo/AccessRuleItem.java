package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationOrigin;
import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;

/** Regra corrente administrativa com vigência e versão explícitas. */
public record AccessRuleItem(
    long id,
    AccessAdministrationOrigin origin,
    long originId,
    String accessKeyInternalReference,
    AccessRuleEffect effect,
    AccessAdministrationState state,
    Instant validFrom,
    Instant validUntil,
    long version) {
}
