# Contracts: Serviços Externos do Cadastro de Usuário

Estes são contratos de saída da aplicação. O Rinos não expõe API REST pública nesta feature; a UI Vaadin consome facades Java no mesmo processo.

## Cloudflare Turnstile Siteverify

**Owner técnico**: RFW Platform  
**Owner da política**: Rinos `user-registration`  
**Method**: `POST https://challenges.cloudflare.com/turnstile/v0/siteverify`  
**Auth**: secret key enviada somente pelo backend

### Request

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `secret` | string | yes | Vem exclusivamente de configuração protegida |
| `response` | string | yes | Máximo de 2048 caracteres; nunca persistido |
| `remoteip` | string | no | Origem já resolvida por política de proxy confiável |
| `idempotency_key` | UUID | yes no Rinos | Novo por tentativa de validação |

### Accepted response

| Field | Type | Validation |
|-------|------|------------|
| `success` | boolean | Deve ser `true` |
| `hostname` | string | Deve pertencer à allowlist configurada |
| `action` | string | Deve ser a ação esperada para cadastro |
| `challenge_ts` | timestamp | Deve ser parseável e compatível com token ainda válido |
| `error-codes` | string[] | Registrados somente de forma sanitizada quando houver falha |

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Token ausente, inválido, expirado ou reutilizado | `HUMAN_VERIFICATION_REQUIRED` com renovação do widget |
| `hostname` ou `action` divergente | `HUMAN_VERIFICATION_INVALID` e evento de segurança |
| Timeout, HTTP 5xx ou resposta inválida | `HUMAN_VERIFICATION_UNAVAILABLE`; nenhuma persistência |
| Configuração ausente/inválida quando obrigatória | Falha explícita de inicialização |

O adapter padrão do RFW representa essas condições por `RFWHumanVerificationFailureEnum`: `INVALID_PROOF`,
`HOSTNAME_MISMATCH`, `ACTION_MISMATCH`, `PROVIDER_UNAVAILABLE` e `CONFIGURATION_INVALID`. A UI converte a categoria
em mensagem pública e nunca apresenta `error-codes`. A `action` não é uma propriedade livre do Rinos: o RFW a deriva
da operação estável (`registration`, `sign-in`, `registration-cancellation` ou `password-recovery`) e compara o valor
devolvido pelo Siteverify.

## Google OpenID Connect

**Owner técnico**: `RFWGoogleSignInComponent` e `RFWGoogleIdentityProvider`, com resolução de domínio pelo Rinos

**Flow**: Google Identity Services com ID token efêmero e `nonce` exclusivo por tentativa

**Auth**: client ID público configurado na instalação

### Inicialização do componente Google

| Field | Required | Validation |
|-------|----------|------------|
| `client_id` | yes | Origem exclusiva em properties |
| `nonce` | yes | Aleatório, gerado no servidor pelo componente e girado antes de nova tentativa |

### ID token claims accepted

| Claim | Validation |
|-------|------------|
| `iss` | Emissor Google explicitamente permitido |
| `aud` | Contém o client ID da instalação |
| `exp`, `iat` | Válidos com tolerância de relógio limitada |
| `nonce` | Corresponde à tentativa aberta e não usada |
| `sub` | Presente; usado com `iss` como identidade estável |
| `email` | Sintaticamente válido e normalizado pelo Rinos |
| `email_verified` | Obrigatoriamente `true` |

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| `nonce` inválido ou token reapresentado em outra tentativa | `EXTERNAL_IDENTITY_REJECTED`; nenhuma escrita de usuário |
| Assinatura, issuer, audience ou tempo inválido | `EXTERNAL_IDENTITY_REJECTED` |
| E-mail não verificado | `EXTERNAL_EMAIL_NOT_VERIFIED` |
| `issuer + sub` já vinculado a outro usuário | `EXTERNAL_IDENTITY_CONFLICT` sem expor o outro usuário |
| E-mail de usuário ativo sem vínculo | `EXISTING_USER_REAUTHENTICATION_REQUIRED` |
| Timeout/indisponibilidade | `EXTERNAL_IDENTITY_UNAVAILABLE`; oferecer cadastro local |

