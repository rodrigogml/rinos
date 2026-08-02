# Cobertura quantitativa e lacunas comportamentais

Data da medição: 2026-08-02

## Método

O Maven usa JaCoCo 0.8.15, compatível com Java 25, para instrumentar Surefire e Failsafe na mesma execução. O
relatório é gerado por `mvn verify` em `target/site/jacoco/`. Não foi definido um percentual mínimo arbitrário:
cobertura de linha e branch localiza caminhos não exercidos, mas o fechamento da feature depende da matriz de
comportamentos e dos gates especializados.

Execução medida:

- 326 testes unitários aprovados;
- 70 testes integrados aprovados;
- 12 E2E opt-in ignorados no perfil padrão;
- 165 classes de produção analisadas.

## Resultado quantitativo

| Escopo | Linhas | Branches | Métodos |
|--------|-------:|---------:|---------:|
| Rinos completo | 89,0% (3.017/3.390) | 69,6% (931/1.338) | 90,0% (664/738) |
| Serviços de identidade | 91,5% (1.365/1.492) | 74,8% (427/571) | 95,1% (213/224) |
| Facades de identidade | 84,4% (353/418) | 67,2% (84/125) | 97,3% (72/74) |
| Adapters UI/config | 98,4% (182/185) | 90,2% (46/51) | 100% (31/31) |
| Componente de acesso do Rinos | 100% (45/45) | 86,4% (19/22) | 100% (12/12) |

Classes de maior risco apresentam a seguinte cobertura de linhas:

| Classe | Linhas | Observação |
|--------|-------:|------------|
| `RegistrationCreationService` | 100% | criação, commit e despacho posterior |
| `RegistrationResendService` | 100% | invalidação e nova prova |
| `OriginLimitService` | 100% | reserva e limite atômico |
| `GoogleIdentityResolutionService` | 98,3% | novo, pendente, ativo e conflito |
| `ExternalRegistrationCompletionService` | 98,2% | aceite e ativação Google |
| `RegistrationCancellationService` | 97,0% | emissão neutra e remoção |
| `RegistrationExpiryCleanupService` | 96,7% | expiração e exclusão coordenada |
| `RegistrationActivationService` | 92,9% | provas e alteração legal |
| `VerificationService` | 92,4% | emissão, inspeção, consumo e invalidação |
| `RegistrationStartFacadeImpl` | 84,3% | borda defensiva possui o maior conjunto de ramos não exercidos |

## Cobertura dos critérios e lacunas abertas

| Critérios | Situação | Lacuna ou gate proprietário |
|-----------|----------|-----------------------------|
| SC-UR-004, 005, 008–013, 015 e 016 | Cobertos automaticamente | Concorrência, provas, Turnstile, origem, Google, lease e cancelamento possuem testes unitários e MySQL |
| SC-UR-006 | Dependência aberta | Acesso autenticado somente ao Painel depende de `user-authentication`; tarefas 6.1.6, 7.5.1 e 7.5.2 |
| SC-UR-001 e 003 | Gate humano aberto | Dez participantes e taxa de sucesso; tarefas 7.3.5 e 7.3.6 |
| SC-UR-002 | Gate operacional aberto | Harness nominal de 100 cadastros e SMTP controlado; tarefas 7.4.1 e 7.4.2 |
| SC-UR-007 | Parcial | Automação, teclado e inspeção visual ainda passam por 7.3; leitor de tela real e participantes exigem execução humana |
| SC-UR-014 | Harness pronto, ambiente aberto | `PasswordHashCalibrationTool` fica intencionalmente com 0% no gate normal; a execução de 50 amostras pertence ao servidor-alvo em 7.5.6 |

As linhas não cobertas das facades concentram combinações defensivas, traduções de falha e retornos de
indisponibilidade. Os comportamentos de segurança que alteram persistência possuem cobertura nominal; ampliar os
ramos defensivos continua melhoria de qualidade, mas não substitui nem bloqueia os gates humanos, SMTP e de ambiente
listados acima.

> [!IMPORTANT]
> Esta tarefa mede e registra as lacunas; ela não declara a feature pronta para produção. Os gates externos continuam
> abertos e rastreáveis nas fases 7.3, 7.4 e 7.5.
