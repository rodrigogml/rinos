# Rascunho da Política de Privacidade do Rinos

> [!CAUTION]
> RASCUNHO NÃO APROVADO E NÃO VIGENTE. Não apresentar ao usuário, não colher ciência e não
> inserir este conteúdo no banco. Todo marcador `[DEFINIR]` exige decisão e revisão.

## 1. Objetivo e identificação dos agentes

Esta Política explica como dados pessoais são tratados na oferta hospedada do Rinos em
`https://app.rinos.com.br`, operada no Brasil e destinada a pessoas físicas e jurídicas
brasileiras. Ela não se aplica a instalações do código-fonte realizadas ou operadas por
terceiros.

Controlador dos dados de identidade, autenticação, segurança, prevenção de fraude,
contratação e operação técnica da plataforma: **Rinos Sistemas de Informática Ltda.**,
CNPJ **22.695.211/0001-80**, com endereço na Rua Onze de Agosto, 710, Centro,
Campinas/SP, CEP 13050-010. Contato: **[DEFINIR]**.
Canal de privacidade: **privacidade@rinos.com.br**.

Enquanto comprovar seu enquadramento como agente de tratamento de pequeno porte e não
incorrer nas exclusões da Resolução CD/ANPD nº 2/2022, a Rinos adotará a dispensa de
indicação de encarregado prevista no art. 11 dessa norma e manterá o canal acima para
titulares e para a ANPD. O enquadramento deverá ser revisto ao menos anualmente e antes de
mudanças relevantes no porte, no grupo econômico, na escala ou no risco dos tratamentos.
**[VALIDAÇÃO INTERNA: RECEITA BRUTA ANUAL DE ATÉ R$ 4,8 MILHÕES CONFIRMADA
CONTABILMENTE; AUSÊNCIA DE GRUPO ECONÔMICO DECLARADA; ESTIMATIVA INICIAL DE ATÉ 1.000
TITULARES NOS PRIMEIROS 12 MESES, SUJEITA A MONITORAMENTO; DADOS SENSÍVEIS NÃO SERÃO
SOLICITADOS NEM NECESSÁRIOS, MAS PODERÃO APARECER INCIDENTALMENTE NO CONTEÚDO DE CLIENTES;
DADOS DE CRIANÇAS OU ADOLESCENTES TAMBÉM PODERÃO APARECER INCIDENTALMENTE NESSE CONTEÚDO,
SEM FUNCIONALIDADE A ELES DIRIGIDA; AUTOMAÇÕES LIMITADAS A RECOMENDAÇÕES E BLOQUEIOS
TEMPORÁRIOS DE SEGURANÇA COM REVISÃO HUMANA, SEM DECISÃO AUTOMÁTICA FINANCEIRA, CONTRATUAL
OU JURÍDICA RELEVANTE; RECOMENDAÇÕES UTILIZARÃO SERVIÇO EXTERNO DE IA, AINDA SEM FORNECEDOR
DEFINITIVAMENTE CONTRATADO NEM FLUXO DE DADOS DEFINIDO; OPENAI API É A OPÇÃO PLANEJADA;
VALIDAR OS DEMAIS CRITÉRIOS DE ALTO RISCO ANTES DA APROVAÇÃO. A POLÍTICA PRINCIPAL USARÁ
DESCRIÇÃO GENÉRICA, MAS O FORNECEDOR E OS PAÍSES DEVERÃO CONSTAR EM LISTA PÚBLICA ATUALIZADA.
REMOVER ESTA NOTA DA VERSÃO PUBLICADA]**.

Para o conteúdo inserido por pessoas físicas ou jurídicas em suas contas, o respectivo
usuário ou cliente determina as finalidades e os dados que serão tratados, e a Rinos atua
como operadora, limitada à prestação das funcionalidades solicitadas e às instruções
aplicáveis. A Rinos atua como controladora quando define finalidades próprias, como nos
tratamentos de identidade, autenticação, segurança, prevenção de fraude, contratação,
suporte, cumprimento de obrigações legais e operação técnica da plataforma.

As responsabilidades sobre dados inseridos por clientes empresariais também serão
disciplinadas em contrato de tratamento de dados. **[DEFINIR CONTRATO E INSTRUÇÕES DO
CONTROLADOR]**.

## 2. Titulares e dados tratados

Conforme a utilização, o Rinos poderá tratar:

- dados de usuários adultos, representantes, participantes convidados e terceiros cujas
  informações sejam legitimamente inseridas em uma conta;
