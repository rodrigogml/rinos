package br.com.rinos.app.backend.module.membership.component;

import java.time.Clock;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.membership.service.MembershipInvitationDeliveryService;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(javax.sql.DataSource.class)
public class MembershipInvitationDeliveryScheduler {
  private final MembershipInvitationDeliveryService delivery;
  private final Clock clock = Clock.systemUTC();
  private final String owner = UUID.randomUUID().toString();

  public MembershipInvitationDeliveryScheduler(MembershipInvitationDeliveryService delivery) {
    this.delivery = delivery;
  }

  @Scheduled(
      fixedDelayString = "${rinos.membership.invitation-delivery-interval:30s}",
      initialDelayString = "${rinos.membership.invitation-delivery-initial-delay:30s}")
  public void dispatch() {
    delivery.dispatchBatch(clock.instant(), owner);
  }
}
