# Research: Cadastro e Ciclo Inicial do Usuário

Documento produzido no Phase 0 do plano de `user-registration`. As decisões abaixo resolvem as escolhas técnicas necessárias antes do desenho.

## Decision 1: Identidade global e persistência

**Decision**: persistir o usuário, seu cadastro temporário, credenciais, identidades externas, consentimentos e auditoria no schema global. Nenhuma dessas entidades recebe `tenantId` nem depende de contexto de tenant.

**Rationale**: a mesma identidade deve existir antes de qualquer conta e depois participar de múltiplos tenants. Manter a raiz da identidade no global evita duplicidade, dependência circular e acesso implícito a dados de conta.

**Alternatives considered**:

- Criar um usuário por tenant: rejeitada porque viola a identidade global única.
- Criar um tenant pessoal durante o cadastro: rejeitada. A decisão de 2026-08-16 cria somente contrato global
  `PERSONAL/FREE`, sem tenant, conta empresarial, associação, direito implícito ou permissão.

## Decision 2: Convergência, concorrência e idempotência

**Decision**: criar a identidade global já no estado `PENDING_VERIFICATION` e manter um processo `Registration` 1:1. A unicidade do e-mail normalizado será garantida no banco, e os casos de uso de início, reenvio, ativação e cancelamento serão transacionais e idempotentes. Colisões concorrentes serão resolvidas relendo o registro vencedor, nunca por uma verificação prévia isolada.

**Rationale**: uma consulta “existe?” seguida de inserção não protege contra duas solicitações simultâneas. A constraint única é a autoridade final, enquanto a aplicação converte a colisão no resultado funcional previsto.

**Alternatives considered**:

- Manter o cadastro somente em sessão ou cache: rejeitada porque não permite retomada em outro dispositivo e perde estado em reinicializações.
- Criar uma nova tentativa para cada solicitação: rejeitada porque duplica identidade, aceites e comprovações.

## Decision 3: Senhas locais

**Decision**: aplicar a política funcional antes da persistência, consultar senhas comprometidas pela API gratuita Pwned Passwords usando k-anonimato e armazenar somente hash Argon2id por meio do `PasswordEncoder` do Spring Security, com identificador de algoritmo e parâmetros no valor persistido. O piso será de 19.456 KiB de memória, duas iterações, paralelismo um, salt de 16 bytes e hash de 32 bytes. Antes da liberação em cada novo perfil de servidor de produção, uma ferramenta reproduzível executará aquecimento e no mínimo 50 medições; memória ou iterações serão ajustadas sem reduzir o piso até obter mediana entre 500 ms e um segundo e percentil 95 de até 1,5 segundo.

A calibração não ocorrerá automaticamente durante a inicialização. Os parâmetros efetivos serão definições exclusivas do `application.properties`, documentadas no `application.properties.model`, e o resultado será registrado no checklist operacional do [README do projeto](../../../README.md). Hashes existentes permanecem verificáveis com os parâmetros codificados no próprio valor; eventual atualização após autenticação pertence a `user-authentication`.

Nenhuma senha, hash SHA-1 completo ou sufixo comparado será registrado em logs ou auditoria. Se o serviço de senhas comprometidas estiver indisponível, o cadastro local falhará fechado com orientação para tentar novamente; o cadastro Google continuará disponível.

**Rationale**: a política exige rejeitar senhas comuns ou comprometidas. A consulta por prefixo envia somente os cinco primeiros caracteres do SHA-1 e compara o restante localmente; a senha completa nunca sai do processo. Argon2id oferece armazenamento não recuperável e o prefixo do encoder permite evolução futura do algoritmo.

**Alternatives considered**:

- Apenas regras de composição: rejeitada porque não detecta credenciais conhecidamente comprometidas.
- Enviar a senha ou o hash completo a um serviço: rejeitada por exposição desnecessária.
- Manter uma cópia local integral da base de comprometimentos no MVP: rejeitada pelo custo operacional de atualização e armazenamento.

