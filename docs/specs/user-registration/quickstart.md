# Quickstart: Cadastro e Ciclo Inicial do Usuário

Cenários de validação end-to-end para a futura implementação. Provedores externos devem ser representados por servidores locais controláveis nos testes de integração; o backend, MySQL, UI e facades usados no cenário permanecem reais.

## Scenario 1: Cadastro local e ativação

1. Iniciar a aplicação com MySQL global vazio, documentos legais vigentes, SMTP de teste e Turnstile de teste configurados.
2. Abrir o cadastro público em navegador real.
3. Informar e-mail novo, senha válida, aceites obrigatórios e desafio válido.
4. Submeter e capturar a mensagem enviada pelo dispatcher SMTP.
5. Abrir o link de comprovação recebido.
6. Consultar o estado pela facade real.
7. **Expected**: existe exatamente um `User.ACTIVE`, uma credencial local ativa, consentimentos das versões vigentes, comprovação usada e nenhum tenant, conta, grupo, papel ou concessão de conta.

## Scenario 2: Dados inválidos não criam pendência

1. Submeter e-mail inválido, senha abaixo da política e aceite obrigatório ausente.
2. **Expected**: a UI associa cada erro ao campo correspondente, preserva foco e não existe `User`, `Registration`, credencial ou consentimento no banco.

## Scenario 3: Senha comprometida ou verificador indisponível

1. Configurar o servidor Pwned Passwords local para retornar o sufixo da senha submetida.
2. Tentar o cadastro local.
3. **Expected**: a senha é rejeitada com explicação e nenhuma escrita é criada.
4. Configurar timeout do mesmo serviço e repetir com outra senha.
5. **Expected**: o cadastro falha fechado, orienta nova tentativa e não persiste identidade.

## Scenario 4: Concorrência do mesmo e-mail

1. Disparar duas submissões simultâneas com grafia/capitalização equivalente do mesmo e-mail.
2. Aguardar ambos os casos de uso concluírem.
3. **Expected**: existe uma única identidade pendente, um único processo e uma única credencial vigente; a resposta funcional não expõe stack trace nem erro de constraint.

## Scenario 5: E-mail já ativo

1. Preparar um usuário ativo.
2. Submeter novo cadastro com o mesmo e-mail normalizado.
3. **Expected**: nenhuma nova identidade é criada; a UI informa explicitamente que o e-mail já existe e oferece o caminho para recuperação de senha, sem revelar outro dado.

## Scenario 6: Reenvio e replay

1. Criar cadastro local pendente e capturar o primeiro link.
2. Solicitar nova comprovação e capturar o segundo link.
3. Abrir o primeiro link.
4. **Expected**: comprovação rejeitada como inválida e oferecido novo envio.
5. Abrir o segundo link duas vezes.
6. **Expected**: a primeira abertura ativa exatamente uma identidade; a segunda retorna resultado idempotente sem repetir ativação ou efeitos.

## Scenario 7: Limite de reenvio

1. Em cadastro pendente, solicitar comprovação até atingir três novas solicitações dentro de 15 minutos.
2. Solicitar novamente antes do fim da janela.
3. **Expected**: nenhum novo token é emitido, o tempo restante é informado e credenciais/e-mail não são expostos.
4. Avançar o relógio de teste além da janela e repetir.
5. **Expected**: nova emissão é permitida.

## Scenario 8: Turnstile e limite por origem

1. Configurar limiar Turnstile zero e submeter sem token.
2. **Expected**: nenhuma escrita ocorre e a UI pede renovação do desafio.
3. Submeter token de teste válido uma vez e reutilizá-lo em outra tentativa.
4. **Expected**: somente a primeira validação pode prosseguir.
5. Com os valores padrão, criar 20 novas pendências de cadastro local na mesma origem dentro da janela de 24 horas.
6. Confirmar que rejeições anteriores à persistência, retomadas, reenvios, cancelamentos e repetições idempotentes não aumentaram o contador.
7. Tentar criar a vigésima primeira pendência com outro token Turnstile válido na mesma origem.
8. **Expected**: a nova criação permanece bloqueada até a janela terminar, mesmo com Turnstile válido, e exibe o tempo restante; outra origem continua operável.
9. Avançar o relógio para mais de 30 dias depois do fim da janela e executar a limpeza.
10. **Expected**: o registro com IP foi excluído e nenhuma auditoria permanente ou log comum conserva o endereço.

