# Análise de Lacuna RFW — Operação de Criação de Conta

**Data**: 2026-08-15
**Estado**: resolvida no RFW pela revisão `ba1bfda` em 2026-08-15

## Problema

O `RFWTurnstileComponent` aceita a action estável `account-creation`, e o
`RFWHumanVerificationProvider` transporta e valida essa action. Porém,
`RFWHumanVerificationOperationEnum` não possui uma operação de criação de conta; seu catálogo atual contém apenas
login, cadastro de identidade, cancelamento de cadastro e recuperação de senha.

Mapear criação de conta para `REGISTRATION` confundiria dois eventos diferentes: registrar uma identidade global e
fundar um novo tenant. Usar valor livre eliminaria o benefício do catálogo fechado. Portanto, o adapter Rinos deve
permanecer fail-safe e indisponível até existir uma operação pública semanticamente correta.

## API proposta

Adicionar de forma compatível e aditiva ao enum público:

```java
ACCOUNT_CREATION("account-creation")
```

O identificador deve coincidir com a action enviada pelo componente e verificada pelo provider. Nenhum método atual,
assinatura ou comportamento das operações existentes precisa mudar.

## Impacto no RFW

- adicionar o valor ao enum e testes de resolução/serialização;
- documentar a operação no showroom e no contrato de verificação humana;
- acrescentar exemplo/laboratório quando a action puder ser demonstrada;
- atualizar traduções somente se a API pública expuser rótulo humano;
- validar isoladamente a suíte e o showroom antes de atualizar o ponteiro no Rinos.

## Compatibilidade e segurança

A mudança é binariamente aditiva para consumidores comuns. Switches exaustivos em código-fonte precisarão tratar o
novo valor ao recompilar, o que é desejável para evitar fallback silencioso. Indisponibilidade ou operação não
reconhecida continua negando a criação; token, IP e resposta do provedor não são persistidos.

## Decisão e entrega

Após autorização explícita, o ciclo separado adicionou `ACCOUNT_CREATION("account-creation")`, tornou `action()` parte
do contrato público estável, atualizou testes e documentou o uso nos seis idiomas do showroom. Core e showroom foram
validados isoladamente antes da atualização do ponteiro no Rinos. O adapter Rinos usa a operação catalogada e falha de
modo seguro diante de indisponibilidade ou configuração inválida.