- dados de identidade e cadastro: e-mail e identificadores internos;
- autenticação local: somente hash não recuperável da senha e parâmetros do algoritmo;
- autenticação externa: provedor, emissor, identificador estável e e-mail verificado;
- segurança: endereço IP, tentativas, bloqueios, provas antiabuso e eventos técnicos;
- evidências legais: documento, versão, decisão e instante;
- comunicações: entregas de e-mail, solicitações relacionadas ao cadastro e preferências
  opcionais de marketing;
- uso e diagnóstico: eventos funcionais, falhas, métricas e correlações técnicas;
- conteúdo dos tenants: **[DETALHAR POR MÓDULO ANTES DA RESPECTIVA LIBERAÇÃO]**;
- suporte: **[DEFINIR DADOS, ACESSO TEMPORÁRIO E RETENÇÃO]**.

O Rinos não solicita dados pessoais sensíveis nem os exige para suas funcionalidades
iniciais. Caso um cliente os inclua incidentalmente em campos livres, anexos ou outros
conteúdos sob seu controle, deverá possuir finalidade e fundamento legal adequados. A Rinos
os tratará somente como operadora, para executar as funcionalidades e instruções aplicáveis,
sem utilizá-los para finalidade própria.

O Rinos poderá produzir estatísticas internas agregadas e efetivamente anônimas para
compreender a utilização e melhorar o serviço. Elas não poderão individualizar usuários,
contas ou terceiros, permitir reidentificação razoável, nem ser produzidas por serviço
externo de analytics. Dados apenas pseudonimizados continuam sujeitos a esta Política.

O sistema não deve registrar senha em texto, token de ativação recuperável, ID token Google
ou token do Turnstile. **[VALIDAR O INVENTÁRIO CONTRA A VERSÃO DE PRODUÇÃO]**.

## 3. Finalidades e bases legais

| Finalidade inicial | Dados principais | Base legal |
|--------------------|------------------|------------|
| Criar, validar e administrar a identidade | E-mail, credencial ou identidade externa, evidências legais | **[DEFINIR]** |
| Proteger usuários e serviço contra abuso | IP, tentativas, bloqueios e eventos | **[DEFINIR]** |
| Entregar e comprovar comunicações operacionais | E-mail, finalidade e estado da entrega | **[DEFINIR]** |
| Cumprir obrigações e exercer direitos | Evidências, auditoria e solicitações | **[DEFINIR]** |
| Operar funcionalidades de cada tenant | Dados definidos pelos módulos e pela conta | **[DEFINIR POR OPERAÇÃO]** |
| Gerar recomendações assistidas por IA externa | Conteúdo mínimo selecionado para a funcionalidade | **[DEFINIR POR OPERAÇÃO E INSTRUÇÃO DO CLIENTE]** |
| Produzir estatísticas internas agregadas e efetivamente anônimas | Eventos de uso minimizados durante o processo de anonimização | **[DEFINIR E VALIDAR PROCESSO DE ANONIMIZAÇÃO]** |
| Enviar marketing opcional | E-mail, registro da escolha e preferências | Consentimento específico, livre e revogável |

O não fornecimento do e-mail, da autenticação e da ciência/aceitação dos documentos
obrigatórios impede a criação da identidade. Outras consequências devem ser explicadas no
momento da coleta.

O recebimento de marketing não será condição para criar ou utilizar a conta. A opção deverá
ser apresentada separadamente e desmarcada por padrão, com cancelamento simples em cada
mensagem e nas preferências da conta. A revogação não afetará comunicações estritamente
operacionais, contratuais ou de segurança.

## 4. Origem dos dados

Os dados podem ser fornecidos diretamente pelo titular, recebidos de uma conta que o convida,
obtidos do provedor Google durante autenticação autorizada, derivados da interação com o
serviço ou enviados por integrações escolhidas pelo usuário ou tenant.

**[DEFINIR COMO A PESSOA É INFORMADA QUANDO O DADO NÃO FOR OBTIDO DIRETAMENTE DELA]**.

## 5. Fornecedores, compartilhamento e transferências

O cadastro foi projetado para utilizar:

- Cloudflare Turnstile, para proteção contra automação;
- Google Identity Services, quando o usuário escolher autenticação Google;
- Forward Email LLC (`forwardemail.net`), como provedor internacional de SMTP para
  comunicações operacionais e, somente para quem aderir, mensagens de marketing. O provedor
  recebe destinatário, remetente, cabeçalhos, conteúdo e metadados necessários à entrega;
  informa manter logs de erros SMTP por 7 dias e registros de mensagens de saída por
  aproximadamente 30 dias. A conta da Rinos deverá configurar retenção de 0 dias para que o
  corpo seja removido após a entrega ou erro permanente. Seus suboperadores declarados estão
  principalmente nos Estados Unidos e também no Reino Unido.
  **[VALIDAR EM PRODUÇÃO A RETENÇÃO DE 0 DIAS, DPA, SUBOPERADORES E MECANISMO DA RESOLUÇÃO
  CD/ANPD Nº 19/2024]**;
- Have I Been Pwned, para consulta de senha comprometida por k-anonimato, enviando somente
  prefixo do hash necessário ao protocolo e nunca a senha;
- OpenAI API, como serviço externo de inteligência artificial planejado para gerar
  recomendações. A OpenAI informa que dados enviados à API não são usados para treinar seus
  modelos por padrão, salvo adesão expressa, e que logs de monitoramento de abuso podem ser
  mantidos por até 30 dias na configuração padrão. **[VALIDAR ENTIDADE CONTRATADA, MODELO,
  ENDPOINTS, DADOS ENVIADOS, PAÍSES, RETENÇÃO DE ESTADO, ELEGIBILIDADE E CONFIGURAÇÃO DE
  ZERO DATA RETENTION, SUBOPERADORES, DPA E MECANISMO DE TRANSFERÊNCIA INTERNACIONAL ANTES
  DA PRODUÇÃO]**.
  A identificação do fornecedor vigente e dos países envolvidos será mantida em lista
  pública separada e vinculada a esta Política: **[DEFINIR URL DA LISTA DE FORNECEDORES E
  TRANSFERÊNCIAS]**;
- infraestrutura própria da Rinos localizada no Brasil, para hospedar a aplicação, o proxy
  e o banco de dados;
- infraestrutura própria da Rinos em outro local físico no Brasil, para manter cópias de
  recuperação separadas do ambiente principal.

No lançamento, as estatísticas internas não utilizarão fornecedor externo de analytics.

Dados serão compartilhados apenas conforme finalidade, necessidade, obrigação ou solicitação
do usuário. **[VALIDAR CONTRATOS, SUBOPERADORES, PAÍSES, MECANISMOS DE TRANSFERÊNCIA
INTERNACIONAL E LINKS PARA POLÍTICAS DOS FORNECEDORES]**.

Integrações bancárias, fiscais, contábeis e outras deverão ser acrescentadas a esta seção
antes de entrarem em produção.

## 6. Cookies e tecnologias semelhantes

**[INVENTARIAR COOKIES, SESSÃO VAADIN, ARMAZENAMENTO LOCAL, GOOGLE E TURNSTILE; indicar
finalidade, duração, domínio e possibilidade de controle.]**

Cookies estritamente necessários devem ser distinguidos de analytics, preferências ou
marketing. Nenhuma categoria opcional deve ser presumida por este rascunho.

## 7. Retenção e eliminação

As definições técnicas iniciais preveem:

- cadastro não ativado: exclusão após 15 dias;
- evidência minimizada de cancelamento pendente: até 15 dias;
- janela de origem/IP usada contra abuso: até 30 dias após o fim da janela;
- token secreto de ativação: não persistido; somente hash e estado até uso ou expiração;
- usuário ativo e consentimentos aplicáveis: **[DEFINIR CRITÉRIO, OBRIGAÇÕES E TÉRMINO]**;
- eventos de segurança e auditoria: **[DEFINIR PRAZOS POR FINALIDADE]**;
- dados de tenant: **[DEFINIR POR CATEGORIA E MÓDULO]**;
- backups mantidos em infraestrutura própria, fisicamente separada e localizada no Brasil:
  **[DEFINIR FREQUÊNCIA, PROTEÇÕES E RETENÇÃO]**.

Os prazos efetivos devem ser confirmados no inventário de dados e compatibilizados com
obrigação legal, exercício regular de direitos, contratos e exclusões tecnicamente
propagáveis.

## 8. Segurança e incidentes

O projeto prevê segregação por tenant, controle de acesso por grupos e chaves, hash de
credenciais, tokens de uso único, proteção contra automação, auditoria sanitizada e
minimização. **[VALIDAR CONTROLES REAIS, RESPONSÁVEIS, BACKUPS, MONITORAMENTO E RESPOSTA]**.

