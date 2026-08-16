package br.com.rinos.app.backend.module.access.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessKeyCategoryEntity;

/** Persistência das categorias canônicas do catálogo. */
public interface AccessKeyCategoryRepository extends JpaRepository<AccessKeyCategoryEntity, Long> {
  Optional<AccessKeyCategoryEntity> findByCode(String code);
  List<AccessKeyCategoryEntity> findAllByOrderByCodeAsc();
}
