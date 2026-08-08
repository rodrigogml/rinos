# Research: Autenticação e Recuperação do Usuário

Documento produzido no Phase 0 do plano de `user-authentication`. As decisões abaixo resolvem as escolhas técnicas
necessárias antes do desenho de dados e contratos.

## Decision 1: Continuidade autenticada sem Spring Session

**Decision**: manter `HttpSession` e `VaadinSession` locais à instância e persistir separadamente um registro global
`AuthSession`, validado por um guard em cada interação protegida. Instalações com múltiplas instâncias usam afinidade
no proxy. A revogação ocorre no MySQL e é observada por todas as instâncias; a replicação transparente da árvore
Vaadin não será prometida nesta feature.

**Rationale**: Vaadin mantém a árvore de componentes no `HttpSession` e sua documentação atual adverte que Spring
Session genérico não é compatível. A alternativa comercial de replicação não condiz com o orçamento inicial. O
registro global independente entrega expiração, reconhecimento e revogação cross-instance sem transformar todo o
estado visual em dado distribuído.

**Alternatives considered**:

- Spring Session JDBC: rejeitada por incompatibilidade documentada com o estado server-side do Vaadin.
- Sessão somente em memória: rejeitada porque bloqueio e revogação remota não seriam imediatamente comuns às
  instâncias.
- Replicação Vaadin/Kubernetes Kit no MVP: adiada por custo e complexidade operacional não exigidos pela implantação
  inicial.

## Decision 2: Cookie opaco e “lembrar-me”

**Decision**: usar cookie opaco com seletor aleatório e verificador secreto. O banco localiza pelo seletor e conserva
somente a representação protegida do verificador. O valor é rotacionado após validação; divergência com seletor
existente revoga a família. O cookie sem “lembrar-me” é de sessão; com a opção marcada, recebe validade máxima de 30
dias. O backend sempre aplica também inatividade de 30 minutos/7 dias e duração absoluta de 12 horas/30 dias.

**Rationale**: o identificador de sessão equivale temporariamente à credencial mais forte usada no login. Persistir
somente um hash/MAC reduz o efeito de leitura do banco, e a rotação detecta reapresentação de um valor antigo.

**Alternatives considered**:

- Cookie autossuficiente assinado: rejeitado porque revogação individual imediata exigiria outra lista global e
  duplicaria estado.
- Persistir o token bruto: rejeitado porque uma leitura indevida do banco permitiria autenticação direta.
- Cookie com ID sequencial da sessão: rejeitado por previsibilidade e exposição de identificador interno.

## Decision 3: Fluxo intermediário persistente

**Decision**: representar a autenticação incompleta por `AuthenticationFlow` persistente, com referência opaca,
finalidade, primeiro fator, métodos permitidos, expiração, tentativas e estado. Nenhum `Authentication` plenamente
autenticado ou `SecurityContext` será publicado antes de MFA e gate legal terminarem.

**Rationale**: o fluxo precisa sobreviver a roundtrips do navegador, impedir replay e ser consumido atomicamente por
qualquer instância. Guardá-lo somente no componente Vaadin criaria sessão parcial e dificultaria concorrência.

**Alternatives considered**:

- Principal parcialmente autenticado no `SecurityContext`: rejeitado por ampliar o risco de autorização acidental.
- Estado somente em memória: rejeitado por perda em reinício e inconsistência entre instâncias.
- JWT de continuação: rejeitado porque consumo único e revogação ainda exigiriam persistência.

## Decision 4: Proteção contra descoberta e automação

**Decision**: reutilizar `security_originWindow` para políticas por IP e acrescentar contagem por identificador
informado em estrutura separada que armazena somente digest/MAC normalizado, nunca e-mail em auditoria antifraude. O
Turnstile torna-se obrigatório depois de três falhas relacionadas ao mesmo e-mail informado ou IP em 15 minutos e
permanece obrigatório até 15 minutos sem nova falha. Respostas públicas são equivalentes para identidade ausente,
senha incorreta e estado indisponível.

**Rationale**: um atacante pode distribuir tentativas por vários IPs contra uma identidade ou atacar muitas
identidades pelo mesmo IP. As duas dimensões precisam ser avaliadas sem permitir bloqueio permanente por terceiros.

**Alternatives considered**:

