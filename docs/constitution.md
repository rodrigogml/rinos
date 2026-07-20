<!--
Sync Impact Report
- Version: inexistente -> 1.0.0
- Princípios criados:
  - Isolamento Multi-Tenant Inviolável
  - Autorização Explícita e Contextual
  - Integridade e Rastreabilidade dos Dados
  - Arquitetura Modular Baseada no RFW
  - Qualidade Antes de Escopo
- Seções adicionadas:
  - Restrições de Arquitetura e Segurança
  - Critérios de Qualidade e Entrega
- Seções removidas: nenhuma
- Artefatos verificados:
  - docs/briefing/20260717-briefing.md: alinhado
  - AGENTS.md: ausente; criar quando as instruções específicas do projeto forem formalizadas
  - docs/specs/*/plan.md: inexistentes; os futuros planos devem demonstrar conformidade
  - docs/specs/*/tasks.md: inexistentes; as futuras tarefas devem incluir os quality gates aplicáveis
- TODOs pendentes: nenhum para ratificação desta versão
-->

# Rinos Constitution

## Core Principles

### I. Isolamento Multi-Tenant Inviolável

Toda leitura, escrita, consulta, evento, cache, arquivo e operação assíncrona que trate dados de uma conta DEVE possuir um tenant explicitamente identificado e validado. Operações globais DEVEM usar um contexto de sistema distinto e não podem reutilizar implicitamente o contexto de uma conta. Cada funcionalidade que acesse dados de tenant DEVE possuir testes automatizados que demonstrem tanto o acesso permitido no tenant correto quanto a negação de acesso cruzado. Nenhuma otimização, integração ou conveniência de desenvolvimento pode enfraquecer essa separação.

### II. Autorização Explícita e Contextual

Todo acesso DEVE ser negado por padrão e liberado somente por grupos e chaves explícitos no contexto aplicável. Papéis como colaborador, parceiro externo, administrador da conta e administrador do sistema identificam atores, mas NÃO concedem permissões por si próprios. As decisões de autorização DEVEM considerar a identidade única do usuário, o contexto do sistema ou da conta e as concessões vigentes. Alterações de grupos, chaves e acessos sensíveis DEVEM produzir registros de auditoria rastreáveis.

### III. Integridade e Rastreabilidade dos Dados

Operações financeiras e administrativas DEVEM preservar consistência, precisão monetária e histórico suficiente para explicar o estado atual. Alterações sensíveis DEVEM registrar autor, contexto, instante e efeito produzido. Migrações de banco DEVEM ser versionadas, revisáveis e compatíveis com o mecanismo do RFW; falhas não podem ser ocultadas nem deixar a aplicação operando sobre uma versão de schema desconhecida. Integrações externas DEVEM definir idempotência, tratamento de repetição, reconciliação e rastreabilidade antes da implementação.

### IV. Arquitetura Modular Baseada no RFW

O RFW DEVE ser a fundação arquitetural da aplicação e permanecer integrado pelo submódulo `modules/RFW.Platform`. O código do Rinos DEVE ser organizado por funcionalidades e contextos de negócio, com contratos explícitos entre módulos e dependências direcionadas. Capacidades já fornecidas pelo RFW NÃO DEVEM ser duplicadas sem justificativa registrada. Desvios das convenções do RFW, mudanças estruturais relevantes ou adoção de outra fundação técnica exigem ADR, análise de compatibilidade e validação de build e testes.

### V. Qualidade Antes de Escopo

Segurança, testes e documentação são condições de entrega, não atividades opcionais posteriores. Toda mudança de comportamento DEVE incluir testes proporcionais ao risco, documentação correspondente e validação automatizada disponível. Nenhuma entrega pode ser considerada concluída com testes obrigatórios, build ou verificações de segurança falhando. Quando prazo e escopo conflitarem com esses critérios, o escopo DEVE ser reduzido ou postergado. A experiência do usuário e a acessibilidade DEVEM ser consideradas na especificação e na validação de toda interface.

## Restrições de Arquitetura e Segurança

- A stack-base é Java 25, Spring, Vaadin, MySQL 9 e RFW. Versões concretas DEVEM permanecer compatíveis com o RFW; alterações incompatíveis exigem ADR e plano de migração.
- A implantação inicial DEVE gerar um JAR executável para servidor Linux atrás de proxy reverso. Segredos e configurações específicas de ambiente NÃO DEVEM ser versionados.
- Persistência, cache, arquivos, filas e integrações DEVEM transportar e validar o contexto do tenant quando lidarem com dados de conta.
- Dados pessoais DEVEM ser tratados segundo os princípios da LGPD: finalidade, necessidade, controle de acesso, rastreabilidade e descarte definido.
- Dados completos de cartões NÃO DEVEM ser armazenados sem decisão arquitetural, análise de PCI-DSS e controles aprovados.
- Dependências e integrações externas DEVEM ter contratos, timeouts, tratamento de falhas e limites de responsabilidade documentados na respectiva especificação.
- Scripts incrementais de banco DEVEM seguir a convenção e o controle de versão estabelecidos pelo RFW. A inicialização DEVE falhar de forma explícita quando a versão do banco for inconsistente.

## Critérios de Qualidade e Entrega

Uma mudança somente está pronta quando todos os critérios aplicáveis abaixo forem atendidos:

1. Requisitos e critérios de aceitação estão rastreáveis até a implementação e os testes.
2. Testes unitários cobrem regras de negócio e casos extremos; testes de integração cobrem persistência, segurança, isolamento de tenant e integrações críticas.
3. Build, testes automatizados e verificações estáticas disponíveis passam sem falhas ocultadas.
4. Mudanças de comportamento, uso, arquitetura ou operação atualizam a documentação correspondente sem duplicar o código.
5. Interfaces são responsivas, navegáveis por teclado e avaliadas segundo o padrão de acessibilidade adotado pelo projeto.
6. Operações críticas produzem logs úteis sem expor credenciais, dados pessoais desnecessários ou informações financeiras sensíveis.
7. A entrega inclui estratégia de implantação, compatibilidade de banco e recuperação quando houver impacto operacional.

> [!IMPORTANT]
> A ordem de prioridade do projeto é: segurança, testes, documentação, acessibilidade, performance e observabilidade. Essa ordem orienta trade-offs, mas não autoriza ignorar um critério obrigatório desta constituição.

## Governance

Esta constituição prevalece sobre convenções locais, planos e tarefas do projeto. Toda especificação, plano técnico e backlog DEVEM indicar como atendem aos princípios aplicáveis. Revisões de código DEVEM verificar conformidade antes da integração.

Emendas exigem uma alteração explícita deste arquivo com justificativa, análise de impacto e atualização do `Sync Impact Report`. A versão segue SemVer:

- **MAJOR**: remoção ou redefinição incompatível de princípio ou regra de governança.
- **MINOR**: inclusão de princípio ou expansão material das obrigações.
- **PATCH**: esclarecimento ou correção sem mudança de significado.

Exceções temporárias somente são permitidas quando registradas em ADR com escopo, justificativa, riscos, responsável, controles compensatórios e condição ou data de encerramento. Uma exceção não pode eliminar isolamento multi-tenant, autorização por negação padrão nem obrigações legais.

A conformidade DEVE ser revisada sempre que uma feature for especificada, antes de iniciar sua implementação e antes de considerá-la concluída. Mudanças na constituição exigem sincronização dos artefatos afetados no mesmo ciclo ou registro explícito da pendência no relatório de impacto.

**Version**: 1.0.0 | **Ratified**: 2026-07-17 | **Last Amended**: 2026-07-17
