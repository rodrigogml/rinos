# Evidência da tarefa 7.4.6

## Build executável

O `vaadin-maven-plugin` executa `build-frontend` no lifecycle normal. Um build iniciado por
`mvn clean ... verify` gerou o bundle otimizado, incorporou os recursos RFW e produziu
`target/rinos-1.0.0.jar` sem depender do servidor de desenvolvimento do Vaadin.

Essa configuração é obrigatória: o JAR anterior tentava iniciar em modo de desenvolvimento e
falhava porque `vaadin-dev-server` corretamente não integrava o artefato executável.

## Smoke atrás da fronteira de proxy

`RinosConfigurationSourceIT`:

1. cria e inicializa um schema MySQL 9.7.2 descartável;
2. reserva uma porta interna e grava no arquivo temporário configuração explícita com
   `server.forward-headers-strategy=none`, origem pública `https://app.rinos.com.br` e apenas
   `127.0.0.1` como proxy confiável;
3. inicia o JAR real em subprocesso;
4. acessa `/login` pela porta interna com `Forwarded`, `X-Forwarded-For`, `X-Forwarded-Proto` e
   `X-Forwarded-Host`, incluindo o host hostil `attacker.invalid`;
5. confirma resposta HTTP entre 200 e 399, bootstrap do bundle `/VAADIN/build/` e ausência do
   host hostil no corpo, em redirecionamentos e no log;
6. encerra o processo e remove o schema mesmo diante de falha do teste.

Comando focal final:

```powershell
mvn "-Dtest=NoUnitTestsForThisGate" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=RinosConfigurationSourceIT#application_shouldServeLogin_withExplicitReverseProxyConfiguration" verify
```

Resultado: um teste de integração executado em 8,106 segundos, sem falhas, erros ou testes
ignorados, e `BUILD SUCCESS`. A execução total levou 18,931 segundos.

> [!NOTE]
> O teste simula a fronteira HTTP do proxy. A configuração TLS, os headers escritos pelo Apache
> e o bloqueio de acesso externo à porta 7070 ainda devem ser verificados no servidor real.
