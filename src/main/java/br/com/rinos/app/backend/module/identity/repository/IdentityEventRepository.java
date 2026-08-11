package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;

/**
 * Acessa o registro append-only dos eventos de identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface IdentityEventRepository extends JpaRepository<IdentityEventEntity, Long> {

  /**
   * Localiza um evento pela correlação técnica.
   *
   * @param correlationId UUID convertido em 16 bytes
   * @return eventos da mesma operação, em ordem de persistência
   */
  List<IdentityEventEntity> findByCorrelationIdOrderById(byte[] correlationId);

  /**
   * Lista eventos ainda relacionados a um usuário.
   *
   * @param userId identificador interno
   * @return eventos relacionados
   */
  List<IdentityEventEntity> findByUserId(Long userId);

  /**
   * Verifica se uma notificação do mesmo tipo já foi solicitada dentro do cooldown.
   *
   * @param userId identidade destinatária
   * @param eventType evento de notificação
   * @param occurredAfter início exclusivo da janela
   * @return {@code true} quando há solicitação recente
   */
  boolean existsByUserIdAndEventTypeAndOccurredAtAfter(
      Long userId,
      IdentityEventTypeEnum eventType,
      Instant occurredAfter);

  /**
   * Lista reemissões recentes de uma pendência para aplicar a janela móvel.
   *
   * @param registrationId identificador interno do cadastro
   * @param eventType tipo de evento contabilizado
   * @param occurredAfter limite temporal exclusivo da janela
   * @return eventos recentes em ordem cronológica
   */
  List<IdentityEventEntity>
      findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
          Long registrationId,
          IdentityEventTypeEnum eventType,
          Instant occurredAfter);

  /**
   * Localiza tombstones antigos de um tipo para política de retenção.
   *
   * @param eventType tipo de evento
   * @param occurredBefore limite temporal exclusivo
   * @return eventos minimizados elegíveis
   */
  List<IdentityEventEntity>
      findByEventTypeAndUserIsNullAndRegistrationIsNullAndOccurredAtBefore(
          IdentityEventTypeEnum eventType,
          Instant occurredBefore);

  /**
   * Exclui um lote de tombstones minimizados cujo prazo de retenção terminou.
   *
   * @param eventType tipo de tombstone autorizado
   * @param cutoff instante máximo elegível
   * @param batchSize limite da transação
   * @return quantidade removida
   */
  @Modifying
  @Query(value = """
      DELETE FROM identity_event
      WHERE eventType = :eventType
        AND idUser IS NULL
        AND idRegistration IS NULL
        AND occurredAt <= :cutoff
      ORDER BY occurredAt, id
      LIMIT :batchSize
      """, nativeQuery = true)
  int deleteTombstoneBatch(
      @Param("eventType") String eventType,
      @Param("cutoff") Instant cutoff,
      @Param("batchSize") int batchSize);

  /**
   * Minimiza a trilha temporária antes da remoção terminal autorizada da pendência.
   *
   * @param userId identidade que será removida
   * @param registrationId cadastro que será removido
   * @return eventos temporários eliminados
   */
  @Modifying
  @Query(value = """
      DELETE FROM identity_event
      WHERE idUser = :userId
         OR idRegistration = :registrationId
      """, nativeQuery = true)
  int deleteRelatedEventsForTerminalRemoval(
      @Param("userId") Long userId,
      @Param("registrationId") Long registrationId);
}
