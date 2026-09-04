package br.com.rinos.app.backend.module.membership.component;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.membership.service.MembershipInvitationExpiryService;
import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(javax.sql.DataSource.class)
public class MembershipInvitationExpiryScheduler {
  private final MembershipInvitationExpiryService expiry;
  private final MembershipInvitationPropertiesConfig properties;
  private final Clock clock = Clock.systemUTC();

  public MembershipInvitationExpiryScheduler(
      MembershipInvitationExpiryService expiry,
      MembershipInvitationPropertiesConfig properties) {
    this.expiry = expiry;
    this.properties = properties;
  }

  @Scheduled(
      fixedDelayString = "${rinos.membership.invitation-expiry-interval:1h}",
      initialDelayString = "${rinos.membership.invitation-expiry-initial-delay:1h}")
  public void expire() {
    while (expiry.expireBatch(clock.instant()) == properties.expiryBatchSize()) {
      // Drena lotes limitados sem manter uma transação longa.
    }
  }
}
