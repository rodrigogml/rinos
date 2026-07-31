package br.com.rinos.app.backend.module.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita exclusivamente os disparadores internos da manutenção global.
 *
 * <p>Os disparadores não concedem liderança: cada tarefa continua obrigada a comprová-la no
 * banco global antes do job e novamente dentro de cada lote.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MaintenanceSchedulingConfig {
}
