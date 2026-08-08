package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;

/**
 * Persiste os métodos que efetivamente compuseram uma sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthSessionMethodRepository
    extends JpaRepository<AuthSessionMethodEntity, Long> {

  /** Retorna as evidências sanitizadas na ordem em que foram verificadas. */
  List<AuthSessionMethodEntity> findBySessionIdOrderByFactorOrder(Long sessionId);
}
