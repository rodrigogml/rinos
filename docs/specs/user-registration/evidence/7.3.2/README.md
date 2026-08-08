# Evidência 7.3.2 — avaliação manual somente por teclado

## Estado

**Bloqueada para conclusão humana.** A execução assistida no JAR real comprovou os contratos que a
ferramenta de navegador consegue observar, mas não substitui a operação sequencial por uma pessoa em
um navegador comum. A tarefa permanece como `release-blocker` e não pode ser declarada concluída a
partir de eventos sintéticos.

## Ambiente da execução parcial

- data: 2026-08-08;
- artefato: `target/rinos-1.0.0.jar`, Vaadin em modo de produção;
- endereço local: `http://localhost:7070`;
- banco: MySQL 9.7.2 local, schema descartável de desenvolvimento `rinos_global`;
- plataforma: RFW 2.0 no commit `dd7554c`;
- navegador: Chromium do navegador integrado do Codex;
- entrada: chamadas semânticas de teclado da automação, sem seleção de ações por coordenadas.

## Resultados observados

| Jornada ou contrato | Resultado parcial |
|---|---|
| Login → cadastro local | A ação `Criar conta` respondeu à tecla Espaço e abriu `INT-WEB-REG-001`. |
| Cadastro vazio | A submissão apresentou estado inválido e moveu o foco para o primeiro campo rejeitado. |
| Login → recuperação | A ação respondeu à tecla Espaço; identificador sintético inexistente produziu a resposta pública neutra, sem exceção no servidor. |
| Ativação manual | `/login?step=activation` iniciou com foco no código; a submissão vazia anunciou `Revise os campos indicados` e preservou o foco no campo rejeitado. |
| Ativação → solicitação de cancelamento | `Cancelar cadastro pendente` respondeu à tecla Espaço; a validação vazia focou o e-mail e anunciou o erro. |
| Confirmação de cancelamento | O deep link removeu a prova da URL visível; a submissão incompleta focou o e-mail e anunciou o erro. |
| Retorno ao login | As ações `Voltar para entrar` responderam à tecla Espaço nas superfícies exercitadas. |

Durante essa execução foi encontrada uma falha real e independente do gate: componentes Spring lazy
da recuperação e do despacho SMTP possuíam construtores de produção ambíguos, e a auto-configuração
SMTP da RFW era avaliada antes de o Spring Boot publicar o `JavaMailSender`. Os contratos foram
corrigidos e cobertos por testes de contexto; a jornada de recuperação passou no JAR depois das
correções.

## Limites da automação

A API de automação localiza o `<input type="checkbox">` nativo, confirma que ele está habilitado,
focável e que responde ao contrato de valor, porém sua operação sintética `press("Space")` não
executa a ação padrão de alternância do navegador. O mesmo checkbox muda normalmente pelo contrato
de controle. Essa divergência da ferramenta não prova defeito no RFW e não autoriza adicionar um
listener de teclado que poderia alternar duas vezes em um navegador real.

Além disso, a continuação Google depende de uma sessão OAuth válida e não estava habilitada no
ambiente local. Os testes automatizados e a matriz visual cobrem seu contrato, mas não substituem o
gate manual desta tarefa.

## Protocolo obrigatório para concluir

Uma pessoa alheia à automação deve executar, sem mouse ou toque, os cinco `INT-WEB-REG-*` e a
recuperação de senha em navegador desktop:

1. iniciar cada jornada com `Tab` e `Shift+Tab`, sem foco preso ou salto inacessível;
2. ativar botões com `Enter` e Espaço e links com `Enter`;
3. marcar e desmarcar cada aceite legal com Espaço, abrir cada documento e retornar sem perda dos
   campos preserváveis;
4. provocar validação vazia e inválida, confirmando foco no primeiro erro e alcance do resumo;
5. renovar o Turnstile quando apresentado sem perder dados permitidos;
6. concluir cadastro, ativação, continuação Google, solicitação e confirmação de cancelamento e
   recuperação com dados controlados;
7. registrar navegador, versão, data, resultado por jornada e qualquer bloqueio ou falha menor.

> [!IMPORTANT]
> A tarefa 7.3.2 só poderá mudar para `[x]` quando esse protocolo tiver evidência humana completa.
> Os resultados assistidos acima reduzem o escopo da verificação restante, mas não aprovam o gate.
