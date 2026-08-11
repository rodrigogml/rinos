# Evidência 7.5.6 — calibração Argon2id no servidor-alvo

**Data da validação:** 2026-08-11  17:50–18:15 UTC
**Servidor:** Turing (`Linux amd64`)
**JVM:** `25.0.2`
**Processadores disponíveis:** 20
**Memória disponível observada:** 17.252.548 kB

## Procedimento

A ferramenta `PasswordHashCalibrationTool` foi executada a partir do JAR instalado em
`/opt/rinos/rinos-1.0.0.jar`. O JAR foi extraído somente para diretórios temporários em
`/tmp`, removidos ao final. Primeiro foi medido o perfil antigo; depois, após backup,
`rinos.password-hash.iterations=75` foi aplicado ao arquivo runtime e o serviço `rinos`
foi reiniciado controladamente. Apache e MySQL não foram reiniciados.

Cada amostra usou dez aquecimentos e 50 medições, sem registrar senha nem hash.

## Resultado do perfil atualmente instalado

```text
measuredAt=2026-08-11T17:50:47.868018996Z
java=25.0.2
os=Linux amd64
processors=20
memoryKib=19456
iterations=2
parallelism=1
saltLength=16
hashLength=32
measurements=50
medianMillis=15,424
p95Millis=19,638
result=FAIL
```

O perfil atual não atende à faixa contratual de mediana entre 500 e 1.000 ms.

## Perfil efetivo após aplicação

O valor foi aplicado ao `application.properties` do Turing após o backup
`/opt/rinos/application.properties.bak-20260811T181407Z`:

```text
measuredAt=2026-08-11T18:14:45.108293562Z
java=25.0.2
os=Linux amd64
processors=20
memoryKib=19456
iterations=75
parallelism=1
saltLength=16
hashLength=32
measurements=50
medianMillis=503,953
p95Millis=541,899
result=PASS
```

O perfil efetivo atende à faixa de mediana entre 500 e 1.000 ms e ao p95 máximo de
1.500 ms. Após o reinício, `systemctl is-active rinos` retornou `active`.

## Smoke test após o reinício

```text
curl -k -H 'Host: app.rinos.com.br' https://127.0.0.1/login -> 200 text/html;charset=utf-8
https://app.rinos.com.br/login -> 200 via Cloudflare
Banco já está na versão esperada.
Tomcat started on port 7070.
```

> [!IMPORTANT]
> Os separadores decimais com vírgula acima são a saída literal do JAR instalado no
> Turing. A versão atual do código usa `Locale.ROOT` para tornar novas execuções
> independentes da localidade.
