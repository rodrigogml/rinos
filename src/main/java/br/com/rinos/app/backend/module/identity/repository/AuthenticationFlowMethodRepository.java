package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowMethodEntity;

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
}
