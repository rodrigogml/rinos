# Roadmap de Especificações

Este documento organiza a sequência recomendada das especificações funcionais do Rinos. Cada item deve permanecer pequeno o suficiente para permitir requisitos detalhados, critérios de aceite objetivos e implementação incremental.

> [!IMPORTANT]
> A ordem representa dependências funcionais conhecidas, não um compromisso imutável de entrega. Alterações de sequência devem preservar os princípios definidos em `docs/constitution.md`.

## Fundação da Plataforma

| Ordem | ID | Feature | Objetivo | Dependências | Status |
|------:|----|---------|----------|--------------|--------|
| 1 | FND-001 | `user-registration` | Criar a identidade global do usuário e concluir seu ciclo inicial de cadastro | Nenhuma | Especificada e clarificada |
| 2 | FND-002 | `user-authentication` | Autenticar por senha, passkey ou Google, oferecer 2FA, manter sessões, recuperar acesso e proteger o login contra automação e descoberta de usuários | FND-001 | Especificada e clarificada |
| 3 | FND-003 | `user-dashboard` | Disponibilizar ao usuário ativo seu painel e suas configurações pessoais | FND-001, FND-002 | Especificada e clarificada |
| 4 | FND-004 | `account-registration` | Criar e manter contas genéricas (tenants), inicialmente no plano Free, deixando a classificação PF/PJ para evolução futura | FND-003 | Especificada e clarificada |
| 5 | FND-005 | `account-membership` | Associar uma identidade de usuário a múltiplas contas, inclusive por convite | FND-001, FND-003, FND-004 | Especificada e clarificada |
| 6 | FND-006 | `access-control` | Definir grupos e chaves de acesso nos níveis do sistema e da conta | FND-005 | Especificada e clarificada |
| 7 | FND-007 | `tenant-context-isolation` | Selecionar o contexto ativo e impedir acesso cruzado entre tenants | FND-004, FND-005, FND-006 | Especificada e clarificada |
| 7.1 | FND-007A | `tenant-storage-provisioning` | Provisionar, versionar, recuperar e desativar o armazenamento físico de cada tenant | FND-004, FND-007 | Especificada e clarificada |
| 7.2 | FND-007B | `tenant-data-governance` | Definir integridade global/tenant, auditoria, backup, restauração, retenção e transferência de dados | FND-007, FND-007A | Especificada e clarificada |
| 8 | FND-008 | `plans-entitlements` | Definir a fundação de planos e direitos por conta, adiando ofertas e valores até a definição das features | FND-004, FND-007 | Especificada e clarificada |
| 9 | FND-009 | `system-administration` | Iniciativa composta para administração segura da plataforma | FND-006, FND-007 | Decomposta |
| 9.1 | FND-009A | `system-directory-administration` | Consultar e intervir em usuários e contas globais, inclusive recuperar a administração mínima de conta órfã | FND-001, FND-002, FND-004, FND-005, FND-006, FND-007 | Especificada e clarificada |
| 9.2 | FND-009B | `platform-operations` | Acompanhar provisionamentos automáticos, migrações no deploy, incompatibilidades, saúde e ocorrências estruturais | FND-007A, FND-007B | Especificada e clarificada |
| 9.3 | FND-009C | `platform-configuration` | Administrar configurações, políticas e valores operacionais globais com versionamento e auditoria | FND-006, FND-009A | Especificada e clarificada |
| 9.4 | FND-009D | `tenant-support-access` | Conceder acesso excepcional, temporário e auditado de suporte ao conteúdo de um tenant | FND-005, FND-006, FND-007, FND-009A | Especificada e clarificada |

## Cadastros Transversais do Tenant

Antes dos módulos de negócio, o sistema deve estabelecer um diretório de pessoas reutilizável por financeiro, CRM, fiscal, compras, vendas, RH e demais contextos.

