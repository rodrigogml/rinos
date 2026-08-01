# Uso da RFW Platform no Rinos

## Objetivo

A RFW Platform é a fundação obrigatória das interfaces e das capacidades técnicas reutilizáveis do Rinos. O objetivo não é apenas compartilhar dependências: todo componente, protocolo ou padrão que possa beneficiar outras aplicações hospedeiras deve permanecer centralizado no RFW.

> [!IMPORTANT]
> O Rinos compõe e configura o RFW. Não copia componentes da plataforma, não cria uma segunda biblioteca visual e não reimplementa localmente uma capacidade genérica já existente.

## Baseline aprovada

A feature `user-registration` usa a revisão
`6f953eee2310a0c9fd312cdecf0a2f560f07df36` da RFW Platform, publicada como
`br.eng.rodrigogml.rfw:rfw:2.0.0`. O ponteiro Git do submódulo é a fonte executável dessa fixação; a versão Maven
identifica o artefato, mas não substitui a revisão imutável do submódulo.

Essa revisão consolida o namespace público em `br.eng.rodrigogml.rfw.*`, o prefixo de configuração em `rfw.*`, os
recursos web sob `/rfw/` e separa as auto-configurações por capacidade. Ela também mantém o showroom sincronizado com
as coordenadas, as dependências `provided` e o procedimento de migração da RFW 2.0. O ponteiro não deve retroceder
para uma revisão que reintroduza `rfw.platform.*`, `/rfw-platform/` ou uma auto-configuração agregadora legada, mesmo
quando o artefato principal compile.

Ao atualizar essa baseline, registre a nova revisão na documentação da feature somente depois de validar e publicar o
RFW conforme o processo descrito neste documento.

## Fontes obrigatórias antes de trabalhar em interfaces

Todo agente ou desenvolvedor deve consultar, nesta ordem:

1. A versão do submódulo fixada pelo repositório Rinos.
2. `modules/RFW.Platform/README.md`.
3. `modules/RFW.Platform/.agents/AGENTS-DELEGATOR.md` e os agentes especializados aplicáveis.
4. `modules/RFW.Platform/modules/rfw.showroom/AGENTS.md`.
5. A documentação em português do showroom:
   - `showroom/content/getting-started/`;
   - `showroom/content/access/`;
   - `showroom/content/components/`;
   - `showroom/content/ui-factory/`;
   - `showroom/content/theme/`;
   - `showroom/content/i18n/`;
   - demais áreas relacionadas à feature.
6. Os laboratórios Java/Vaadin do módulo `modules/rfw.showroom`.
7. A API pública e os testes do componente candidato dentro de `modules/RFW.Platform/src/`.

Os paths de `showroom/content/` acima são relativos a:

```text
modules/RFW.Platform/modules/rfw.showroom/src/main/resources/
```

O README é uma porta de entrada, mas não substitui o showroom. A documentação viva do showroom e seus exemplos devem ser tratados como referência prioritária de uso da plataforma.

## Divisão de responsabilidades

| RFW Platform | Rinos |
|--------------|-------|
| Componentes reutilizáveis, factories e configurações de apresentação | Rotas, navegação de produto e composição da página |
| Máquina de estados e orquestração genérica de interação | Regras de negócio, persistência e transações |
| Contratos de providers e VOs compartilháveis | Implementações dos providers com o domínio Rinos |
| Protocolos técnicos reutilizáveis, como Turnstile, Google, WebAuthn, TOTP e sessão | Políticas de cadastro, autorização, auditoria e retenção |
| Tokens, temas, classes públicas, slots e renderers | Identidade visual e conteúdo do Rinos por APIs públicas |
| i18n-base dos componentes | Mensagens e traduções específicas do produto |
| Showroom, documentação e laboratórios dos componentes | Testes de integração e jornadas completas do Rinos |

## Integração pública da hospedeira

O RFW descobre suas auto-configurações pelo próprio artefato. O Rinos não importa classes internas, não mantém um
arquivo paralelo de auto-configurações e não recria a `RFWAccessComponentFactory`.