- Bloqueio permanente da conta: rejeitado por permitir negação de serviço contra o usuário.
- Contadores em memória: rejeitados porque reinício ou troca de instância reiniciaria a política.
- Mensagens distintas por causa: rejeitadas por descoberta de identidades e estados.

## Decision 5: Passkeys com Spring Security WebAuthn

**Decision**: reutilizar os endpoints e tipos de `spring-security-webauthn`, fornecendo implementações persistentes de
`PublicKeyCredentialUserEntityRepository` e `UserCredentialRepository` sobre tabelas globais do Rinos. A conclusão
WebAuthn será integrada ao mesmo fluxo pós-prova de usuário, MFA e aceite legal, em vez de publicar sessão diretamente
no endpoint padrão.

O `userHandle` será aleatório e estável; credential ID, chave pública, sign count, transports e flags de backup serão
persistidos. User verification será exigida para a passkey satisfazer 2FA. Queda ou repetição anormal do contador será
evento de risco, não revogação automática de todos os métodos, porque passkeys sincronizadas e concorrência podem
produzir sinais que não demonstram clonagem isoladamente.

**Rationale**: o Spring Security já implementa a validação criptográfica do protocolo. O W3C recomenda armazenar os
estados de backup e alerta que anomalia do contador pode significar clonagem, mau funcionamento ou corrida; a decisão
de risco pertence ao domínio.

**Alternatives considered**:

- Implementar WebAuthn criptográfico no Rinos: rejeitada por risco e duplicação.
- Usar e-mail como user handle: rejeitado porque e-mail muda e é informação identificável.
- Ignorar user verification: rejeitado porque a passkey não satisfaria o requisito de segundo fator.

## Decision 6: TOTP e proteção do segredo

**Decision**: usar `RFWTotpService` com seis dígitos, período de 30 segundos e janela de uma etapa para cada lado. O
segredo será cifrado com AEAD antes da persistência, incluindo versão da chave e nonce aleatório. A chave ativa e as
chaves anteriores de leitura terão origem exclusiva no `application.properties` e deverão ser iguais em todas as
instâncias da instalação. O último time-step aceito será gravado atomicamente para impedir reutilização.

**Rationale**: o servidor precisa recuperar o segredo para validar TOTP; hash unidirecional não funciona. Como esse
segredo permite gerar códigos futuros, ele exige proteção forte contra leitura do banco, diferente de IPs de curta
retenção. O keyring atende rotação sem invalidar fatores existentes.

**Alternatives considered**:

- Segredo em texto: rejeitado porque vazamento do banco comprometeria o fator.
- Chave de cifra no mesmo banco: rejeitada porque não cria separação útil diante de leitura do banco.
- Um segredo diferente por instância: rejeitado porque torna validação e failover inconsistentes.

## Decision 7: OTP por e-mail

**Decision**: gerar código numérico criptograficamente aleatório pelo RFW, enviar após commit e persistir somente MAC
versionado, expiração, tentativas e estado. Uma nova emissão invalida a anterior. Códigos terão uso único e limites
próprios de emissão e tentativa.

**Rationale**: códigos numéricos possuem espaço pequeno e um hash simples pode ser testado offline rapidamente. MAC
com chave fora do banco preserva comparação eficiente e reduz o valor de uma leitura isolada do banco. TTL e limites
continuam necessários.

**Alternatives considered**:

- Argon2 para cada OTP: rejeitado porque ainda permite busca offline do espaço pequeno e aumenta custo de ataque ao
  serviço.
- Texto puro: rejeitado por permitir uso imediato após leitura do banco.
- Reutilizar o token opaco de recuperação: rejeitado porque OTP digitável possui contrato e ergonomia diferentes.

## Decision 8: Códigos de recuperação

**Decision**: gerar 10 códigos pelo `RFWRecoveryCodeService`, apresentar uma única vez e armazenar hashes
independentes. Cada consumo bloqueia o conjunto e marca exatamente um código como usado. Regeneração, desativação do
2FA ou recuperação que altere fatores invalida o conjunto anterior inteiro.

**Rationale**: códigos possuem entropia suficiente para armazenamento unidirecional e precisam ser revogáveis
individualmente. Uma linha por código simplifica consumo atômico e auditoria sem conservar a lista em um campo
serializado.

