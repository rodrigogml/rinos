# Quickstart: Autenticação e Recuperação do Usuário

Cenários de validação end-to-end. UI Vaadin, facades, backend e MySQL permanecem reais. Google, Turnstile e SMTP usam
servidores locais controláveis nos testes automatizados; integrações reais recebem smoke tests separados.

## Scenario 1: Login por senha sem MFA

1. Preparar usuário `ACTIVE`, credencial local válida e aceites legais vigentes.
2. Abrir `/login` em navegador real, informar e-mail/senha e não marcar “lembrar-me”.
3. Submeter e seguir o callback RFW até `/user`.
4. **Expected**: senha não permanece na UI; existe uma única `AuthSession` normal, o identificador HTTP foi renovado,
   o principal não possui tenant/role/permission e o painel mostra somente o próprio usuário.
5. Avançar o relógio além de 30 minutos sem atividade ou 12 horas absolutas.
6. **Expected**: o guard encerra o contexto e exige nova autenticação.

## Scenario 2: Falha neutra e Turnstile progressivo

1. Executar tentativas para e-mail inexistente, senha incorreta e usuário bloqueado.
2. **Expected**: conteúdo, status público e navegação não revelam a causa; nenhuma sessão é criada.
3. Produzir três falhas no mesmo e-mail informado, distribuídas em IPs diferentes, dentro de 15 minutos.
4. **Expected**: Turnstile torna-se obrigatório para esse identificador.
5. Produzir três falhas para usuários diferentes no mesmo IP.
6. **Expected**: Turnstile torna-se obrigatório para essa origem.
7. Esperar menos de 15 minutos, falhar novamente e depois avançar 15 minutos sem falha.
8. **Expected**: a janela é estendida pela nova falha e liberada somente após o intervalo sem falha.

## Scenario 3: Turnstile, proxy e indisponibilidade

1. Quando obrigatório, submeter sem token, token reutilizado, hostname/action divergente e token válido.
2. **Expected**: apenas o válido pode alcançar a autenticação e o widget é renovado após rejeição.
3. Forjar `Forwarded`/`X-Forwarded-For` diretamente em peer não confiável.
4. **Expected**: o IP da conexão continua sendo a origem contabilizada.
5. Simular timeout Siteverify.
6. **Expected**: login afetado falha fechado sem indicar se o usuário existe; outro método não sujeito ao desafio não
   é inventado como bypass.

## Scenario 4: Passkey descobrível

1. Preparar usuário com passkey ativa e user verification.
2. Na tela de login, iniciar passkey sem informar e-mail.
3. Concluir `navigator.credentials.get` com challenge, RP ID, origin e assinatura válidos.
4. **Expected**: a assertion entra no orquestrador; usuário/aceites são revalidados; somente então a sessão é criada.
5. Repetir a assertion ou alterar challenge/origin.
6. **Expected**: rejeição sem nova sessão.
7. Simular cancelamento do navegador.
8. **Expected**: foco e mensagem permitem escolher outro método; a passkey não é revogada.

## Scenario 5: Google vinculado e coincidência de e-mail

1. Autenticar ID token válido de `(issuer, sub)` já vinculado a usuário ativo.
2. **Expected**: o fluxo reconhece o vínculo, registra `GOOGLE` e aplica MFA/legal antes da sessão.
3. Repetir com e-mail igual ao de usuário ativo, mas `issuer + sub` não vinculado.
4. **Expected**: nenhum vínculo e nenhuma sessão são criados; o usuário deve autenticar a identidade Rinos para
   realizar vínculo explícito.
5. Alterar o e-mail retornado pelo Google mantendo `(issuer, sub)`.
6. **Expected**: o vínculo continua estável e o e-mail principal Rinos não muda.

## Scenario 6: MFA por TOTP e e-mail

1. Preparar usuário com MFA voluntário e TOTP ativo.
2. Validar a senha e receber desafio sem `SecurityContext` autenticado.
3. Informar TOTP válido; repetir o mesmo código no mesmo time-step.
4. **Expected**: primeiro consumo conclui no máximo uma sessão; replay é rejeitado.
5. Em novo fluxo, selecionar código por e-mail.
6. **Expected**: envio ocorre depois do commit, destino é mascarado e nova emissão invalida a anterior.
7. Tentar OTP expirado, reutilizado e além do limite.
8. **Expected**: nenhum cria sessão; respostas não expõem estado interno.

## Scenario 7: Sessão persistente, revogação e múltiplas instâncias

1. Autenticar marcando “lembrar-me” e confirmar cookie persistente e `AuthSession.remembered=true`.
2. Encerrar o navegador, reabrir antes dos limites e acessar rota protegida.
3. **Expected**: cookie é validado/rotacionado, sessão local é reconstruída e o mesmo registro global permanece
   reconhecível.
4. Em outra instância/sessão, revogar a primeira pela referência de gestão.
5. Enviar nova interação na instância original.
6. **Expected**: guard observa revogação no MySQL, limpa contexto/cookie e nega imediatamente.
7. Avançar além de sete dias inativos ou 30 dias absolutos.
8. **Expected**: restauração falha e o cookie é removido.

