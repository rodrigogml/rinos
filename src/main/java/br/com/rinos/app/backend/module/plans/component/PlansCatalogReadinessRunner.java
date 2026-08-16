package br.com.rinos.app.backend.module.plans.component;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.plans.service.PlansCatalogReadinessService;

/** Valida os defaults de planos depois das migrations e antes dos demais catálogos. */
@Component
@ConditionalOnBean(DataSource.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlansCatalogReadinessRunner implements ApplicationRunner {

  private final PlansCatalogReadinessService readiness;

  public PlansCatalogReadinessRunner(PlansCatalogReadinessService readiness) {
    this.readiness = readiness;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    readiness.validate();
  }
}
