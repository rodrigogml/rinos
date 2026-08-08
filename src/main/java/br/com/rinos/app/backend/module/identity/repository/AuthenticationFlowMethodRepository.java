package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowMethodEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import jakarta.persistence.LockModeType;

/**
 * Persiste e consulta a coleção fechada de métodos permitidos por fluxo.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationFlowMethodRepository
    extends JpaRepository<AuthenticationFlowMethodEntity, Long> {

  /** Retorna os métodos do fluxo em ordem estável. */
  List<AuthenticationFlowMethodEntity> findByFlowIdOrderByMethod(Long flowId);

  /** Bloqueia um método do fluxo depois que o fluxo proprietário já foi bloqueado. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT method
      FROM AuthenticationFlowMethodEntity method
      WHERE method.flow.id = :flowId
        AND method.method = :authenticationMethod
      """)
  Optional<AuthenticationFlowMethodEntity> findByFlowIdAndMethodForUpdate(
      @Param("flowId") Long flowId,
      @Param("authenticationMethod") AuthenticationMethodEnum authenticationMethod);
}
