# Evidência da tarefa 6.3.1

Data da validação: 2026-08-01

## Escopo

A etapa de login do Rinos foi rastreada e validada desde a configuração explícita da aplicação até o componente
oficial do Google fornecido pelo RFW. A implementação de produção necessária já existia como resultado das fases de
infraestrutura e backend; esta tarefa consolidou a prova de integração sem duplicar componentes ou alterar o
submódulo.

Esta etapa cobre somente o início do protocolo Google em `SIGN_IN`. A composição e a conclusão do cadastro externo
permanecem nas tarefas 6.3.2 a 6.3.4, e os cenários visuais e de navegador permanecem na tarefa 6.3.7.

## Cadeia integrada

Quando `rfw.authentication.google.enabled=true` e o `client-id` público está preenchido, a integração segue
esta cadeia:

```text
application.properties
  → RFWExternalIdentityResolverAdapter do Rinos
  → RFWGoogleIdentityProvider criado pela auto-configuração do RFW
  → capability EXTERNAL_IDENTITY
  → RFWGoogleSignInComponent na etapa SIGN_IN
```

O `RFWExternalIdentityResolverAdapter` depende somente da facade pública `GoogleIdentityResolutionFacade`. Depois de
o RFW validar a credencial externa, esse adapter entrega ao domínio apenas a identidade verificada necessária para a
decisão de continuidade. A credencial Google bruta permanece confinada ao protocolo do RFW.

O renderer padrão do `RFWAccessComponent` detecta o provider `google` e cria `RFWGoogleSignInComponent`. O Rinos não
possui botão, JavaScript, CSS ou formulário Google paralelo.

## Configuração segura

O modelo versionado mantém o Google desabilitado por padrão:

```properties
rfw.authentication.google.enabled=false
rfw.authentication.google.client-id=
rfw.authentication.google.issuer=https://accounts.google.com
rfw.authentication.google.timeout=10s
rfw.authentication.google.clock-skew=60s
```

A habilitação exige edição explícita do `application.properties` da instância. Sem a propriedade habilitada, o RFW
não cria o provider e não anuncia `EXTERNAL_IDENTITY`; consequentemente, nenhum início Google é apresentado no
login.

> [!IMPORTANT]
> O `client-id` é a audiência pública do aplicativo Google. Segredos, ID tokens e nonces não são gravados em
> configuração, logs, documentação ou estado de formulário do Rinos.

## Validação automatizada

O teste `RFWPlatformIntegrationTest` passou a usar o `RFWExternalIdentityResolverAdapter` concreto do Rinos. Com a
configuração Google habilitada, ele comprova que:

- existe um único resolvedor externo e ele é o adapter real da hospedeira;
- o RFW cria um único `RFWGoogleIdentityProvider`;
- a capability `EXTERNAL_IDENTITY` é anunciada;
- o componente Rinos/RFW inicia em `SIGN_IN`;
- a árvore renderizada contém exatamente um elemento oficial `rfw-google-sign-in`.

Teste focal:

```powershell
mvn -Dtest=RFWPlatformIntegrationTest test
```

Resultado:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:      292; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   49; 0 falhas; 0 erros; 4 E2E de navegador opt-in
BUILD SUCCESS
```

Os quatro E2E opt-in pertencem às jornadas de navegador já existentes e não foram necessários para provar esta
integração estrutural. Os cenários MySQL do gate padrão foram executados, sem skips adicionais.

## Conclusão

O início Google está disponível exclusivamente na etapa de login quando a configuração explícita e o adapter real do
Rinos permitem que o RFW descubra a capability. A tarefa 6.3.1 está concluída sem alteração na API ou no runtime do
RFW.
