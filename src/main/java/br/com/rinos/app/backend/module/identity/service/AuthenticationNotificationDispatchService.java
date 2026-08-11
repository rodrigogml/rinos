package br.com.rinos.app.backend.module.identity.service;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.AuthenticationNotificationRequestedEvent;
import br.eng.rodrigogml.rfw.exception.RFWInfrastructureException;
import br.eng.rodrigogml.rfw.exception.RFWIntegrationException;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailMessage;

/**
 * Renderiza e envia notificações de segurança somente após o evento transacional ser publicado.
 *
 * <p>Os templates desta etapa não recebem parâmetros e não incluem segredos, IP, digest ou
 * identificadores de sessão. Falhas ficam observáveis sem reverter a operação original.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
@Service
@Lazy
public class AuthenticationNotificationDispatchService {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      AuthenticationNotificationDispatchService.class);

  private final EmailDispatchService emailDispatchService;

  public AuthenticationNotificationDispatchService(EmailDispatchService emailDispatchService) {
    this.emailDispatchService = emailDispatchService;
  }

  /**
   * Envia a notificação já liberada pelo commit da transação de origem.
   *
   * @param event solicitação sanitizada
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void dispatch(AuthenticationNotificationRequestedEvent event) {
    try {
      EmailMessage message = emailDispatchService.createMessage(
          event.template().getTemplateName(),
          Locale.ROOT,
          null,
          List.of(event.user().getEmail()),
          List.of(),
          List.of(),
          null);
      emailDispatchService.dispatch(message);
      LOGGER.info(
          "Notificação de segurança enviada: correlationId={}, template={}",
          event.correlationId(), event.template());
    } catch (RFWInfrastructureException | RFWIntegrationException | RuntimeException failure) {
      LOGGER.warn(
          "Falha no envio de notificação de segurança: correlationId={}, template={}, failureType={}",
          event.correlationId(), event.template(), failure.getClass().getSimpleName());
    }
  }
}
