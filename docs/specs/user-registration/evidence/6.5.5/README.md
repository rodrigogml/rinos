# Evidência 6.5.5 — próximas jornadas após o cancelamento

## Resultado

O resultado terminal da confirmação de cancelamento oferece duas próximas jornadas:

- **Criar conta**, que abre um formulário de cadastro novo;
- **Voltar para entrar**, que retorna ao login.

As ações são apresentadas em conjunto somente quando um outcome `COMPLETED` foi produzido pela etapa tipada
`REGISTRATION_CANCELLATION_CONFIRMATION` e a capability de cadastro está disponível. Enquanto a confirmação
assíncrona está pendente, após uma rejeição ou em resultados de outras jornadas, o atalho de novo cadastro não é
renderizado.

O RFW preserva a etapa de origem do resultado por um campo próprio. Isso evita acoplamento a chaves de mensagens
definidas pela aplicação hospedeira e mantém o comportamento reutilizável. Código, testes e guia do showroom foram
atualizados no commit `dc93685` do submódulo.

## Validação

- RFW isolado: 317 testes, sem falhas ou erros;
- showroom isolado: 21 testes, sem falhas ou erros;
- integração Rinos: comprova ausência da ação durante o processamento e a dupla de ações após o cancelamento.
