# Evidência 6.5.6 — acessibilidade e adaptação da confirmação

## Contrato validado

- a descrição irreversível precede campos e ação;
- o foco inicial é solicitado para a prova, inclusive em entrada por deep link;
- erros de campo continuam com precedência no controlador central de foco;
- a prova é obrigatória e declara `autocomplete="one-time-code"`;
- o botão possui o nome inequívoco **Confirmar cancelamento**;
- feedback remoto usa a região `alert` assertiva e atômica do componente;
- a ordem do DOM preserva identificador, prova, ação principal e retorno;
- a ação principal ocupa a largura disponível, controles mantêm alvos touch e ações secundárias quebram linha;
- o card usa largura máxima no desktop e espaçamento reduzido abaixo de 36 rem;
- os textos de resultado inválido, expirado e concluído são resolvidos em pt-BR pela hospedeira.

## Evolução compartilhada

O foco explícito na prova foi implementado no RFW e documentado no showroom no commit `18e8400`. A plataforma foi
validada isoladamente com 317 testes, e o showroom com 21 testes, ambos sem falhas ou erros.

## Limite desta evidência

A inspeção visual e a execução completa no navegador, em telefone e desktop, pertencem ao fechamento da tarefa
6.5.7. Esta tarefa comprova o contrato estrutural que essa execução consome.