**Alternatives considered**:

- Um hash do conjunto: rejeitado porque impede consumo individual claro.
- Exibição posterior: rejeitada porque exigiria armazenamento recuperável.
- Expiração fixa: rejeitada pela decisão funcional explícita da spec.

## Decision 9: Recuperação de senha e fatores

**Decision**: ampliar a recuperação mínima já implementada em `user-registration`. A prova opaca de uma hora e sua
resposta neutra permanecem; a conclusão aplica política vigente, invalida provas e sessões e notifica o usuário.
Recuperação de fator usa código de recuperação ou processo reforçado separado, nunca somente o fator perdido.

Usuário sem senha local não recebe senha silenciosamente. O fluxo informa os métodos existentes de maneira neutra e
qualquer criação de senha exige uma continuação explícita autenticada/recuperada.

**Rationale**: recuperação é outra forma de autenticação e não pode ser mais fraca que o acesso normal. Reutilizar a
infraestrutura já testada evita duas provas de redefinição incompatíveis.

**Alternatives considered**:

- Suporte humano no MVP: rejeitado porque a spec exige autosserviço e ainda não há processo operacional de prova de
  identidade.
- Perguntas de segurança: rejeitadas por baixa segurança e ausência de dados pessoais confiáveis.

## Decision 10: Reautenticação e nível de garantia

**Decision**: persistir na sessão os métodos usados e o instante da última autenticação/reautenticação. Operações
sensíveis exigem prova recente de até 15 minutos. A prova aceita depende do contexto: senha, TOTP ou passkey podem ser
usados quando vinculados; para sessão iniciada pelo Google, código no mesmo e-mail não eleva acesso administrativo.

**Rationale**: “usuário autenticado” não informa por si só a força ou atualidade da prova. Uma fotografia explícita
permite à autorização futura exigir garantia sem transformar método em permissão.

**Alternatives considered**:

- Guardar apenas booleano `mfa=true`: rejeitado porque não explica método, atualidade ou restrição do mesmo canal.
- Sempre exigir senha: rejeitado para usuários passwordless e passkeys.
- Conceder autoridade Spring por método: rejeitado porque mistura autenticação com autorização.

## Decision 11: Gate de documentos legais

**Decision**: após todos os fatores e antes da sessão, consultar versões obrigatórias vigentes e os consentimentos
imutáveis. Pendência cria `LegalConsentChallenge` de uso único. A conclusão registra novos aceites e consome o fluxo
na mesma transação; recusa não cria sessão.

**Rationale**: o requisito vale para todos os métodos e para usuários antigos. Colocar o gate somente no dashboard
permitiria acesso transitório a rotas protegidas.

**Alternatives considered**:

- Verificar depois de criar a sessão: rejeitada por janela de acesso indevido.
- Atualizar o aceite antigo: rejeitada porque evidências são imutáveis.
- Cachear a versão no navegador: rejeitado porque mudança durante o fluxo ficaria obsoleta.

## Decision 12: Manutenção e retenção

**Decision**: reutilizar o catálogo diário coordenado por `platform_maintenanceLease` para remover sessões, fluxos,
OTP e provas expiradas. Artefatos expirados deixam de ser aceitos pelo próprio `expiresAt` imediatamente; o job apenas
executa retenção física e não participa da decisão de segurança.

**Rationale**: atraso de job não pode prolongar uma credencial. O coordenador existente já oferece lease, fencing,
timeout e idempotência entre instâncias.

**Alternatives considered**:

- Um scheduler independente por feature: rejeitado por duplicar eleição e concorrência.
- Excluir apenas no momento do uso: rejeitado por retenção indefinida de dados nunca reapresentados.

## Referências técnicas consultadas

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP Multifactor Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
- [NIST SP 800-63B-4 — Authentication and Authenticator Management](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [RFC 6238 — TOTP](https://datatracker.ietf.org/doc/html/rfc6238)
- [W3C Web Authentication Level 3](https://www.w3.org/TR/webauthn-3/)
- [Spring Security — Passkeys](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passkeys.html)
- [Spring Security — Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Vaadin — Spring integration](https://vaadin.com/docs/latest/flow/integrations/spring)
- [Vaadin — Distributed Deployment](https://vaadin.com/docs/latest/flow/production/distributed-deployment)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
