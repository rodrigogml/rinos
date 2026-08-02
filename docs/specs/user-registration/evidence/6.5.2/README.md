# Evidências da tarefa 6.5.2

Data da validação: 2026-08-02

## Consequência e ação

A confirmação apresenta, antes dos campos e da ação principal, que uma prova válida:

- exclui definitivamente o cadastro pendente;
- invalida links e códigos de ativação;
- libera o e-mail para um novo cadastro;
- produz uma ação que não pode ser desfeita.

A ação principal permanece nomeada **Confirmar cancelamento**, sem rótulo genérico como “Continuar”, “Enviar” ou
“OK”. O texto não pressupõe que uma pendência exista: ele descreve o efeito condicionado a uma prova válida.

## Evolução da RFW Platform

A lacuna de conteúdo era compartilhada pelo renderer padrão. A correção foi implementada no commit
`5ef0600dd7a7e10db26c321522cb143e6a6132af`, com:

- textos próprios nos bundles português, inglês, espanhol, francês, italiano e chinês simplificado;
- teste de ordem entre consequência e ação;
- contrato exato do conteúdo pt-BR;
- atualização equivalente das seis versões do guia de lifecycle no showroom.

Validação isolada:

```text
RFW Platform: 314 testes; 0 falhas; 0 erros; 0 ignorados
RFW Showroom:  21 testes; 0 falhas; 0 erros; 0 ignorados
```

## Validação no Rinos

O teste de integração da plataforma percorre a solicitação neutra, entra na confirmação e verifica tanto a
consequência localizada quanto o nome acessível da ação.

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest' test
```

```text
18 testes; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

Gate completo:

```text
Testes unitários: 321; 0 falhas; 0 erros; 0 ignorados
Testes de integração: 56; 0 falhas; 0 erros; 10 E2E opt-in ignorados
BUILD SUCCESS
```
