# Checklist de Segurança e Isolamento — Controle de Acesso

**Objetivo**: validar que a especificação falha fechada e preserva isolamento multi-tenant.

- [x] Ausência de regra permissiva nega acesso. {auto}
- [x] Bloqueio vigente vence permissões do mesmo contexto e chave. {auto}
- [x] Regra global não se propaga a tenant e regra tenant não se propaga a outro contexto. {auto}
- [x] Categoria não possui efeito de herança ou bloqueio. {auto}
- [x] Chave inativa, futura, expirada ou de escopo incompatível não autoriza. {auto}
- [x] Identidade bloqueada invalida todos os contextos sem ser bloqueio de chave. {auto}
- [x] Falha de cache, revisão ou persistência nega a operação. {auto}
- [x] Cache revisionado evita snapshot anterior após mutação confirmada. {auto}
- [x] Snapshot é particionado por sujeito e contexto e nunca representa ACL completa do tenant ou decisão final. {auto}
- [x] Início ou término de vigência invalida o uso do resultado anterior mesmo sem mutação. {auto}
- [x] Evento de invalidação perdido, atrasado ou fora de ordem não contorna a revisão persistida. {auto}
- [x] Sessão e principal não armazenam tenant ativo compartilhado, chaves ou decisões efetivas. {auto}
- [x] UI não substitui verificação de serviço. {auto}
- [x] Explicação não revela dados de outro tenant. {auto}
- [x] Dados completos usam chaves separadas da consulta mascarada. {auto}
- [x] Mutação administrativa registra auditoria sem segredo de autenticação. {auto}
- [x] Continuidade administrativa é validada na mesma transação da mutação. {auto}
- [x] Grupo protegido não pode bloquear ou perder baseline mínima. {auto}
- [x] Bootstrap exige identidade ativa, e-mail confirmado e TOTP ou passkey. {auto}
- [x] Trabalho assíncrono reautoriza antes de iniciar. {auto}
- [x] Operação sistêmica não simula usuário nem integra grupo humano. {auto}
- [x] Acessibilidade não depende de cor ou de ponteiro. {humano}

## Resultado

Checklist aprovado para o planejamento inicial.

## Follow-up de implementação — 2026-09-02

A revisão de segurança da implementação está registrada em `../evidence/6.3.3/README.md` e foi executada contra a
suíte completa de testes e integrações MySQL. O item humano de acessibilidade acima não possui ainda evidência manual
reproduzível de teclado, foco e leitor de tela; por isso a tarefa `6.3.4` permanece aberta. Essa pendência não autoriza
reduzir as verificações de serviço, contexto, bloqueio ou falha fechada já cobertas pelos testes automatizados.
