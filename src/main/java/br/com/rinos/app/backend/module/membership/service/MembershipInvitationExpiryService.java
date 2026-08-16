package br.com.rinos.app.backend.module.membership.service;
import java.time.Instant; import java.util.UUID;
import org.springframework.data.domain.PageRequest; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.api.module.membership.enums.MembershipInvitationStatus;
import br.com.rinos.app.backend.module.membership.entity.*; import br.com.rinos.app.backend.module.membership.repository.*;
import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;
@Service
@org.springframework.context.annotation.Lazy
public class MembershipInvitationExpiryService {
 private final MembershipInvitationRepository invitations; private final MembershipEventRepository events;
 private final MembershipOutboxEventRepository outbox; private final MembershipInvitationPropertiesConfig properties;
 public MembershipInvitationExpiryService(MembershipInvitationRepository invitations,MembershipEventRepository events,
   MembershipOutboxEventRepository outbox,MembershipInvitationPropertiesConfig properties){this.invitations=invitations;this.events=events;this.outbox=outbox;this.properties=properties;}
 @Transactional
 public int expireBatch(Instant cutoff){if(cutoff==null)throw new IllegalArgumentException("cutoff is required");
  var batch=invitations.findExpiredBatchForUpdate(MembershipInvitationStatus.PENDING,cutoff,PageRequest.of(0,properties.expiryBatchSize()));
  for(var invitation:batch){invitation.expire();outbox.cancelInvitationDelivery(invitation.getId());events.save(new MembershipEventEntity("INVITATION_EXPIRED",invitation.getAccountId(),
    null,invitation.getId(),"membership-expiry","expiry-"+UUID.randomUUID(),"COMPLETED",cutoff));
   outbox.save(new MembershipOutboxEventEntity(UUID.randomUUID(),"INVITATION",invitation.getId(),"INVITATION_EXPIRED",
    "{\"invitationPublicId\":\""+invitation.getPublicId()+"\"}"));}
  if(!batch.isEmpty()){invitations.flush();outbox.flush();}return batch.size();}
}
