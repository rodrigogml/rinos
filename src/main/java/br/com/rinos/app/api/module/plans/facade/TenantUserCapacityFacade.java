package br.com.rinos.app.api.module.plans.facade;

import br.com.rinos.app.api.module.plans.dto.AssociationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationAcceptanceCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityReleaseRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;

/** Autoridade única para reserva e ocupação da franquia de usuários associados. */
public interface TenantUserCapacityFacade {

  TenantUserCapacityResult reserve(InvitationCapacityRequest request);

  TenantUserCapacityResult occupy(AssociationCapacityRequest request);

  TenantUserCapacityResult convert(InvitationAcceptanceCapacityRequest request);

  TenantUserCapacityResult releaseUnaccepted(InvitationCapacityReleaseRequest request);

  TenantUserCapacityResult inspect(TenantUserCapacityRequest request);
}