ID token e credenciais Google não são persistidos nem registrados. O adapter público do Rinos copia do VO validado
do RFW somente `providerId`, `issuer`, `subject`, e-mail, indicação de e-mail verificado e um correlation ID novo.
O mapa de claims não atravessa a API do Rinos. A resolução que ainda exige aceites emite uma referência aleatória de
uso único, persiste somente seu hash e a apresenta como `RFWExternalRegistrationChallengeVO`.

`RFWExternalRegistrationProvider` envia de volta somente essa referência e os IDs dos documentos aceitos. O Rinos
revalida prova, expiração, cadastro e a única identidade externa candidata, separa documentos obrigatórios de
opcionais pelo catálogo vigente e conclui tudo em uma transação. Eventual senha local e as provas anteriores da
pendência reutilizada só são removidas depois dessas validações. O adapter cria o `Authentication` com um principal
mínimo, sem authorities de tenant, somente depois do commit; prova usada ou expirada nunca produz nova sessão.

O gate da integração executa isoladamente os testes unitários e de integração do provider Google do RFW, incluindo
nonce divergente, assinatura e discovery simulados, e executa no Rinos as falhas de borda antes da resolução, durante
a persistência e depois da emissão da continuação. Indisponibilidade retorna
`registration.google.unavailable` sem autenticação e preserva a opção de cadastro local do componente de acesso.

O Google Identity Services usado pelo componente é um protocolo de identidade, sem autorização OAuth para Drive,
Calendar ou qualquer outro serviço Google. O Rinos não configura nem solicita escopos adicionais.

## Pwned Passwords Range API

**Owner técnico**: adapter de política de senha do Rinos  
**Method**: `GET https://api.pwnedpasswords.com/range/{prefix}`  
**Auth**: não exigida para Pwned Passwords

### Request

| Element | Validation |
|----------|------------|
| `prefix` | Exatamente os primeiros 5 caracteres uppercase do SHA-1 calculado localmente |
| `User-Agent` | Identifica o Rinos conforme configuração |
| `Add-Padding` | Habilitado para reduzir inferência pelo tamanho da resposta |

### Response

O adapter procura localmente o sufixo restante do SHA-1 entre as linhas `SUFFIX:COUNT`. O prefixo, a resposta e o sufixo comparado não são persistidos.

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Sufixo encontrado com contagem positiva | `PASSWORD_COMPROMISED` |
| Sufixo ausente | Senha passa nesta verificação e segue para hashing |
| Timeout, HTTP não esperado ou payload inválido | `PASSWORD_CHECK_UNAVAILABLE`; cadastro local não persiste |

## SMTP por RFW

**Owner técnico**: RFW Platform `EmailDispatchService`/`EmailDispatcher`  
**Transport**: SMTP configurado pela instalação

### Application request

| Field | Required | Validation |
|-------|----------|------------|
| template lógico | yes | Recurso existente no classpath |
| destinatário | yes | E-mail imutável do cadastro |
| URL de confirmação | yes | HTTPS em produção; `/login?step=activation&proof=...` |
| código para cópia manual | yes, na ativação | Mesma prova opaca do link; não é um segundo segredo |
| expiração exibida | yes | Deve corresponder ao `expiresAt` persistido |
| locale | no | Fallback documentado pelo RFW |

Links externos usam exclusivamente `rinos.application.public-base-url`. O valor local padrão é
`http://localhost:7070`; a instalação produtiva define `https://app.rinos.com.br`, independentemente da porta interna
`server.port=7070` e dos cabeçalhos recebidos pelo proxy. Paths de navegação permanecem relativos dentro da aplicação,
mas mensagens de e-mail recebem a URL absoluta exigida por clientes externos.