Desde o RFW 2.0, as capacidades antes divididas entre Platform e Kernel estão consolidadas no artefato `rfw`. A
aplicação hospedeira não deve manter dependência, instalação ou import de `rfw.kernel`. A nova hierarquia pública de
falhas fica em `br.eng.rodrigogml.rfw.exception` e distingue validação, configuração, infraestrutura e integração por
tipos e códigos estáveis.

Imports públicos usam `br.eng.rodrigogml.rfw.*`; configurações usam `rfw.*`; recursos estáticos usam `/rfw/`. Não
introduza novamente o segmento `platform` nessas três fronteiras. A RFW registra `RFWAutoConfiguration` como núcleo
mínimo e auto-configurações independentes para autenticação, banco, e-mail, i18n, execução, sessão e UI. O Rinos deixa
a descoberta a cargo do artefato e só referencia auto-configurações específicas em testes de contexto isolado.

As integrações declaradas como `provided` pelo POM do RFW pertencem ao classpath da hospedeira. O Rinos declara
explicitamente apenas o conjunto exigido pelas capacidades que utiliza, incluindo Vaadin, persistência, validação,
e-mail, Spring Security, Google/WebAuthn, contexto Micrometer e renderização Markdown/HTML. Ao ativar outra capacidade,
compare primeiro seu contrato e o POM da revisão fixada; não dependa de uma transitividade acidental de versão anterior.

O App Shell da hospedeira deve carregar explicitamente a folha agregada pública do RFW:

```java
@StyleSheet("context://rfw/styles.css")
public class RinosApplication implements AppShellConfigurator {
}
```

Esse carregamento é obrigatório em produção e nos harnesses de inspeção visual. Sem ele, os componentes continuam
funcionais, mas perdem tokens, temas e estilos estruturais, produzindo uma interface nativa que não representa o
contrato visual da plataforma. Por isso, cada entrypoint Vaadin executável deve declarar o mesmo recurso público. O
Rinos não deve copiar as folhas internas nem substituir esse import por CSS paralelo.

Os padrões globais de idioma, tema e acesso pertencem exclusivamente ao `application.properties` explícito e são
documentados no `application.properties.model`. A configuração inicial declara:

- português do Brasil como único idioma integralmente suportado e como locale textual e de formatação padrão;
- `America/Sao_Paulo` como fuso padrão de apresentação;
- `messages` como basename do produto;
- `rinos` como namespace estável de tema e acesso;
- tema claro, escala e densidade médias, persistência das preferências e aplicação automática;
- seletores de tema e idioma disponíveis e “lembrar-me” desabilitado.

Uma view que hospede o fluxo de acesso deve injetar a factory pública e criar o componente diretamente:

```java
public LoginView(RFWAccessComponentFactory factory) {
  add(factory.create());
}
```

Configurações específicas de uma instância devem usar `RFWAccessComponentConfig`. Slots e renderers somente são
aplicados depois de esgotadas as configurações públicas e não podem substituir regras de negócio nem a máquina de
estados do RFW.

### Providers e capabilities

Providers conectam o RFW aos casos de uso do Rinos, mas não constituem a API de negócio da aplicação. Cada adapter
Spring que implemente um provider do RFW deve depender somente de facades, DTOs e VOs públicos de `br.com.rinos.app.api`
e de contratos compartilhados permitidos. Ele não pode acessar `backend`, entities ou repositories diretamente.

> [!IMPORTANT]
> Não registre providers vazios, provisórios ou que retornem sucesso artificial. O
> `RFWAccessCapabilityService` anuncia uma capability pela presença do provider efetivo; portanto, ausência de
> implementação deve resultar em capability ausente e em funcionalidade não oferecida pela interface.

Implementações concretas serão registradas junto às tarefas dos respectivos casos de uso. A fundação mantém apenas a
factory e os serviços técnicos auto-configurados, permitindo que o conjunto de capabilities cresça conforme contratos
reais forem entregues.

### Pontos de extensão locais

Use os pontos de extensão nesta ordem:

1. properties globais para idioma, tema e comportamento compartilhado;
2. `RFWAccessComponentConfig` para uma instância;
3. slots para conteúdo complementar;
4. renderers documentados para apresentação de uma etapa;
5. adapters de provider apoiados exclusivamente na camada `api`;
6. componente local apenas para comportamento exclusivo do domínio Rinos.