## Scenario 9: Proxy não confiável

1. Enviar cabeçalhos de IP encaminhado diretamente a uma instância sem origem pertencente à allowlist de proxies.
2. Repetir tentativas alterando o cabeçalho.
3. **Expected**: todas são contabilizadas pela conexão real e o cliente não burla os limites.
4. Repetir por proxy explicitamente confiável.
5. **Expected**: a cadeia é interpretada conforme a política configurada, sem confiar em saltos não autorizados.

## Scenario 10: Cadastro Google novo

1. Iniciar o fluxo Google e concluir o callback do Google Identity Services com nonce e ID token válidos, contendo e-mail verificado sem usuário correspondente.
2. Confirmar os documentos legais na UI com e-mail bloqueado.
3. **Expected**: um usuário ativo é criado com `ExternalIdentity` única por `issuer + sub`, sem senha local e sem confirmação adicional por e-mail; tokens Google não aparecem no banco ou logs.

## Scenario 11: Google reutiliza pendência

1. Criar cadastro local pendente e preservar seu link de confirmação.
2. Concluir cadastro Google válido com o mesmo e-mail normalizado.
3. **Expected**: a mesma identidade é ativada, a credencial local e todas as comprovações anteriores são invalidadas/removidas antes do vínculo externo.
4. Abrir o link local antigo.
5. **Expected**: nenhum acesso ou efeito é produzido.

## Scenario 12: Google encontra usuário ativo

1. Preparar usuário ativo com o mesmo e-mail, sem vínculo Google.
2. Concluir callback Google válido.
3. **Expected**: nenhum vínculo é criado; o resultado exige reautenticação do usuário Rinos na feature futura de autenticação.

## Scenario 13: Mudança de documento legal

1. Criar cadastro pendente aceitando as versões vigentes.
2. Publicar nova versão obrigatória antes da confirmação do e-mail.
3. Abrir a comprovação válida.
4. **Expected**: a ativação não ocorre até que a nova versão seja apresentada e aceita; o aceite anterior permanece rastreável.

## Scenario 14: Cancelamento

1. Criar cadastro pendente e abrir a solicitação de cancelamento com o identificador preenchido.
2. Informar um token Turnstile válido, provocar uma rejeição recuperável do identificador e tentar submeter novamente sem resolver um novo desafio.
3. **Expected**: somente o identificador permanece preenchido; o widget é renovado, o token anterior não pode ser reutilizado e a segunda tentativa não alcança o provider de cancelamento.
4. Voltar ao login e abrir novamente a solicitação.
5. **Expected**: o estado transitório anterior foi descartado e nenhum identificador ou token é restaurado pela UI.
6. Solicitar três provas de cancelamento dentro da janela móvel padrão e tentar uma quarta solicitação.
7. **Expected**: as três emissões criam eventos e despachos; a quarta mantém a mesma resposta pública neutra, mas não cria prova, evento nem mensagem, e registra somente a limitação sanitizada.
8. Avançar o relógio até o evento mais antigo deixar a janela e solicitar novamente.
9. **Expected**: uma nova prova pode ser emitida sem alterar o contador de novos cadastros por origem.
10. Confirmar cancelamento na UI.
11. **Expected**: credencial e comprovações são inutilizadas, o cadastro não pode ser ativado e o e-mail pode iniciar novo processo; permanece somente tombstone sem PII pelo prazo definido.

## Scenario 15: Expiração automática

1. Criar cadastro pendente com mais de 15 dias usando relógio controlado.
2. Iniciar duas instâncias com `instanceId` distintos e disputar atomicamente o mesmo lease.
3. Confirmar que somente a vencedora renova heartbeat e que nenhum lote começa antes dos 10 minutos de estabilização.
4. Executar o job diário duas vezes e ativar concorrentemente um dos cadastros selecionáveis.
5. **Expected**: dados ainda pendentes são removidos uma única vez, usuário ativado nunca é excluído e falhas individuais são observáveis sem interromper silenciosamente o lote.
6. Interromper a coordenadora, avançar o relógio do banco além de quatro horas e permitir que a segunda instância assuma com novo `epoch`.
7. Manter artificialmente um lote da instância antiga em execução e confirmar que ele conclui ou é abortado pelo timeout de cinco minutos.
8. Retomar a instância antiga e executar nova avaliação.
9. **Expected**: nenhum lote da nova coordenadora começa antes dos 10 minutos de estabilização; a sessão antiga não inicia outro lote, não existe sobreposição de escritas e nenhum timeout registra progresso não confirmado.
10. Criar uma `OriginWindow` cuja retenção tenha vencido e executar o catálogo diário de manutenção.
11. **Expected**: a janela é removida em lote próprio sob a mesma liderança, sem interferir na limpeza dos cadastros pendentes.

