# Research: Cadastro e Ciclo Inicial do Usuário

Documento produzido no Phase 0 do plano de `user-registration`. As decisões abaixo resolvem as escolhas técnicas necessárias antes do desenho.

## Decision 1: Identidade global e persistência

**Decision**: persistir o usuário, seu cadastro temporário, credenciais, identidades externas, consentimentos e auditoria no schema global. Nenhuma dessas entidades recebe `tenantId` nem depende de contexto de tenant.

**Rationale**: a mesma identidade deve existir antes de qualquer conta e depois participar de múltiplos tenants. Manter a raiz da identidade no global evita duplicidade, dependência circular e acesso implícito a dados de conta.

**Alternatives considered**:

- Criar um usuário por tenant: rejeitada porque viola a identidade global única.
- Criar um tenant pessoal durante o cadastro: rejeitada porque a ativação não deve criar conta, tenant ou plano.

## Decision 2: Convergência, concorrência e idempotência

**Decision**: criar a identidade global já no estado `PENDING_VERIFICATION` e manter um processo `Registration` 1:1. A unicidade do e-mail normalizado será garantida no banco, e os casos de uso de início, reenvio, ativação e cancelamento serão transacionais e idempotentes. Colisões concorrentes serão resolvidas relendo o registro vencedor, nunca por uma verificação prévia isolada.

**Rationale**: uma consulta “existe?” seguida de inserção não protege contra duas solicitações simultâneas. A constraint única é a autoridade final, enquanto a aplicação converte a colisão no resultado funcional previsto.

**Alternatives considered**:

- Manter o cadastro somente em sessão ou cache: rejeitada porque não permite retomada em outro dispositivo e perde estado em reinicializações.
- Criar uma nova tentativa para cada solicitação: rejeitada porque duplica identidade, aceites e comprovações.

## Decision 3: Senhas locais

**Decision**: aplicar a política funcional antes da persistência, consultar senhas comprometidas pela API gratuita Pwned Passwords usando k-anonimato e armazenar somente hash Argon2id por meio do `PasswordEncoder` do Spring Security, com identificador de algoritmo no valor persistido. Os parâmetros de custo deverão ser calibrados no ambiente de produção antes da liberação.

Nenhuma senha, hash SHA-1 completo ou sufixo comparado será registrado em logs ou auditoria. Se o serviço de senhas comprometidas estiver indisponível, o cadastro local falhará fechado com orientação para tentar novamente; o cadastro Google continuará disponível.

**Rationale**: a política exige rejeitar senhas comuns ou comprometidas. A consulta por prefixo envia somente os cinco primeiros caracteres do SHA-1 e compara o restante localmente; a senha completa nunca sai do processo. Argon2id oferece armazenamento não recuperável e o prefixo do encoder permite evolução futura do algoritmo.

**Alternatives considered**:

- Apenas regras de composição: rejeitada porque não detecta credenciais conhecidamente comprometidas.
- Enviar a senha ou o hash completo a um serviço: rejeitada por exposição desnecessária.
- Manter uma cópia local integral da base de comprometimentos no MVP: rejeitada pelo custo operacional de atualização e armazenamento.

## Decision 4: Comprovação do e-mail

**Decision**: emitir um token opaco de alta entropia para um link HTTPS, persistindo somente seu hash SHA-256, estado, emissão e expiração. O token será de uso único, válido por 24 horas; nova emissão invalida as anteriores. A aplicação Rinos será responsável pelo significado, persistência e política, reutilizando os contratos de entrega e o serviço de e-mail do RFW.

O registro pendente e a comprovação serão confirmados na mesma transação. O envio SMTP ocorrerá após o commit; se falhar, o cadastro permanece retomável e o reenvio gera uma nova comprovação.

**Rationale**: um segredo aleatório suficientemente longo pode ser transportado em link sem pedir digitação e pode ser validado por comparação de hash. Separar persistência de entrega impede que uma falha SMTP desfaça ou duplique o cadastro.

**Alternatives considered**:

- Persistir o token em texto recuperável: rejeitada porque uma leitura indevida do banco permitiria ativação.
- Usar somente o gerador numérico padrão do RFW: rejeitada para este fluxo público porque exige controles adicionais de tentativas e oferece menor entropia que um token opaco.
- Usar uma fila/outbox no primeiro incremento: adiada; o reenvio idempotente satisfaz o requisito atual com menor complexidade.

## Decision 5: Documentos legais e consentimentos

**Decision**: manter no global um catálogo versionado de documentos legais com período de vigência e registrar um consentimento imutável por usuário, versão e finalidade. O cadastro resolve as versões vigentes no momento da confirmação; se mudarem desde o envio inicial, a ativação é interrompida até novo aceite.

**Rationale**: armazenar apenas um booleano não demonstra qual texto foi aceito. A versão e o instante são necessários para rastreabilidade e para reapresentar alterações ocorridas durante um cadastro pendente.

