# Documentos jurídicos do Rinos

Esta pasta organiza a elaboração, revisão, aprovação e publicação dos textos jurídicos
apresentados pela aplicação. Ela não substitui a revisão de profissional habilitado nem
autoriza a publicação automática de qualquer rascunho.

> [!CAUTION]
> Arquivos em `drafts/` são material de trabalho, contêm lacunas e não podem ser carregados
> no banco, exibidos como documento vigente ou vinculados a um aceite de usuário.

## Organização

```text
docs/legal/
├── README.md
├── drafts/
│   ├── privacy-policy.md
│   └── terms-of-use.md
└── approved/
    ├── privacy-policy/<version>.md
    ├── subprocessors-and-international-transfers/<version>.md
    └── terms-of-use/<version>.md
```

A pasta `approved/` deve ser criada somente quando houver uma primeira versão efetivamente
revisada e aprovada. Cada arquivo aprovado contém exatamente o Markdown público que será
persistido e apresentado, sem avisos internos, comentários de revisão ou campos pendentes.

## Responsáveis e gates

| Etapa | Responsabilidade | Resultado |
|-------|------------------|-----------|
| Produto | Descrever o serviço real, fluxos, integrações, retenções e público | Contexto factual completo |
| Privacidade e segurança | Inventariar dados, finalidades, agentes, compartilhamentos, retenções e controles | Mapa de tratamento validado |
| Jurídico especializado | Escolher bases legais, cláusulas, controlador, foro e redação final | Parecer e texto aprovados |
| Responsável pelo projeto | Autorizar expressamente versão e início de vigência | Versão liberada |
| Desenvolvimento | Persistir o conteúdo exato, seu SHA-256 e a vigência; publicar a rota de leitura | Documento vigente e rastreável |

Nenhum agente de desenvolvimento pode declarar um documento como aprovado apenas porque
preencheu o esboço ou porque os testes técnicos passaram.

## Informações que ainda precisam ser definidas

Antes da primeira aprovação, confirmar no mínimo:

1. pessoa física ou jurídica responsável pela oferta hospedada do Rinos, com nome, CPF/CNPJ,
   endereço e canais de contato;
2. controlador dos dados de identidade e segurança da aplicação;
3. papel do operador da instância hospedada diante dos dados inseridos por cada conta/tenant;
4. encarregado ou canal equivalente para privacidade e exercício de direitos;
5. público permitido, idade mínima e tratamento aplicável a crianças e adolescentes;
6. território atendido, legislação aplicável e solução de conflitos;
7. condições dos planos gratuitos e futuros planos pagos;
8. níveis de serviço, suporte, indisponibilidade e política de encerramento;
9. fornecedores efetivamente usados em produção e eventual transferência internacional;
10. inventário e prazo de retenção de dados ativos, auditoria, segurança, suporte e tenant;
11. licenças do código aberto e distinção entre o software distribuído e o serviço hospedado;
12. procedimento de comunicação de incidentes e atendimento aos titulares.

> [!IMPORTANT]
> O aceite dos Termos de Uso e a ciência da Política de Privacidade não tornam o
> consentimento a base legal automática de todo tratamento. A versão final deve identificar
> a base adequada para cada finalidade. Na interface, prefira “aceito” para os Termos e
> “li e estou ciente” para a Política, salvo quando houver consentimento específico, livre,
> informado, inequívoco e revogável.

## Escopo esperado dos Termos de Uso

O documento deve tratar, em linguagem clara:

- identidade do fornecedor e objeto do serviço;
- requisitos de elegibilidade e capacidade;
- criação de usuário, segurança da credencial, 2FA e responsabilidade pela conta;
- identidade global e participação independente em múltiplas contas/tenants;
- permissões, convites, administradores e responsabilidade por acessos concedidos;
- plano padrão gratuito, franquias e condições de futuros planos pagos;
- conteúdo e dados inseridos pelo usuário, titularidade e licenças estritamente necessárias;
- uso aceitável, atos proibidos, abuso, automação e tentativa de violação;
- integrações de terceiros e responsabilidades nas decisões de conciliação ou automação;
- disponibilidade, manutenção, suporte e correção de erros;
- suspensão, cancelamento, exclusão, exportação e efeitos do término;
- propriedade intelectual, marcas e diferença entre licença open source e serviço hospedado;
- garantias, limitações de responsabilidade e indenização compatíveis com a legislação;
- alteração dos termos, versionamento, nova apresentação e vigência;
- legislação, solução de conflitos e canais de contato.

