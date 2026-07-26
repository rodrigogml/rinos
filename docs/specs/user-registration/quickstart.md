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
5. Atingir o limite máximo configurado para uma origem.
6. Repetir com outro token Turnstile válido na mesma origem.
7. **Expected**: novas tentativas dessa origem continuam bloqueadas até a janela terminar, mesmo com Turnstile válido, e exibem o tempo restante; outra origem continua operável.

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

1. Criar cadastro pendente e comprovar controle do e-mail para a operação de cancelamento.
2. Confirmar cancelamento na UI.
3. **Expected**: credencial e comprovações são inutilizadas, o cadastro não pode ser ativado e o e-mail pode iniciar novo processo; permanece somente tombstone sem PII pelo prazo definido.

## Scenario 15: Expiração automática

1. Criar cadastro pendente com mais de 15 dias usando relógio controlado.
2. Executar o job diário duas vezes, inclusive em duas instâncias concorrentes.
3. **Expected**: dados pendentes são removidos uma única vez, usuário ativo nunca é excluído e falhas individuais são observáveis sem interromper silenciosamente o lote.

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
