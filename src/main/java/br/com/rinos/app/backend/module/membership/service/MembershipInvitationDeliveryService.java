package br.com.rinos.app.backend.module.membership.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import br.com.rinos.app.api.module.membership.enums.MembershipInvitationStatus;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringService;
import br.com.rinos.app.backend.module.identity.service.PublicApplicationUriService;
import br.com.rinos.app.backend.module.membership.repository.MembershipInvitationRepository;
import br.com.rinos.app.backend.module.membership.repository.MembershipOutboxEventRepository;
import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;

/** Despacha convites duráveis sem persistir nem registrar a prova em texto claro. */
@Service
@org.springframework.context.annotation.Lazy
public class MembershipInvitationDeliveryService {

  static final String SECRET_DOMAIN = "membership/invitation-delivery/v1";
  private static final Logger LOGGER = LoggerFactory.getLogger(MembershipInvitationDeliveryService.class);

  private final MembershipOutboxEventRepository outbox;
  private final MembershipInvitationRepository invitations;
  private final AuthenticationKeyringService keyring;
  private final PublicApplicationUriService uris;
  private final EmailDispatchService emails;
  private final MembershipInvitationPropertiesConfig properties;
  private final TransactionTemplate transactions;

  public MembershipInvitationDeliveryService(
      MembershipOutboxEventRepository outbox,
      MembershipInvitationRepository invitations,
      AuthenticationKeyringService keyring,
      PublicApplicationUriService uris,
      EmailDispatchService emails,
      MembershipInvitationPropertiesConfig properties,
      PlatformTransactionManager transactionManager) {
    this.outbox = outbox;
    this.invitations = invitations;
    this.keyring = keyring;
    this.uris = uris;
    this.emails = emails;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public int dispatchBatch(Instant now, String owner) {
    if (now == null || owner == null || owner.isBlank() || owner.length() > 100) {
      throw new IllegalArgumentException("invitation dispatcher context is invalid");
    }
    List<Delivery> deliveries = transactions.execute(status -> outbox
        .findDispatchableForUpdate(now, PageRequest.of(0, properties.deliveryBatchSize()))
        .stream()
        .map(event -> {
          event.claim(owner, now.plus(properties.deliveryLease()));
          return new Delivery(event.getEventId(), event.getAggregateId(), event.encryptedSecret());
        })
        .toList());
    if (deliveries == null) return 0;
    deliveries.forEach(delivery -> dispatch(delivery, now));
    return deliveries.size();
  }

  private void dispatch(Delivery delivery, Instant now) {
    byte[] plaintext = null;
    try {
      var invitation = invitations.findById(delivery.invitationId()).orElse(null);
      if (invitation == null || invitation.getStatus() != MembershipInvitationStatus.PENDING
          || !now.isBefore(invitation.getExpiresAt())) {
        finish(delivery.eventId(), now, false, true);
        return;
      }
      plaintext = keyring.decrypt(SECRET_DOMAIN, delivery.secret());
      String proof = new String(plaintext, StandardCharsets.UTF_8);
      var uri = uris.membershipInvitationUri(invitation.getPublicId(), proof);
      var message = emails.createMessage(
          "membership-invitation",
          Locale.forLanguageTag("pt-BR"),
          null,
          List.of(invitation.getNormalizedEmail()),
          List.of(),
          List.of(),
          null,
          HtmlUtils.htmlEscape(uri.toASCIIString()),
          formatExpiry(invitation.getExpiresAt()));
      emails.dispatch(message);
      finish(delivery.eventId(), now, true, false);
    } catch (Exception failure) {
      LOGGER.warn("Despacho de convite falhou: eventId={}, failureType={}",
          delivery.eventId(), failure.getClass().getSimpleName());
      finish(delivery.eventId(), now, false, false);
    } finally {
      if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
    }
  }

  private void finish(UUID eventId, Instant now, boolean published, boolean cancelled) {
    transactions.executeWithoutResult(status -> outbox.findByEventIdForUpdate(eventId).ifPresent(event -> {
      if (!"PROCESSING".equals(event.getStatus())) return;
      if (published) event.publish(now);
      else if (cancelled || event.getSecretExpiresAt() == null || !now.isBefore(event.getSecretExpiresAt())) event.cancel();
      else event.retry(now.plus(retryDelay(event.getAttemptCount())));
    }));
  }

  private Duration retryDelay(int attemptCount) {
    long multiplier = 1L << Math.min(attemptCount, 6);
    Duration candidate = properties.deliveryRetryBase().multipliedBy(multiplier);
    return candidate.compareTo(properties.deliveryRetryMaximum()) > 0
        ? properties.deliveryRetryMaximum() : candidate;
  }

  private static String formatExpiry(Instant expiresAt) {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.of("UTC"))
        .format(expiresAt);
  }

  private record Delivery(
      UUID eventId,
      long invitationId,
      br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO secret) {}
}