## Decision 4: Comprovação do e-mail

**Decision**: emitir um token opaco de alta entropia para um link HTTPS, persistindo somente seu hash SHA-256, estado, emissão e expiração. O token será de uso único, válido por 24 horas; nova emissão invalida as anteriores. A mesma prova de 256 bits é exibida no e-mail como código copiável para a entrada manual, sem criar um código curto ou um segundo segredo. A aplicação Rinos será responsável pelo significado, persistência e política, reutilizando os contratos de entrega e o serviço de e-mail do RFW.

O registro pendente e a comprovação serão confirmados na mesma transação. O envio SMTP ocorrerá diretamente pelo serviço de e-mail do RFW após o commit, com timeout explícito e sem outbox ou retentativa automática no primeiro incremento. Se o envio falhar ou o processo for interrompido entre o commit e o dispatch, o cadastro permanece retomável e o reenvio solicitado pela pessoa gera uma nova comprovação. Token, URL secreta e mensagem renderizada não serão persistidos para envio posterior.

**Rationale**: um segredo aleatório suficientemente longo pode ser transportado em link sem pedir digitação e pode ser validado por comparação de hash. Separar persistência de entrega impede que uma falha SMTP desfaça ou duplique o cadastro.

**Alternatives considered**:

- Persistir o token em texto recuperável: rejeitada porque uma leitura indevida do banco permitiria ativação.
- Usar somente o gerador numérico padrão do RFW: rejeitada para este fluxo público porque exige controles adicionais de tentativas e oferece menor entropia que um token opaco.
- Usar uma fila/outbox no primeiro incremento: adiada; além da complexidade operacional, o transporte durável do link exigiria persistir um segredo recuperável ou definir criptografia e rotação de chave. O reenvio solicitado pela pessoa satisfaz o requisito atual sem expor o token.

## Decision 5: Documentos legais e consentimentos

**Decision**: manter no global um catálogo versionado de documentos legais com período de vigência e registrar um consentimento imutável por usuário, versão e finalidade. O cadastro resolve as versões vigentes no momento da confirmação; se mudarem desde o envio inicial, a ativação é interrompida até novo aceite.

**Rationale**: armazenar apenas um booleano não demonstra qual texto foi aceito. A versão e o instante são necessários para rastreabilidade e para reapresentar alterações ocorridas durante um cadastro pendente.

**Alternatives considered**:

- Gravar apenas a versão atual no usuário: rejeitada porque perde o histórico de aceites.
- Copiar o conteúdo integral em cada aceite: rejeitada por duplicação; a versão imutável do documento é a fonte da verdade.

## Decision 6: Cloudflare Turnstile e controle por origem

**Decision**: reutilizar `RFWTurnstileComponent`, `RFWHumanVerificationProvider` e `RFWTurnstileVerificationService` já fornecidos pelo RFW Platform. O Rinos permanece responsável pela política que decide quando exigir o desafio e pelos limites por origem. O token será validado no servidor antes de qualquer persistência, com verificação de `hostname`, contexto/action, timeout limitado e sem persistência do token.

Os limites por IP usarão janelas persistidas no schema global. O endereço validado pela política de proxy será normalizado e armazenado diretamente em formato binário na tabela compartilhada `security_originWindow`, reutilizável futuramente por autenticação e recuperação. Não haverá HMAC, criptografia nem rotação de chave. O registro será acessível somente para prevenção e investigação de abuso, não será copiado para auditoria permanente e será excluído até 30 dias depois do fim da janela.

As credenciais do Turnstile, os limites, as janelas e a lista explícita de proxies confiáveis serão definições exclusivas de `application.properties`. O limiar padrão do Turnstile será zero. O limite absoluto padrão será de 20 novas pendências de cadastro local por origem em uma janela de 24 horas iniciada pela primeira criação contabilizada. Somente uma nova pendência efetivamente persistida consumirá esse limite; rejeições anteriores à persistência, retomadas, reenvios, cancelamentos e convergências idempotentes não serão contabilizados. Falha do Siteverify será tratada como indisponibilidade e impedirá o cadastro quando o desafio for obrigatório.

