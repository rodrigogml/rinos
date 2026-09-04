package br.com.rinos.app.backend.module.access.component;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import javax.sql.DataSource;

import br.com.rinos.app.backend.module.access.service.GlobalAccessBootstrapService;

/** Tenta o bootstrap após a sincronização do catálogo; ausência do candidato ainda é um estado normal. */
@Component
@ConditionalOnBean(DataSource.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class GlobalAccessBootstrapReadinessRunner implements ApplicationRunner {
  private final GlobalAccessBootstrapService bootstrap;

  public GlobalAccessBootstrapReadinessRunner(GlobalAccessBootstrapService bootstrap) {
    this.bootstrap = bootstrap;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    bootstrap.attempt(UUID.randomUUID(), Instant.now());
  }
}
