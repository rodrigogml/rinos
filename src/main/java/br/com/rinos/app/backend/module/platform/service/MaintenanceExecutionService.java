package br.com.rinos.app.backend.module.platform.service;

import java.time.Duration;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

/**
 * Impõe a barreira de liderança antes de jobs e lotes globais exclusivos.
 *
 * <p>Cada lote recebe uma transação nova e limitada pelo timeout operacional. A primeira operação
 * dessa transação comprova novamente o lease; o trabalho somente é chamado depois dessa prova.
 * Sua criação é tardia para não antecipar consumidores JPA aos diagnósticos de inicialização.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class MaintenanceExecutionService {

  private static final String BATCH_TRANSACTION_NAME = "maintenance-batch";

  private final MaintenanceLeaseService leaseService;
  private final TransactionTemplate batchTransaction;

  /**
   * Cria a barreira com transações independentes e o timeout definido em properties.
   *
   * @param leaseService serviço que comprova o lease no banco global
   * @param transactionManager gerenciador da transação do banco global
   * @param properties propriedades tipadas da coordenação
   * @throws ArithmeticException quando o timeout excede a representação em segundos do Spring
   */
  public MaintenanceExecutionService(
      MaintenanceLeaseService leaseService,
      PlatformTransactionManager transactionManager,
      MaintenancePropertiesConfig properties) {
    this.leaseService = leaseService;
    batchTransaction = new TransactionTemplate(transactionManager);
    batchTransaction.setName(BATCH_TRANSACTION_NAME);
    batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    batchTransaction.setTimeout(toTimeoutSeconds(properties.batchTransactionTimeout()));
  }

  /**
   * Revalida a liderança imediatamente antes de avaliar ou iniciar um job.
   *
   * @param expectedLease token vigente conhecido pela coordenadora
   * @return {@code true} somente quando a nova prova no banco autoriza o início
   * @throws NullPointerException quando o token é nulo
   */
  public boolean canStartJob(MaintenanceLeaseVO expectedLease) {
    return leaseService.provesStableOwnership(
        Objects.requireNonNull(expectedLease, "expectedLease must not be null"));
  }

  /**
   * Executa um lote somente depois de nova prova, dentro da mesma transação limitada.
   *
   * <p>Retorna {@code false} sem chamar o lote quando a prova falha. Exceções do lote ou da
   * infraestrutura são propagadas para que o gerenciador transacional reverta seus efeitos.
   *
   * @param expectedLease token vigente conhecido pela coordenadora
   * @param batch trabalho atômico e idempotente do lote
   * @return {@code true} quando o lote foi chamado e a transação confirmou; {@code false} quando a
   *     prova impediu sua execução
   * @throws NullPointerException quando algum argumento é nulo
   */
  public boolean executeBatch(MaintenanceLeaseVO expectedLease, Runnable batch) {
    Objects.requireNonNull(expectedLease, "expectedLease must not be null");
    Objects.requireNonNull(batch, "batch must not be null");
    Boolean executed = batchTransaction.execute(status -> {
      if (!leaseService.provesStableOwnership(expectedLease)) {
        return Boolean.FALSE;
      }
      batch.run();
      return Boolean.TRUE;
    });
    return Boolean.TRUE.equals(executed);
  }

  private static int toTimeoutSeconds(Duration timeout) {
    return Math.toIntExact(timeout.toSeconds());
  }
}