| Ordem | ID | Feature | Objetivo | Dependências | Status |
|------:|----|---------|----------|--------------|--------|
| 10 | FND-010 | `party-registration` | Cadastrar e manter pessoas físicas e jurídicas como registros neutros do tenant, com identificadores, endereços e meios de contato | FND-006, FND-007, FND-007B | Especificada e clarificada |
| 11 | FND-011 | `party-relationships-roles` | Associar pessoas a papéis e relacionamentos extensíveis, como cliente, fornecedor, colaborador, contato e representante | FND-010 | Especificada e clarificada |
| 12 | FND-012 | `party-payment-details` | Manter contas bancárias, chaves Pix e demais dados de pagamento das pessoas com finalidade, proteção e auditoria próprias | FND-006, FND-007B, FND-010 | Especificada e clarificada |

## Módulo Financeiro

O módulo financeiro deve ser decomposto em capacidades menores e reutilizar o diretório de pessoas para representar pagadores, beneficiários, portadores e demais contrapartes.

| Ordem | ID | Feature | Objetivo | Dependências | Status |
|------:|----|---------|----------|--------------|--------|
| 13 | FIN-001 | `financial-accounts` | Cadastrar contas monetárias por natureza, custodiante, produto e capacidade, com moedas, saldos iniciais e ciclo de vida | FND-006, FND-007, FND-008 | Especificada e clarificada |
| 14 | FIN-002 | `financial-categories` | Classificar lançamentos por finalidade financeira e preparar associações contábeis futuras sem incorporá-las à categoria | FND-006, FND-007 | Especificada e clarificada |
| 14.1 | FIN-002A | `financial-dimensions` | Definir dimensões analíticas configuráveis, valores, hierarquias, aplicabilidade e rateios, incluindo centros de custo, projetos e eixos próprios do tenant | FND-006, FND-007, FIN-002 | Especificada e clarificada |
| 14.2 | FIN-002B | `financial-closing-control` | Controlar uma única data de fechamento por tenant, bloquear novos fatos retroativos e auditar reaberturas | FND-006, FND-007, FIN-001 | Especificada e clarificada |
| 15 | FIN-003 | `financial-transactions` | Registrar créditos e débitos categorizados, auditáveis e imutáveis depois do fechamento que expliquem o saldo interno das contas | FIN-001, FIN-002, FIN-002A, FIN-002B, FND-010 | Especificada e clarificada |
| 16 | FIN-004 | `financial-transfers` | Movimentar valores entre contas por operações vinculadas, inclusive entre moedas quando houver conversão explícita | FIN-001, FIN-003 | Especificada e clarificada |
| 17 | FIN-005 | `cash-sessions` (nome provisório) | Reavaliar uma solução unificada para responsabilidade, jornada, contagem, suprimento, sangria e fechamento de numerário | A definir após consolidação das funcionalidades correlatas | Adiada — MVP futuro |
| 18 | FIN-006 | `credit-cards` | Controlar contas especializadas de cartão, bandeiras, limites, compras, parcelas, faturas, pagamentos e cartões opcionais | FIN-001, FIN-002, FIN-003, FIN-004, FND-010 | Especificada e clarificada |
| 19 | FIN-007 | `checks` | Redescobrir o controle de talonários e folhas de cheque, incluindo emissão, beneficiário, compensação, devolução, cancelamento e sustação | A definir na redescoberta do escopo | Adiada — MVP futuro |
| 20 | FIN-008 | `accounts-payable` | Controlar obrigações, parcelas, vencimentos e liquidações a fornecedores e demais contrapartes | FIN-002, FIN-003, FND-010, FND-011, FND-012 | Especificada e clarificada |
| 21 | FIN-009 | `accounts-receivable` | Controlar direitos, parcelas, vencimentos, cobranças e liquidações de clientes e demais contrapartes | FIN-002, FIN-003, FND-010, FND-011, FND-012 | Planejada |
| 21.1 | FIN-009A | `financial-recurrences` | Definir séries recorrentes versionadas de obrigações, direitos, pagamentos e demais documentos financeiros, com frequências convencionais e personalizadas, exceções e alterações de determinada ocorrência em diante | FIN-003, FIN-008, FIN-009 | Planejada |
| 22 | FIN-010 | `bank-statements-reconciliation` | Importar linhas externas separadamente e conciliá-las com lançamentos internos por correspondências auditáveis | FIN-001, FIN-003, FIN-004 | Planejada |
| 23 | FIN-011 | `financial-connectivity` | Administrar consentimentos, conexões e sincronizações com bancos, instituições de pagamento e outros custodiantes | FIN-001, FIN-010 | Planejada |
| 24 | FIN-012 | `investment-positions` | Controlar ativos, quantidades, custos, cotações, eventos patrimoniais, rendimentos e valores de mercado sem misturá-los ao saldo monetário | FIN-001, FIN-003, FND-010 | Planejada |