A confirmação local usa uma única prova aleatória de 256 bits, codificada em Base64 URL-safe sem padding. O e-mail
oferece essa mesma prova de duas formas: embutida no deep link canônico da rota `/login` e exibida em bloco de código
para copiar e colar no formulário manual. Não existe código curto, segunda credencial ou persistência reversível; o
banco conserva somente o SHA-256 da prova. A rota aceita apenas a intenção pública `activation`, nunca recebe e-mail
ou ID interno e entrega a entrada ao `RFWAccessComponent` por `RFWAccessEntryRequestVO`. Depois dessa entrega, o
navegador substitui a URL por `/login`, reduzindo exposição em histórico e navegações posteriores sem apagar a prova
efêmera já aberta na UI.

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Mensagem aceita pelo dispatcher e pelo SMTP | Cadastro permanece pendente e UI confirma envio |
| Falha de template, timeout ou transporte | Cadastro permanece pendente; UI não afirma que houve envio e oferece retomada e reenvio |
| Processo interrompido entre commit e dispatch | Cadastro permanece pendente; retomada permite solicitar nova comprovação |
| Reenvio | Nova comprovação invalida anteriores antes do novo dispatch |

O log registra apenas resultado operacional e correlation ID; não registra destinatário completo, conteúdo nem URL secreta.

O cancelamento usa o template lógico `registration-cancellation` e a URL absoluta
`/cancel-registration?token=...` sobre a mesma origem pública. A solicitação sempre devolve à UI uma continuação
aleatória com a mesma forma e validade, exista ou não pendência elegível. Somente a pendência real recebe
`REGISTRATION_CANCEL`; falha de template ou SMTP permanece observável internamente, mas não altera a resposta pública,
pois essa diferença permitiria descobrir cadastros existentes. Uma nova solicitação substitui apenas outra prova aberta
de cancelamento e não afeta a prova de ativação até que o cancelamento seja confirmado.

No primeiro incremento, o dispatch é direto depois do commit. Não existe outbox nem retentativa automática, e token,
URL secreta ou mensagem renderizada não são persistidos para envio posterior. A recuperação de falhas ocorre por
reenvio solicitado pela pessoa, que cria uma nova comprovação conforme as regras do cadastro.

O reenvio bloqueia a pendência antes da decisão, considera exclusivamente eventos
`VERIFICATION_REISSUED` ocorridos dentro da janela móvel e permite, por padrão, três novas
solicitações em 15 minutos. O envio inicial não consome essa franquia. A quarta solicitação
informa o tempo restante calculado pelo evento mais antigo ainda dentro da janela; nenhuma
prova é emitida nessa condição. Pendências ausentes, encerradas, não locais ou que atingiram
os 15 dias respondem de forma neutra e não disparam mensagem. Um reenvio aceito invalida
qualquer prova aberta anterior, registra o evento sanitizado e cria uma prova distinta sem
alterar a expiração absoluta do cadastro.

O Rinos mede o intervalo entre o commit do cadastro e a aceitação da mensagem pelo servidor SMTP. Entrega final na
caixa postal, bounce, classificação como spam e atrasos posteriores só integram a observabilidade quando o provedor
configurado oferecer eventos próprios; sem esses eventos, não constituem gate de release.

O coordenador `VerificationEmailDispatchService` registra o trabalho na sincronização da transação proprietária e
materializa a mensagem somente no callback `afterCommit`. O resultado assíncrono permanece pendente até a aceitação ou
falha do SMTP; rollback conclui sem tentativa de envio. Falha não inicia retentativa automática, e um reenvio posterior
é uma nova operação explícita. As métricas `rinos.registration.verification.smtp.attempts` e
`rinos.registration.verification.smtp.duration` usam exclusivamente a tag fixa `result`, sem destinatário, token, URL
ou conteúdo renderizado.

O gate nominal usa 100 cadastros contra SMTP local controlado e exige ao menos 95 aceitações em até dois minutos. Para
medir somente o dispatch, o perfil de teste permite ao menos 100 novas pendências por origem e usa verificação humana
controlada; a política padrão de 20 e o Turnstile real são validados separadamente. No SMTP real da instalação, o
readiness executa somente um smoke test e não declara throughput ou capacidade suportada.