## Scenario 16: Roundtrip UI Vaadin -> Facade -> MySQL

1. Executar a aplicação e o banco reais; abrir a view de cadastro em navegador.
2. Submeter o formulário pela UI, sem chamar repository ou service diretamente.
3. Capturar o VO retornado pela facade Java à view e confirmar que contém apenas estado público, próxima ação e tempo aplicável — nunca entity, e-mail de terceiro, hash ou token.
4. Consultar o banco por fixture de teste e verificar o modelo de [data-model.md](./data-model.md).
5. **Expected**: nomes, enums e estados da facade permanecem coerentes com a persistência e a UI apresenta o resultado sem conversão silenciosa.

## Scenario 17: Human Interaction Roundtrip

1. Em viewport de telefone e depois desktop, concluir o cadastro local usando somente teclado e leitor de tela.
2. Forçar expiração do Turnstile durante o preenchimento e renová-lo.
3. Continuar a operação real até o e-mail de teste e abrir a confirmação.
4. **Expected**: e-mail e aceites legais permanecem preenchidos; senha, confirmação, token e outras provas são descartados e precisam ser reinformados; foco e erros são anunciados, a identidade fica ativa e o próximo acesso possível é somente o próprio Painel de Usuário.

## Scenario 18: Falha SMTP e recuperação por reenvio

1. Configurar o servidor SMTP de teste para falhar ou exceder o timeout depois que o cadastro local for confirmado no banco.
2. Submeter um cadastro válido.
3. **Expected**: a pendência e sua comprovação existem, nenhuma identidade está ativa, a UI não afirma que o e-mail foi enviado e oferece retomada e reenvio.
4. Retomar o cadastro, restabelecer o SMTP e solicitar nova comprovação.
5. **Expected**: a comprovação anterior é invalidada, uma nova mensagem é aceita pelo SMTP e o novo link permite uma única ativação.
6. Verificar banco, logs e eventos.
7. **Expected**: não existe outbox, token recuperável, URL secreta ou mensagem renderizada persistida.

## Scenario 19: Calibração Argon2id para produção

1. No mesmo perfil de servidor, Java e limites de recursos da produção, configurar o piso de 19.456 KiB, duas iterações, paralelismo um, salt de 16 bytes e hash de 32 bytes.
2. Executar a ferramenta de calibração depois do aquecimento da JVM e coletar no mínimo 50 operações sem registrar senha ou hash.
3. Ajustar somente memória ou iterações, sem reduzir o piso, até alcançar a faixa definida.
4. **Expected**: mediana entre 500 ms e um segundo, percentil 95 de até 1,5 segundo e hashes válidos com identificador e parâmetros codificados.
5. Registrar hardware, JVM, parâmetros, data e resultados conforme o checklist do `README.md`.
6. **Expected**: o gate impede a liberação quando o piso ou o limite de latência não é atendido.

## Scenario 20: SLO SMTP nominal e smoke test real

1. Configurar um servidor SMTP local controlado, elevar o limite antifraude do perfil de teste para ao menos 100 novas pendências por origem e usar verificação humana controlada.
2. Executar 100 cadastros nominais, registrando o instante do commit e da aceitação de cada mensagem.
3. **Expected**: ao menos 95 mensagens são aceitas em até dois minutos depois do respectivo commit; falhas são contabilizadas sem duplicar ou ativar identidades.
4. Restaurar o perfil antifraude padrão e confirmar separadamente o limite de 20 e o Turnstile real conforme o Scenario 8.
5. Configurar o SMTP real da instalação e realizar um único cadastro de smoke test com destinatário operacional autorizado.
6. **Expected**: a mensagem é aceita e a evidência confirma somente conectividade e configuração, sem declarar throughput, carga máxima ou quantidade de usuários simultâneos.
