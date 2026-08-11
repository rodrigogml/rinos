# Evidência 7.5.6 — calibração Argon2id no servidor-alvo

**Data da validação:** 2026-08-11  17:50–17:54 UTC
**Servidor:** Turing (`Linux amd64`)
**JVM:** `25.0.2`
**Processadores disponíveis:** 20
**Memória disponível observada:** 17.252.548 kB

## Procedimento

A ferramenta `PasswordHashCalibrationTool` foi executada a partir do JAR instalado em
`/opt/rinos/rinos-1.0.0.jar`. Para preservar o serviço e a configuração de produção,
o JAR foi extraído somente para diretórios temporários em `/tmp`; cada diretório foi
removido ao final. Não houve reinício, alteração de configuração persistente ou
interrupção do serviço `rinos`.

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

## Candidato calibrado

Mantendo memória, paralelismo, salt e hash, foi testado um arquivo de propriedades
temporário com `iterations=75`:

```text
measuredAt=2026-08-11T17:53:51.249523079Z
java=25.0.2
os=Linux amd64
processors=20
memoryKib=19456
iterations=75
parallelism=1
saltLength=16
hashLength=32
measurements=50
medianMillis=517,045
p95Millis=527,174
result=PASS
```

O candidato satisfaz o critério de latência, mas **não foi aplicado** ao
`application.properties` nem ao serviço instalado. A alteração efetiva exige uma
janela operacional autorizada e novo smoke test após o deploy. Até essa aplicação,
o gate de release permanece aberto.

> [!IMPORTANT]
> Os separadores decimais com vírgula acima são a saída literal do JAR instalado no
> Turing. A versão atual do código usa `Locale.ROOT` para tornar novas execuções
> independentes da localidade.