Incidentes capazes de gerar risco ou dano relevante serão avaliados e comunicados conforme
as regras aplicáveis. Canal para comunicar suspeitas: **[DEFINIR]**.

## 9. Direitos dos titulares

O titular poderá solicitar, conforme aplicável:

- confirmação e acesso;
- correção;
- informação sobre compartilhamento;
- anonimização, bloqueio ou eliminação;
- portabilidade, quando regulamentada e cabível;
- revisão de decisões exclusivamente automatizadas;
- informação sobre consentimento e suas consequências;
- revogação do consentimento quando esta for a base utilizada;
- oposição e petição perante autoridades competentes.

Canal: **privacidade@rinos.com.br**.
Procedimento de autenticação do pedido: **[DEFINIR SEM COLETAR DADOS EXCESSIVOS]**.
Prazos de resposta: **[VALIDAR CONFORME TIPO DE PEDIDO E REGULAMENTAÇÃO VIGENTE]**.

## 10. Crianças e adolescentes

O serviço é destinado exclusivamente a pessoas com 18 anos ou mais. No cadastro, o usuário
deverá declarar que possui a idade mínima, sem fornecimento de data de nascimento ou
documento de identidade para essa finalidade.

O Rinos não é dirigido a crianças ou adolescentes. Dados dessas pessoas poderão aparecer
incidentalmente em conteúdo inserido por um cliente, sem que exista funcionalidade a elas
dirigida. Nessa situação, o cliente deverá assegurar finalidade e fundamento legal
adequados, necessidade, proteção integral e melhor interesse, e a Rinos realizará o
tratamento somente como operadora, conforme as instruções aplicáveis.

**[DEFINIR PROCEDIMENTO PROPORCIONAL PARA APURAR E ENCERRAR CADASTRO QUANDO HOUVER
EVIDÊNCIA DE USUÁRIO MENOR DE 18 ANOS, E PARA ATENDER SOLICITAÇÕES RELATIVAS A DADOS DE
MENORES INSERIDOS POR CLIENTES]**.

## 11. Decisões e recomendações automatizadas

O sistema poderá sugerir associações, categorias, dimensões ou conciliações com pontuação,
mas a decisão final permanecerá com o usuário. Mecanismos de prevenção a abuso poderão
aplicar bloqueios automáticos temporários ao cadastro ou ao acesso, com possibilidade de
revisão humana mediante solicitação pelos canais de atendimento.

O Rinos não utilizará decisões exclusivamente automatizadas para produzir efeitos
financeiros, contratuais ou jurídicos relevantes sobre o titular. **[INVENTARIAR CRITÉRIOS,
DURAÇÃO, CANAL E PROCEDIMENTO DE REVISÃO DOS BLOQUEIOS ANTES DA APROVAÇÃO]**.

As recomendações serão geradas com auxílio da OpenAI API, fornecedora planejada, com envio
apenas do conteúdo necessário à funcionalidade. O resultado será uma sugestão sujeita à
decisão humana. **[CONFIRMAR CONTRATAÇÃO, FORMA DE ACIONAMENTO, CONFIGURAÇÕES DE RETENÇÃO E
VEDAÇÃO DE USO DOS DADOS PARA TREINAMENTO OU FINALIDADES PRÓPRIAS ANTES DA PRODUÇÃO]**.

Estatísticas internas destinadas à melhoria do serviço deverão ser agregadas e efetivamente
anônimas, sem perfil individual, identificação de usuário ou conta, marketing direcionado ou
treinamento externo.

## 12. Alterações desta Política

Cada versão possui conteúdo, identificação e início de vigência próprios. Mudanças relevantes
serão comunicadas e, quando exigido, apresentadas novamente antes da continuidade do uso.

Versão proposta: **[DEFINIR]**
Início de vigência: **[DEFINIR EM UTC E FORMATO LEGÍVEL]**

## 13. Contatos

Controlador: **Rinos Sistemas de Informática Ltda., CNPJ 22.695.211/0001-80, Rua Onze de
Agosto, 710, Centro, Campinas/SP, CEP 13050-010**
Privacidade: **privacidade@rinos.com.br**
Encarregado: não indicado enquanto aplicável a dispensa para agente de tratamento de pequeno
porte prevista na Resolução CD/ANPD nº 2/2022. **[VALIDAR ENQUADRAMENTO]**
Segurança: **[DEFINIR]**
Atendimento geral: **[DEFINIR]**