Substituir a `RFWAccessComponentFactory` auto-configurada exige uma necessidade concreta, teste de contexto e
documentação da diferença. Um simples padrão visual ou funcional deve permanecer em properties ou na configuração da
instância.

## Processo obrigatório de decisão

Antes de criar qualquer componente ou comportamento de UI:

1. Pesquisar no showroom e na API pública do RFW.
2. Reutilizar o componente sem customização quando ele atender ao contrato.
3. Aplicar configuração pública quando a diferença for parametrizável.
4. Usar slots para conteúdo complementar.
5. Usar renderer documentado quando a hierarquia de uma etapa precisar mudar sem substituir a máquina de estados.
6. Evoluir o RFW quando a lacuna for genérica ou quando a customização local duplicaria infraestrutura reutilizável.
7. Criar componente estritamente local somente quando o comportamento for exclusivo do domínio Rinos e não couber como extensão pública do RFW.

```text
componente existente
  -> configuração
  -> slot
  -> renderer
  -> evolução do RFW
  -> componente local excepcional
```

> [!CAUTION]
> Um renderer local não deve ser usado para contornar deficiência genérica permanente da plataforma. Se mais de uma aplicação puder precisar da mesma solução, ela pertence ao RFW.

## Regras visuais

- Preferir tokens públicos do RFW antes de sobrescrever seletores.
- Preferir classes e variantes semânticas públicas.
- Não depender da estrutura interna não documentada de componentes Vaadin.
- Manter compatibilidade com temas claro/escuro, densidades, zoom e `prefers-reduced-motion`.
- Novos componentes reutilizáveis devem possuir classes públicas, tokens, documentação e demonstração no showroom.
- Recursos específicos do Rinos podem definir identidade visual, mas não uma segunda arquitetura de tema.

## Regras de interface e acessibilidade

- Reutilizar os estados, feedbacks, tratamento assíncrono e proteções de credenciais já oferecidos pelo RFW.
- Preservar operação por teclado, nomes acessíveis, foco, leitor de tela, reflow e alvos de toque.
- Textos visíveis devem usar i18n; exemplos do showroom indicam os serviços e factories aplicáveis.
- Views do Rinos não devem acessar entities ou repositories; providers/facades ligam o domínio aos contratos do RFW.

## Quando o RFW precisar mudar

Antes de alterar o submódulo, apresentar ao responsável:

- lacuna observada e evidência no showroom/API atual;
- por que configuração, slot ou renderer não resolvem adequadamente;
- contrato público proposto;
- efeito sobre compatibilidade e aplicações hospedeiras;
- testes e documentação necessários;
- atualização esperada no showroom.

Somente depois de autorização explícita a alteração pode começar.

Toda evolução aprovada do RFW deve:

1. seguir as instruções do próprio submódulo;
2. preservar compatibilidade ou documentar a ruptura;
3. incluir testes proporcionais;
4. documentar obrigatoriamente toda alteração dentro do módulo `modules/rfw.showroom`, sem exceção por tamanho ou
   natureza da mudança;
5. atualizar a documentação-base em português e todas as traduções equivalentes;
6. incluir ou atualizar laboratório, bundles e testes do showroom quando a alteração for demonstrável;
7. validar build e, quando visual, executar inspeção no showroom;
8. ser commitada e publicada no repositório do RFW antes de o Rinos atualizar seu ponteiro.

> [!IMPORTANT]
> Código, testes e documentação geral fora do showroom não substituem essa obrigação. Uma alteração no RFW somente é
> considerada concluída quando o showroom informa às aplicações hospedeiras o que mudou, para que serve e como usar.

## Validação no Rinos

Mesmo quando o componente estiver validado no RFW, a integração do Rinos deve comprovar:

- providers e facades corretos;
- regras de negócio e segurança próprias;
- navegação e autorização;
- mensagens e identidade visual;
- responsividade e acessibilidade;
- comportamento com falhas externas;
- ausência de credenciais e dados sensíveis em estado serializável, logs e telemetria.
