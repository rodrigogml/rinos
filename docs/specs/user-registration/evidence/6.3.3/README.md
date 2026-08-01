# Evidência da tarefa 6.3.3

Data da validação: 2026-08-01

## Escopo

A continuação do cadastro Google foi validada quanto ao catálogo jurídico apresentado e à minimização dos dados da
interface. A implementação de produção já era composta pelo `RinosAccessComponentFactory` sobre o renderer público
do RFW; a tarefa consolidou esse contrato por testes sem criar formulário paralelo nem alterar o submódulo.

Esta tarefa cobre a apresentação dos documentos e a ausência de senha ou dados adicionais do perfil Google.
Conclusão, autenticação e navegação pertencem à tarefa 6.3.4; estados de erro pertencem à 6.3.5; acessibilidade,
localização e inspeção visual por form factor permanecem nas tarefas 6.3.6 e 6.3.7.

## Contrato comprovado

O cenário integrado parte de uma identidade verificada que contém `subject`, `issuer`, nome e URL de foto. O adapter
real reduz esses dados à continuação pública necessária, e o componente recebe somente a challenge opaca e o catálogo
jurídico vigente:

```text
identidade Google validada com claims adicionais
  → RFWExternalIdentityResolverAdapter
  → continuação pública minimizada
  → RFWAccessComponent.openExternalRegistration
  → e-mail verificado somente leitura
  → Termos, Privacidade e Marketing opcional
```

Os documentos são fornecidos pela facade pública do Rinos. A factory converte suas referências para
`RFWLegalDocumentVO`, preserva a ordem Termos, Privacidade e Marketing e produz links internos no formato
`/legal-document/{referência}`. O renderer do RFW não solicita senha local, confirmação de senha, nome, foto ou
qualquer outra informação do perfil Google.

A referência opaca da continuação, o identificador do provedor, o `subject`, o `issuer` e os claims adicionais não são
renderizados. Na submissão, o contrato existente envia somente a referência opaca e os identificadores dos documentos
aceitos; ele não aceita senha nem dados adicionais do perfil.

## Validação automatizada

O cenário em `RFWPlatformIntegrationTest` comprova que:

- a etapa contém exatamente os três documentos vigentes configurados, inclusive Marketing opcional;
- os três links apontam para as rotas públicas internas correspondentes;
- existe um único `EmailField`, preenchido com o e-mail verificado e bloqueado para edição;
- não existem `PasswordField` nem `TextField` adicionais;
- `subject`, `issuer`, nome, foto e referência opaca não aparecem no conteúdo renderizado.

O teste do `RFWExternalIdentityResolverAdapter` continua comprovando separadamente que claims excedentes não cruzam
a API pública do Rinos.

Teste focal:

```powershell
mvn "-Dtest=RFWPlatformIntegrationTest,RFWExternalIdentityResolverAdapterTest" test
```

Resultado:

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:      293; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   49; 0 falhas; 0 erros; 4 E2E de navegador opt-in
BUILD SUCCESS
```

Os quatro E2E opt-in são as jornadas de navegador existentes. A inspeção visual e responsiva da continuação Google
será executada na tarefa 6.3.7.

## Conclusão

O Rinos apresenta somente o e-mail verificado e os aceites jurídicos necessários para concluir um novo cadastro
Google. Nenhuma senha local ou informação adicional do perfil é solicitada ou exposta. A tarefa 6.3.3 está concluída
sem mudança no código de produção ou no submódulo RFW.
