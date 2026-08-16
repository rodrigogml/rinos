package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("rinos.membership.invitation")
public record MembershipInvitationPropertiesConfig(
    @DefaultValue("15d") Duration validity,
    @DefaultValue("15m") Duration rateWindow,
    @DefaultValue("100") int accountLimit,
    @DefaultValue("20") int inviterLimit,
    @DefaultValue("5") int recipientLimit,
    @DefaultValue("50") int originLimit,
    @DefaultValue("100") int expiryBatchSize,
    @DefaultValue("25") int deliveryBatchSize,
    @DefaultValue("2m") Duration deliveryLease,
    @DefaultValue("1m") Duration deliveryRetryBase,
    @DefaultValue("1h") Duration deliveryRetryMaximum) {

  public MembershipInvitationPropertiesConfig {
    if (invalid(validity) || invalid(rateWindow) || accountLimit <= 0 || inviterLimit <= 0
        || recipientLimit <= 0 || originLimit <= 0 || expiryBatchSize <= 0
        || deliveryBatchSize <= 0 || invalid(deliveryLease) || invalid(deliveryRetryBase)
        || invalid(deliveryRetryMaximum) || deliveryRetryMaximum.compareTo(deliveryRetryBase) < 0) {
      throw new IllegalArgumentException("membership invitation properties are invalid");
    }
  }

  private static boolean invalid(Duration value) {
    return value == null || value.isZero() || value.isNegative();
  }
}
