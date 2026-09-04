# Quickstart: Controle de Acesso por Grupos e Chaves

Os cenários abaixo são critérios end-to-end do módulo. Eles não descrevem procedimentos de produção.

## Bootstrap global

Configure `rinos.access.bootstrap.administrator-email` no `application.properties` antes da primeira conclusão. O
padrão é `admin@rinos.com.br`. A propriedade apenas seleciona o candidato enquanto o marcador está
`NEVER_COMPLETED`: o usuário ainda precisa concluir a ativação normal e registrar TOTP ou passkey compatível. Depois
de `COMPLETED`, trocar a propriedade não concede, transfere nem remove acesso.

## Cenário 1: Registrar uma chave de módulo

1. O contributor de `party-registration` publica `tenant.party.view` com descriptor completo.
2. O registry global sincroniza a chave, categoria `tenant.parties`, textos i18n e referências `FR-PTY-*`.
3. Um administrador abre o catálogo do tenant.
4. **Esperado**: encontra “Consultar pessoas” por nome; o código técnico não aparece; tentativa de contributor com mesmo
   código e semântica divergente falha a readiness.

## Cenário 2: Permissão por grupo

1. Em um tenant ativo, administrador cria o grupo “Cadastros”.
2. Registra regra `PERMITIR` para “Consultar pessoas”.
3. Associa participante ativo ao grupo.
4. O participante consulta uma pessoa no mesmo tenant.
5. **Esperado**: decisão permitida por origem de grupo; o mesmo participante não consulta pessoa em outro tenant.

## Cenário 3: Bloqueio direto vence grupo

1. O participante do cenário anterior continua no grupo permissivo.
2. Administrador cria regra direta `BLOQUEAR` para a mesma chave e associação de tenant.
3. O participante repete a consulta.
4. **Esperado**: decisão negada; explicação administrativa mostra a permissão do grupo e o bloqueio direto decisivo.
5. A explicação é solicitada por `AccessExplanationRequest`: o consulente precisa de `tenant.access.explain` naquela
   mesma conta e garantia corrente; informar modo administrativo não concede esse acesso.

## Cenário 4: Bloqueio de grupo vence permissão direta

1. Administrador cria regra direta `PERMITIR` para “Criar lançamento”.
2. Associa o participante a grupo que contém `BLOQUEAR` para “Criar lançamento”.
3. O participante tenta criar lançamento.
4. **Esperado**: negação sem lançamento parcial e bloqueio de grupo como motivo decisivo.

## Cenário 5: Vigência e contexto

1. Criar permissão de tenant com início amanhã e bloqueio em tenant diferente.
2. Avaliar hoje e amanhã no tenant correto.
3. **Esperado**: hoje a permissão futura não produz efeito; amanhã ela pode permitir; o bloqueio do outro tenant nunca
   participa da decisão.

## Cenário 6: Operação composta

1. A transferência declara as chaves de transferência, das duas contas e dos custos adicionais aplicáveis.
2. O usuário recebe permissões para todas exceto uma conta de destino.
3. Confirma a transferência.
4. **Esperado**: operação inteira é negada antes de qualquer efeito. A explicação lista o resultado de cada chave.

## Cenário 7: Continuidade administrativa

1. Um tenant possui apenas um administrador mínimo apto com TOTP.
2. Esse administrador tenta inserir bloqueio em uma de suas chaves mínimas ou remover seu fator forte.
3. **Esperado**: transação é rejeitada; grupo, regra, fator e revisão permanecem inalterados; auditoria registra a
   tentativa conforme política.

## Cenário 8: Trabalho assíncrono revogado

1. Usuário solicita importação de extrato; a intenção entra na fila.
2. Antes de iniciar, administrador remove a permissão ou adiciona bloqueio.
3. Executor tenta iniciar o trabalho.
4. **Esperado**: executor reautoriza, encontra a mudança de revisão e não inicia a importação.
5. O executor usa `UserInitiatedJobAuthorizationService.execute(...)`; o callback do primeiro efeito não é chamado
   quando a nova decisão nega.

## Cenário 9: Falha segura do cache

1. Simular indisponibilidade da tabela de revisão ou carregamento do snapshot.
2. Solicitar operação protegida.
3. **Esperado**: decisão negada, sem efeito de negócio, com telemetria e motivo seguro para diagnóstico.

## Cenário 10: Duas áreas de trabalho e invalidação revisionada

1. O mesmo usuário autenticado abre duas áreas de trabalho: tenant A e tenant B.
2. Cada área executa uma operação e forma snapshot próprio por associação e tenant.
3. No tenant A, uma regra de grupo é alterada; a revisão A é incrementada e a notificação remota é simulada como perdida.
4. A área B continua no tenant B; a área A tenta nova operação.
5. **Esperado**: B não muda; A lê a revisão persistida, descarta o snapshot anterior e aplica a nova regra. Nenhuma
   chave ou decisão estava armazenada na sessão compartilhada.

## Cenário 11: Fronteira temporal sem mutação

1. Carregar snapshot com permissão que termina em instante conhecido e bloqueio que começa depois.
2. Avaliar antes, exatamente na fronteira e depois dela sem alterar dados nem revisão.
3. **Esperado**: início inclusivo e término exclusivo são respeitados; o snapshot não congela o resultado anterior e
   nenhuma tarefa periódica é necessária para retirar acesso.
