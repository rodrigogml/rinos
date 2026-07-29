package br.com.rinos.app.backend.module.platform.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

/**
 * Mantém a identidade efêmera da execução atual da aplicação.
 *
 * <p>O bean possui escopo singleton padrão do Spring: a sessão permanece estável durante o
 * contexto atual e um novo contexto cria outro UUID, mesmo quando o {@code instanceId} é igual.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class MaintenanceSessionService {

  private final MaintenanceSessionVO currentSession;

  /**
   * Cria a sessão exclusiva desta inicialização.
   *
   * @param properties propriedades validadas da coordenação de manutenção
   */
  public MaintenanceSessionService(MaintenancePropertiesConfig properties) {
    currentSession = new MaintenanceSessionVO(properties.instanceId(), UUID.randomUUID());
  }

  /**
   * Retorna a identidade imutável da execução atual.
   *
   * @return sessão criada durante a inicialização deste contexto
   */
  public MaintenanceSessionVO getCurrentSession() {
    return currentSession;
  }
}