A solicitação de cancelamento usa política própria e sempre exige Turnstile quando a integração está habilitada. Para
impedir bombardeio de e-mails sem transformar a resposta em oráculo, somente provas de cancelamento efetivamente
emitidas para uma pendência elegível consomem uma franquia independente, com padrão de três em 15 minutos. Cadastro
ausente, inelegível ou limitado recebe a mesma continuação pública aleatória; o instante de liberação permanece
interno, e essa janela não altera o contador de novas pendências por origem.

O RFW recebe a decisão dinâmica por operação/origem, deriva e valida a `action`, gera uma `idempotency_key` por
tentativa e aceita um `RFWRemoteAddressProvider` para que a hospedeira entregue apenas a origem já validada. A
plataforma também distingue prova inválida, divergência de contexto, indisponibilidade e configuração inválida sem
expor códigos técnicos. Essas capacidades foram evoluídas no RFW, com autorização, sem criar um segundo cliente
Turnstile no Rinos. A análise completa está em [rfw-gap-analysis.md](./rfw-gap-analysis.md).

**Rationale**: o RFW já concentra o contrato técnico reutilizável com o provedor, enquanto regras de cadastro pertencem ao Rinos. A retenção curta, a finalidade antifraude e a necessidade operacional de diagnóstico não justificam chave compartilhada, sincronização entre instâncias e rotação para pseudonimizar o IP. A lista de proxies confiáveis impede aceitar cabeçalhos forjados por clientes diretos.

**Alternatives considered**:

- Validar somente no navegador: rejeitada; o token pode ser forjado ou reutilizado.
- Pseudonimizar o IP com HMAC: rejeitada porque o benefício diante de vazamento exclusivo do banco não compensa chave compartilhada, sincronização, reinício e rotação entre instâncias para dados de curta retenção.
- Contadores apenas em memória: rejeitados porque reinicializações e múltiplas instâncias burlariam as janelas.

## Decision 7: Cadastro Google

**Decision**: reutilizar o fluxo Google Identity Services já entregue por `RFWGoogleSignInComponent` e `RFWGoogleIdentityProvider`. O componente gera e gira um `nonce` por tentativa; o backend valida assinatura, emissor, audiência, validade, `nonce` e `email_verified`. O vínculo persistido será único por `issuer + subject`; ID tokens Google serão transitórios e nunca serão credenciais locais.

O Rinos implementará `RFWExternalIdentityResolver` para localizar, iniciar ou rejeitar o vínculo conforme o domínio. E-mail igual ao de usuário ativo nunca cria vínculo automático. O RFW precisa expor o emissor validado de forma explícita e oferecer uma continuação genérica para que uma identidade Google nova aceite documentos legais antes da ativação; essas adaptações estão descritas em [rfw-gap-analysis.md](./rfw-gap-analysis.md).

**Rationale**: `sub` é o identificador estável do usuário no emissor; o e-mail pode mudar. O `nonce` server-side do componente vincula o ID token à tentativa iniciada naquela UI e é girado antes de outra submissão.

**Alternatives considered**:

- Vincular somente por e-mail: rejeitada porque o e-mail não é o identificador estável do provedor e permitiria associação indevida.
- Persistir access token ou ID token: rejeitada porque esta feature precisa somente comprovar a identidade.
- Criar outro fluxo Google diretamente no Rinos: rejeitada porque duplicaria o protocolo e o componente já fornecidos pelo RFW.

## Decision 8: Cancelamento, expiração e minimização

**Decision**: cadastros pendentes não ativados serão excluídos integralmente após 15 dias. No cancelamento, credenciais e comprovações são removidas imediatamente, o e-mail deixa de impedir um novo cadastro e permanece somente um tombstone sem e-mail ou outro dado diretamente identificável, correlacionado por identificador interno aleatório, pelo prazo operacional de 15 dias. Depois disso, o tombstone é excluído.

