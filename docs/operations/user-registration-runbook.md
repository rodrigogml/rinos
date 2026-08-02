# Runbook operacional do cadastro de usuário

Este runbook orienta o diagnóstico da feature `user-registration` em uma instalação Linux com
JAR executável, Apache como proxy reverso e MySQL 9. Ele não substitui o procedimento de deploy,
o monitoramento da infraestrutura nem a documentação do provedor externo.

> [!IMPORTANT]
> Backup e restauração são operações exclusivas da infraestrutura. O Rinos não cria,
> seleciona, executa ou restaura cópias. Este runbook não autoriza essas operações.

## Primeiros dados do incidente

Antes de alterar o ambiente, registre:

- instante inicial e fuso horário;
- versão do JAR e identificador da instância;
- escopo: toda a aplicação, um tenant futuro, uma operação ou um usuário;
- rota e resultado público, sem copiar e-mail, IP, token, prova ou senha;
- correlation ID exibido no log, quando houver;
- categoria e tipo fechado da falha;
- mudança de deploy, rede, certificado, proxy, banco ou provedor próxima ao início.

Não cole `application.properties`, URL JDBC, credencial, corpo de e-mail, cabeçalho de
autorização ou payload de provedor em ticket ou chat. Quando necessário, confirme apenas que uma
chave está presente e não vazia no arquivo protegido.

## Verificações Linux somente leitura

Substitua os nomes entre `<...>` pelos valores definidos pela infraestrutura:

```shell
systemctl status <servico-rinos> --no-pager
journalctl -u <servico-rinos> --since '<inicio-do-incidente>' --no-pager
ss -ltnp
curl --fail --head http://127.0.0.1:7070/login
```

O `curl` interno apenas comprova que o JAR responde. A jornada pública deve ser verificada
separadamente em `https://app.rinos.com.br/login`, passando pelo Apache e pelo HTTPS.

> [!CAUTION]
> Não acrescente propriedades por argumento, `-D`, variável de ambiente ou profile para tentar
> contornar uma falha. O arquivo `application.properties` da pasta de execução é a única origem
> autorizada e qualquer alteração exige reinicialização controlada.

## Matriz de diagnóstico

| Sintoma | Sinais a consultar | Diagnóstico e ação permitida |
|---------|--------------------|------------------------------|
| JAR não inicia | exit code, primeira exceção causal, categoria RFW, ausência de `Started RinosApplication` | Verificar Java 25, arquivo único, permissões, porta e dependências. Não reiniciar em ciclo antes de entender a causa. |
| Migration global falha | categoria `CONFIGURATION`, `LOCK_TIMEOUT`, `SCRIPT_*`, `EXECUTION` ou `VERSION_CONSISTENCY`; versões e script seguros | Manter a aplicação indisponível e seguir [Falhas e intervenção externa](../architecture/database-scripts.md#falhas-e-intervenção-externa). Não editar `databaseVersion`, script publicado ou estado parcial por tentativa. |
| `/login` não responde externamente | processo ativo, porta 7070 local, status do Apache, certificado e resposta interna/externa | Corrigir a fronteira de rede. Manter `server.forward-headers-strategy=none`, origem pública canônica e allowlist mínima do proxy. Não publicar a porta interna. |
| Falha SMTP | `rinos.registration.verification.smtp.attempts` e `.duration` com `template_failure` ou `transport_failure`; `WARN` pelo correlation ID | Verificar DNS, rota, TLS, autenticação, remetente e disponibilidade do SMTP sem imprimir segredos. A pendência permanece; orientar retomada e reenvio explícito. Não criar retentativa automática. |
| Mensagem aceita, mas não recebida | resultado SMTP `accepted` | O Rinos comprovou somente o aceite do servidor. Investigar atraso, bounce e spam no provedor quando ele oferecer eventos; não alterar o estado do cadastro com base em suposição. |
| Turnstile indisponível ou rejeitado | resultado `unavailable`, falha contextual e status do provedor | Confirmar endpoint, timeout, hostname, action e chaves no arquivo protegido. Falhar fechado e tentar novamente depois; nunca desabilitar em produção para contornar incidente. |
| Google indisponível ou rejeitado | `unavailable`, `identity_rejected`, divergência de issuer/audience/nonce ou status do provedor | Verificar relógio UTC, rede, client ID e disponibilidade. Oferecer cadastro local quando permitido; não vincular e-mail automaticamente. |
| HIBP indisponível | resultado `unavailable` da operação e conectividade com Range API | Verificar DNS, TLS, timeout e user agent. Cadastro local permanece fail-closed; cadastro Google continua uma alternativa independente. |
| Muitos bloqueios | lifecycle `blocked`, operação `rate_limited`, baseline e janela | Confirmar se o Apache é o único peer confiável e se a cadeia encaminhada representa o cliente. Não apagar contadores para liberar uma origem; a janela expira pelas regras configuradas. |
| Job de limpeza falha | `WARN` com `task` e `failureType`; eventos do lease | A falha de uma tarefa não impede as demais. Corrigir a causa e aguardar o próximo ciclo normal; não disparar lote manual sem procedimento específico aprovado. |
| Lease não estabiliza | `rinos.maintenance.lease.events`, heartbeat, owner/session/epoch seguros | Verificar relógio, conectividade global, IDs exclusivos e intervalos. Deve existir uma única sessão responsável; perda de prova suspende novos lotes. |

## Proteção de dados durante o diagnóstico

- Pesquise por correlation ID, operação, resultado, tarefa, categoria e tipo da falha.
- Não pesquise ou indexe logs por e-mail, IP, token, código ou URL de confirmação.
- Não habilite SQL completo, dump de requisição, corpo SMTP ou log de credencial.
- Preserve somente o recorte temporal necessário e siga a retenção definida pela infraestrutura.
- Trate eventual PII encontrada em log como incidente separado e remova a causa antes de ampliar a coleta.

## Encerramento e escalonamento

O incidente pode ser encerrado somente quando:

1. o mecanismo normal confirma startup e versão estrutural compatíveis;
2. a rota interna e a rota HTTPS pública respondem pelo caminho esperado;
3. o sinal que abriu o alerta cessou pela janela definida;
4. uma jornada segura confirma o comportamento afetado sem reutilizar dado real de usuário;
5. processos, schemas de teste e diagnósticos temporários foram removidos;
6. causa, impacto, correção e prevenção ficaram registrados sem segredos.

Escale ao desenvolvimento quando houver invariantes quebradas, resultado público incoerente,
falha reproduzível com configuração válida ou necessidade de novo update incremental. Escale ao
provedor quando a aplicação produzir requisição válida e o serviço externo permanecer degradado.

## Referências

- [Operação e observabilidade do cadastro](../specs/user-registration/operations.md)
- [Contratos externos do cadastro](../specs/user-registration/contracts/external-services.md)
- [Scripts, migrations e intervenção externa](../architecture/database-scripts.md)
- [Preparação para produção](../../README.md#preparação-para-produção)