> [!NOTE]
> O item FIN-005 não bloqueia a sequência do roadmap. Seu nome e escopo são provisórios e deverão ser redescobertos junto das demais funcionalidades que possam compartilhar controle de responsabilidade, jornada, conferência e custódia de numerário.

## Decisões Encaminhadas para Features Futuras

| ID | Feature de destino | Decisão a incorporar |
|----|--------------------|----------------------|
| DR-UD-001 | `user-dashboard` | Uma solicitação de troca do e-mail de um usuário ativo deve expirar após 24 horas sem confirmação. Ao expirar, a alteração deve ser descartada, o e-mail atual deve permanecer inalterado e o usuário deve ser informado no endereço atualmente cadastrado. Uma nova tentativa exige reiniciar o processo de alteração. |
| DR-RFW-001 | RFW Platform | Disponibilizar uma integração reutilizável com Cloudflare Turnstile, incluindo configuração, apresentação do desafio, validação obrigatória no servidor, tratamento de indisponibilidade e suporte a testes. |
| DR-AUTH-001 | `user-authentication` | Aplicar Cloudflare Turnstile ao login para mitigar automação e descoberta de usuários existentes. A respectiva spec deve definir mensagens neutras e a política configurável que torna o desafio obrigatório. |
| DR-AUTH-002 | `user-authentication` | A senha deve possuir de 10 a 128 caracteres e incluir ao menos uma letra maiúscula, uma minúscula, um número e um caractere especial. Deve aceitar gerenciadores e colagem, rejeitar senhas comuns ou comprometidas e não expirar periodicamente sem indício de comprometimento. |
| DR-AUTH-003 | `user-authentication` | Suportar passkeys e chaves de segurança por WebAuthn, permitindo múltiplas credenciais por usuário, verificação local obrigatória, login sem informar e-mail, consulta, nomeação, revogação e recuperação segura. Chaves privadas nunca devem ser recebidas ou armazenadas pelo Rinos. Uma passkey somente satisfaz a exigência de 2FA quando a verificação local do usuário for obrigatória e comprovada. |
| DR-AUTH-004 | `user-authentication` | Um login Google cujo e-mail corresponda a usuário ativo não deve criar vínculo automático. O usuário deve primeiro autenticar a identidade Rinos existente e confirmar explicitamente o vínculo; logins posteriores devem localizar o vínculo pelo emissor e `sub` do Google, não pelo e-mail. |
| DR-AUTH-005 | `user-authentication` | Oferecer autenticação de dois fatores por código enviado ao e-mail e por TOTP compatível com aplicativos autenticadores. O uso deve ser opcional para usuários comuns e obrigatório para administradores do sistema e administradores de contas antes do exercício de acessos administrativos, sem que o papel conceda permissões por si próprio. Login por senha deve exigir um segundo fator nesses contextos; login por passkey somente dispensa o fator adicional quando houver verificação local obrigatória e comprovada. Após login Google, código enviado ao mesmo e-mail não satisfaz o 2FA administrativo: deve ser exigido TOTP ou passkey com verificação local. A spec deve definir recuperação, códigos de contingência e operações que exigem nova confirmação. |
| DR-REG-001 | `user-registration` | Um novo usuário autenticado pelo Google, com e-mail verificado, deve concluir o cadastro com o e-mail bloqueado e apenas os aceites legais, sem senha local ou confirmação adicional do e-mail. O vínculo deve usar emissor e `sub`; eventual cadastro pendente do mesmo e-mail deve ser reutilizado somente após invalidar suas credenciais e comprovações anteriores. |
| DR-UD-002 | `user-dashboard` | Usuários criados por identidade externa devem poder adicionar posteriormente senha local e passkeys mediante reautenticação segura. |
| DR-ACC-001 | `plans-entitlements` | Manter exatamente um plano padrão vigente para novas contas e fallback. Inicialmente será o plano gratuito `FREE`, mas gratuidade e condição de padrão são independentes; outros planos gratuitos podem existir ou tornar-se legados. Franquias, funcionalidades e evolução pertencem à spec de planos; `account-registration` mantém apenas a atribuição atômica necessária à ativação. |
| DR-FAC-001 | `financial-transactions` | Correção posterior ao bloqueio do saldo de abertura deve ocorrer por lançamento financeiro comum categorizado, sem tipo privilegiado capaz de alterar diretamente o saldo. Lançamentos em moeda distinta da moeda funcional devem preservar valores original e convertido, moedas, taxa, fonte, data e arredondamento. |
| DR-FAC-002 | `bank-statements-reconciliation` | Linha de extrato externo e lançamento financeiro interno devem possuir identidades próprias. A conciliação deve preservar ambos e admitir correspondências auditáveis de um para um, um para muitos e muitos para um. |
| DR-FAC-003 | Módulo contábil futuro | Associação de conta ou categoria financeira com diário e contas contábeis deve ser explícita, versionada e capaz de considerar contrapartidas, débito, crédito, rateios e demais dimensões; não presumir relação fixa de uma categoria para uma única conta contábil. |
| DR-FAC-004 | `investment-positions` | Conta monetária em corretora representa somente dinheiro de liquidação ou custódia. Posições cujo valor dependa de quantidade, cotação, índice, valorização ou evento patrimonial devem usar modelo próprio e não alterar o saldo monetário sem evento efetivado. |
| DR-FAC-005 | `financial-accounts` e `credit-cards` | Carteira ou plataforma com saldo próprio deve ser conta financeira; aplicativo, cartão de débito ou canal que apenas acessa uma conta não exige cadastro próprio. Chaves Pix e identificadores pertencem à conta. Cartão de crédito é conta especializada de crédito/passivo, e seus cartões físicos, adicionais ou virtuais são detalhamentos opcionais sem saldo próprio. |
| DR-FAC-006 | Controle operacional de numerário futuro | Preservar a ideia de jornadas temporárias de responsabilidade sobre numerário, com abertura, contagens, suprimentos, sangrias, conferência e fechamento. A capacidade não deve ser especificada nem implementada isoladamente neste MVP atual: deverá ser redescoberta e unificada com funcionalidades correlatas em um MVP futuro. Se a solução calcular diferenças, sua confirmação deverá gerar lançamentos financeiros comuns categorizados e auditados, sem reescrever saldo ou abertura. |
| DR-FCAT-001 | `financial-dimensions` | Permitir dimensões configuráveis pelo tenant e dimensões vinculadas a entidades fornecidas por módulos. Cada dimensão deve possuir política padrão de aplicabilidade, e cada categoria poderá sobrescrevê-la como obrigatória, opcional ou proibida; as regras serão avaliadas por parcela de categoria e versionadas sem alterar o histórico. |
| DR-MEM-001 | `system-administration` | Definir procedimento excepcional e fortemente auditado para recuperar a administração de uma conta sem participante apto. Operações comuns de associação devem impedir que a conta fique sem participante ativo com chaves administrativas mínimas e 2FA compatível. |
| DR-TCI-001 | `tenant-storage-provisioning` | Adotar cadastro global e armazenamento separado por conta, mantendo a localização física interna, imutável, gerada pelo sistema e resolvida sem participação da interface. A solução deve permitir evolução futura da distribuição física sem alterar a identidade do tenant. |
| DR-TCI-002 | `tenant-data-governance` | Permitir FKs físicas de tenant para global quando houver benefício de integridade. Toda FK deve usar `ON DELETE SET NULL` quando o filho puder sobreviver sem o vínculo ou `ON DELETE CASCADE` quando depender integralmente do pai. FKs de global para tenant e `ON DELETE RESTRICT`/`NO ACTION` em referências tenant para global são proibidos. |