**Rationale**: o tombstone curto permite diagnosticar repetição e demonstrar o cancelamento sem conservar o identificador primário. A liberação imediata do e-mail preserva o controle da pessoa sobre um processo não concluído.

**Alternatives considered**:

- Reter o cadastro cancelado completo: rejeitada por necessidade e minimização.
- Excluir toda evidência no mesmo instante: rejeitada porque elimina a rastreabilidade mínima de cancelamento e abuso.

## Decision 9: Superfície e limites de módulos

**Decision**: a única superfície humana desta feature será a aplicação web responsiva Vaadin, acessível por desktop, tablet e telefone. A rota do Rinos hospedará `RFWAccessComponent`; a UI conectará o domínio pelos providers/facades públicos do RFW, enquanto entidades JPA e repositories permanecerão no backend. O fluxo detalhado de telas, estados e acessibilidade será produzido na etapa de Interface Design.

**Rationale**: Vaadin é a tecnologia aprovada e não há aplicativo móvel nativo no escopo. A separação `ui -> api`, com implementação no backend, segue as convenções do projeto.

**Alternatives considered**:

- Criar API pública REST no MVP: rejeitada porque não há consumidor externo definido.
- Criar componente próprio de acesso no Rinos: rejeitada porque duplicaria `RFWAccessComponent`.
- Colocar regras de cadastro nas views Vaadin: rejeitada por acoplamento, baixa testabilidade e violação das fronteiras de packages.

## Decision 10: Coordenação da limpeza em múltiplas instâncias

**Decision**: antecipar a capacidade de liderança automática de manutenção definida em `platform-operations`. Cada instância terá `instanceId` explícito no `application.properties` e `sessionId` novo por inicialização. Um lease no banco global será renovado a cada 30 minutos, expirará depois de quatro horas sem heartbeat e incrementará `epoch` em cada nova aquisição. A sessão vencedora aguardará 10 minutos, comprovará novamente propriedade e fencing e somente então executará tarefas vencidas. Cada transação de lote terá timeout padrão de cinco minutos, configurado em properties e validado como estritamente inferior à estabilização.

O heartbeat usa o relógio do MySQL e não gera versão de configuração. A limpeza de cadastros pendentes e de janelas de origem continua idempotente, em lotes próprios e limitados, com releitura transacional antes da exclusão e verificação do lease antes de cada lote. Um lote iniciado pela sessão anterior pode somente concluir dentro do timeout ou ser abortado; a nova líder começa depois da estabilização, quando já não pode haver transação antiga vigente. Preferência manual futura será configuração global separada, sem edição direta do lease.

**Rationale**: uma liderança compartilhada evita distribuir jobs simples e permite failover automático. Separar lease de configuração impede que heartbeats poluam histórico, auditoria e caches funcionais; `sessionId` e `epoch` impedem que uma execução antiga inicie novo trabalho depois de perder a liderança. O timeout menor que a estabilização fecha a janela de um lote já aberto sem exigir fencing distribuído em cada escrita de domínio.

**Alternatives considered**:

- Coordenador fixo por properties: rejeitado porque exige failover manual e pode deixar tarefas paradas enquanto outra instância está saudável.
- Heartbeat em propriedade global chave/valor: rejeitado porque estado operacional volátil não deve produzir versões de configuração.
- Processamento cooperativo com `SKIP LOCKED`: adiado porque distribui um job que não exige escala no estágio atual.

## Referências técnicas consultadas

- [Cloudflare Turnstile — validação no servidor](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Google OpenID Connect API Reference](https://developers.google.com/identity/openid-connect/reference)
- [Have I Been Pwned — Pwned Passwords](https://haveibeenpwned.com/API/v3#PwnedPasswords)
- [Spring Security — armazenamento de senhas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