## Escopo esperado da Política de Privacidade

O documento deve informar, por operação de tratamento:

- quem são controlador, operador, encarregado e demais agentes;
- categorias de titulares e de dados pessoais;
- origem dos dados, inclusive cadastro local, Google, logs, integrações e tenants;
- finalidades específicas e respectivas bases legais;
- dados obrigatórios e consequências de não fornecê-los;
- decisões ou recomendações automatizadas e como solicitar revisão, quando aplicável;
- fornecedores, destinatários, compartilhamentos e transferências internacionais;
- cookies, armazenamento local e tecnologias equivalentes;
- prazos ou critérios de retenção e procedimentos de eliminação;
- controles gerais de segurança sem divulgar detalhes exploráveis;
- direitos do titular, autenticação do pedido e canais de atendimento;
- tratamento de dados de crianças e adolescentes, se permitido;
- comunicação de incidentes com risco ou dano relevante;
- versionamento, vigência e forma de comunicar mudanças.

O inventário inicial deve conferir ao menos os dados já previstos no cadastro: e-mail, hash
de credencial local, identificadores externos, decisões sobre documentos, endereço IP de
origem, provas de verificação, eventos de segurança e correlação técnica. Os dados de negócio
dos tenants serão acrescentados conforme os módulos forem especificados.

## Fluxo de aprovação e publicação

1. Copiar o rascunho para uma área de revisão sem remover as marcações pendentes.
2. Validar cada afirmação contra o comportamento implementado e a infraestrutura real.
3. Obter revisão jurídica e registrar externamente quem aprovou e em que data.
4. Remover avisos, instruções e todos os marcadores `[DEFINIR]`.
5. Definir uma versão legível e imutável, inicialmente sugerida como `1.0.0`.
6. Definir `effectiveAt` em UTC. A vigência não deve ser retroativa.
7. Gravar o texto público exato em:
   - `approved/terms-of-use/<version>.md`; ou
   - `approved/privacy-policy/<version>.md`.
8. Criar a alteração de banco correspondente sem modificar updates já publicados:
   - inserir o mesmo conteúdo em `identity_legalDocumentVersion`;
   - calcular o SHA-256 dos bytes UTF-8 exatos do conteúdo;
   - usar `TERMS_OF_USE` ou `PRIVACY_POLICY`;
   - marcar ambos como obrigatórios para o cadastro inicial;
   - refletir a versão no init global e em um novo update global.
9. Validar que a rota pública apresenta exatamente o conteúdo cujo hash está persistido.
10. Testar cadastro, ativação pendente durante troca de versão e consulta histórica.

Uma correção posterior sempre cria nova versão. Conteúdo, tipo, obrigatoriedade, nome e hash
de uma versão já aceita não podem ser alterados. A versão anterior recebe apenas o fim de
vigência definido pelo fluxo administrativo.

## Fontes iniciais de pesquisa

Estas referências orientam a revisão, mas não substituem análise jurídica específica:

- [Lei Geral de Proteção de Dados Pessoais](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm)
- [Marco Civil da Internet](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/lei/l12965.htm)
- [Código de Defesa do Consumidor](https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm)
- [Aviso de Privacidade da ANPD](https://www.gov.br/anpd/pt-br/acesso-a-informacao/aviso-de-privacidade)
- [Direitos dos titulares segundo a ANPD](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados-1/direito-dos-titulares)
- [Materiais educativos e publicações da ANPD](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes)
- [Comunicação de incidente de segurança](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis)
