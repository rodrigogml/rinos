# Evidência da fase 5.2 — Central e editores de acesso

## Uso da RFW Platform

Antes da implementação foram revisados `docs/architecture/rfw-platform-usage.md`, README/AGENTS do submódulo e os
guias/laboratórios públicos do showroom para filtering, picker, buttons, banner, toast, i18n e responsive layout.
Não existe componente genérico de matriz ACL ou árvore de domínio na RFW; a central é uma composição Rinos com
`TreeGrid`, `Grid`, `FormLayout`, `Dialog` e `FlexLayout`. Ela reutiliza `RFWPicker`, ações da `UIFactory`, banner,
toast, ícones e tradução públicos. Nenhuma alteração foi feita no submódulo.

## Implementação

- `GlobalAccessCenterView` seleciona contexto global na `UI` exata.
- `TenantAccessCenterView` recebe tenant e membership explícitos e ambos são revalidados pela autorização.
- `AccessCenterComponent` oferece árvore de categorias, pesquisa por nome/descrição e abas autorizadas de grupos,
  participantes e catálogo.
- O editor mantém nome/descrição, participantes temporais, matriz e regras diretas/de grupo, incluindo permissão,
  bloqueio, ausência, futuro, expirado, desativado e indisponível pelo plano.
- Grupos e regras podem ser desativados logicamente; serviços existentes preservam baseline protegida, continuidade,
  histórico, auditoria e invalidação.
- Ações não autorizadas não são exibidas e toda chamada continua reautorizada no adapter Spring.

## Concorrência, isolamento e apresentação

- O snapshot leva a revisão contextual; gravação obsoleta é rejeitada atomicamente antes da mutação.
- A leitura recebe capacidades resolvidas por chave e não consulta seções ocultas.
- O layout usa flex-wrap e passos responsivos do `FormLayout`; grids, árvore, picker, diálogos e botões preservam a
  navegação de teclado nativa do Vaadin/RFW e rótulos textuais para efeitos/estados.
- Todos os textos estão no bundle. Os códigos e as próprias chaves i18n não são renderizados; há fallback humano para
  uma tradução ausente.

## Cobertura automatizada

```text
mvn -q "-Dtest=SpringAuthorizationAdapterTest,SpringAccessAdministrationAdapterTest,AccessAdministrationFacadeImplTest,AccessCenterComponentTest,AccessCatalogI18nTest" test
```

Os testes cobrem autorização por seção, ausência de leitura de repositories ocultos, contexto da UI, ator derivado,
conflito de revisão antes da escrita, composição responsiva, textos humanos e ausência do código técnico.

## Quality gate integrado

O startup empacotado revelou que `@ConditionalOnMissingBean` aplicado diretamente a adapters escaneados não
registrava os fallbacks de planos, capacidade de membership e verificação humana de forma confiável. Os fallbacks
foram movidos para configurações com métodos `@Bean`; serviços persistentes não usados no perfil sem JPA ficaram
lazy, e runners/schedulers persistentes passaram a exigir `DataSource`. Isso preserva falha fechada e permite que os
diagnósticos de configuração anteriores ao banco continuem sendo emitidos.

Resultados finais:

```text
Unitários:   733 testes, 0 falhas, 0 erros
Integração: 133 testes, 0 falhas, 0 erros, 18 ignorados por condição externa
git diff --check: sem erro (somente avisos de normalização LF/CRLF existentes no workspace)
```

As integrações cobrem o JAR real, login por proxy reverso, migrations e repositórios MySQL, inclusive persistência de
access-control, account e membership.