## Scenario 8: Redefinição de senha

1. Solicitar recuperação para e-mail existente e inexistente.
2. **Expected**: resposta pública é equivalente; somente identidade elegível recebe prova de uma hora.
3. Consumir prova válida com senha compatível e não comprometida.
4. **Expected**: hash é substituído, provas anteriores e todas as sessões são invalidadas, evento/notificação são
   produzidos e a prova não funciona novamente.
5. Repetir com usuário sem senha local.
6. **Expected**: nenhuma senha é criada silenciosamente e a orientação não revela métodos a terceiros.

## Scenario 9: Enrollment, recuperação e último método

1. Em sessão recentemente reautenticada, iniciar TOTP.
2. **Expected**: QR/URI e segredo são apresentados uma única vez; fator permanece `PENDING` até código válido.
3. Confirmar, gerar 10 códigos de recuperação e fechar o diálogo.
4. **Expected**: segredo/códigos não podem ser consultados novamente; somente cifra/hash existe no banco.
5. Consumir um recovery code duas vezes.
6. **Expected**: apenas a primeira operação produz efeito.
7. Regenerar o conjunto e tentar código antigo.
8. **Expected**: todo o conjunto anterior está inválido.
9. Tentar remover o último método inicial ou fator exigido de administrador.
10. **Expected**: backend impede a operação mesmo que a UI seja contornada.

## Scenario 10: Reautenticação e garantia administrativa

1. Em sessão com autenticação forte há menos de 15 minutos, iniciar operação compatível.
2. **Expected**: não há desafio redundante.
3. Avançar além de 15 minutos e repetir.
4. **Expected**: o RFW abre métodos permitidos e só continua após prova válida.
5. Em sessão iniciada por Google, tentar elevar com código enviado ao mesmo e-mail.
6. **Expected**: método não é oferecido/aceito; TOTP ou passkey com user verification é exigido.
7. Confirmar passkey válida.
8. **Expected**: `lastStrongAuthAt` é atualizado somente na sessão atual e nenhuma permissão é criada.

## Scenario 11: Alteração de métodos e política de sessões

1. Criar duas sessões reconhecidas e adicionar/remover uma passkey ou vínculo Google.
2. Escolher manter sessões reconhecidas.
3. **Expected**: mudança é auditada e somente sessões exigidas pela política de risco são revogadas.
4. Repetir escolhendo encerrar todas.
5. **Expected**: todas as sessões, inclusive a atual quando aplicável, deixam de funcionar.
6. Alterar senha ou concluir recuperação.
7. **Expected**: não há opção de preservar; todas as sessões são invalidadas.

## Scenario 12: Gate legal em todos os métodos

1. Preparar usuário com credenciais válidas e publicar nova versão obrigatória depois de seu último aceite.
2. Tentar login por senha, Google e passkey em execuções separadas.
3. **Expected**: todos comprovam identidade, mas nenhum cria/libera sessão plenamente autenticada antes da
   continuação legal.
4. Aceitar a versão vigente.
5. **Expected**: nova evidência é inserida, as anteriores permanecem imutáveis e exatamente uma sessão é criada.
6. Recusar/cancelar.
7. **Expected**: nenhuma sessão é criada e nenhum aceite anterior é alterado.

## Scenario 13: Roundtrip UI Vaadin -> RFW -> Facade -> MySQL

1. Submeter login real pela UI, sem chamar service/repository no teste.
2. Observar o provider RFW chamar facade pública com DTO efêmero.
3. Confirmar que facade delega ao service transacional e devolve VO sem entity/segredo.
4. Consultar o schema por fixture e validar fluxo, prova, sessão e evento.
5. **Expected**: nomes, estados, validade e uso único permanecem coerentes nas quatro bordas.

## Scenario 14: Human Interaction Roundtrip

1. Em telefone e desktop, concluir login por senha+TOTP e depois por passkey usando somente teclado e leitor de tela.
2. Forçar erro, expiração, cancelamento WebAuthn e reenvio de OTP.
3. Abrir configurações de segurança, cadastrar TOTP, salvar códigos, reconhecer/revogar sessão e voltar ao painel.
4. **Expected**: foco inicial e de erro é previsível; busy/erro são anunciados; QR possui alternativa textual; nenhum
   segredo reaparece; toda jornada termina ou oferece saída segura sem mouse.

## Scenario 15: Concorrência, replay e restauração

1. Consumir simultaneamente o mesmo OTP, recovery code e prova de recuperação em duas instâncias.
2. **Expected**: cada artefato produz no máximo um efeito.
3. Bloquear o usuário durante uma sessão ativa e uma confirmação de MFA concorrente.
4. **Expected**: nenhuma nova sessão é criada e a existente é negada no próximo guard.
5. Restaurar snapshot contendo sessões/provas já vencidas pelos timestamps persistidos.
6. **Expected**: nenhuma é aceita; o job posterior apenas remove os registros.