**Alternatives considered**:

- Gravar apenas a versão atual no usuário: rejeitada porque perde o histórico de aceites.
- Copiar o conteúdo integral em cada aceite: rejeitada por duplicação; a versão imutável do documento é a fonte da verdade.

## Decision 6: Cloudflare Turnstile e controle por origem

**Decision**: adicionar ao RFW um adaptador genérico para a API Siteverify do Cloudflare Turnstile; o Rinos permanece responsável pela política que decide quando exigir o desafio e pelos limites por origem. O token será validado no servidor antes de qualquer persistência, com verificação de `hostname` e `action`, timeout limitado e sem persistência do token.

Os limites por IP usarão janelas persistidas no schema global, identificadas por HMAC do endereço normalizado. A chave HMAC, as credenciais do Turnstile, os limites, as janelas e a lista explícita de proxies confiáveis serão definições exclusivas de `application.properties`. O limiar padrão do Turnstile será zero. Falha do Siteverify será tratada como indisponibilidade e impedirá o cadastro quando o desafio for obrigatório.

**Rationale**: o RFW deve concentrar o contrato técnico reutilizável com o provedor, enquanto regras de cadastro pertencem ao Rinos. O HMAC permite contar uma origem sem conservar o IP em claro. A lista de proxies confiáveis impede aceitar cabeçalhos forjados por clientes diretos.

**Alternatives considered**:

- Validar somente no navegador: rejeitada; o token pode ser forjado ou reutilizado.
- Armazenar o IP em claro: rejeitada por minimização de dados.
- Contadores apenas em memória: rejeitados porque reinicializações e múltiplas instâncias burlariam as janelas.

## Decision 7: Cadastro Google

**Decision**: usar OpenID Connect Authorization Code com PKCE e `nonce`. O backend fará a troca do código e validará assinatura, emissor, audiência, validade, `nonce` e `email_verified`. O vínculo persistido será único por `issuer + subject`; tokens Google serão transitórios e nunca serão credenciais locais.

O Rinos definirá uma porta de identidade externa independente do provedor. O adaptador Google será usado pelo cadastro agora e poderá ser reutilizado posteriormente por `user-authentication`. E-mail igual ao de usuário ativo nunca cria vínculo automático.

**Rationale**: `sub` é o identificador estável do usuário no emissor; o e-mail pode mudar. PKCE e `nonce` vinculam a resposta à tentativa iniciada pelo navegador e reduzem interceptação e repetição.

**Alternatives considered**:

- Vincular somente por e-mail: rejeitada porque o e-mail não é o identificador estável do provedor e permitiria associação indevida.
- Persistir access token ou ID token: rejeitada porque esta feature precisa somente comprovar a identidade.

## Decision 8: Cancelamento, expiração e minimização

**Decision**: cadastros pendentes não ativados serão excluídos integralmente após 15 dias. No cancelamento, credenciais e comprovações são removidas imediatamente, o e-mail deixa de impedir um novo cadastro e permanece somente um tombstone sem e-mail ou outro dado diretamente identificável, correlacionado por identificador interno aleatório, pelo prazo operacional de 15 dias. Depois disso, o tombstone é excluído.

**Rationale**: o tombstone curto permite diagnosticar repetição e demonstrar o cancelamento sem conservar o identificador primário. A liberação imediata do e-mail preserva o controle da pessoa sobre um processo não concluído.

**Alternatives considered**:

- Reter o cadastro cancelado completo: rejeitada por necessidade e minimização.
- Excluir toda evidência no mesmo instante: rejeitada porque elimina a rastreabilidade mínima de cancelamento e abuso.

## Decision 9: Superfície e limites de módulos

**Decision**: a única superfície humana desta feature será a aplicação web responsiva Vaadin, acessível por desktop, tablet e telefone. A UI chamará facades Java da aplicação; entidades JPA e repositories permanecerão no backend. O fluxo detalhado de telas, estados e acessibilidade será produzido na etapa de Interface Design.

**Rationale**: Vaadin é a tecnologia aprovada e não há aplicativo móvel nativo no escopo. A separação `ui -> api`, com implementação no backend, segue as convenções do projeto.

**Alternatives considered**:

- Criar API pública REST no MVP: rejeitada porque não há consumidor externo definido.
- Colocar regras de cadastro nas views Vaadin: rejeitada por acoplamento, baixa testabilidade e violação das fronteiras de packages.

## Referências técnicas consultadas

- [Cloudflare Turnstile — validação no servidor](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Google OpenID Connect API Reference](https://developers.google.com/identity/openid-connect/reference)
- [Have I Been Pwned — Pwned Passwords](https://haveibeenpwned.com/API/v3#PwnedPasswords)
- [Spring Security — armazenamento de senhas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

