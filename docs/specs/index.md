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
| 13 | FIN-001 | `financial-accounts` | Cadastrar e manter contas financeiras, instituições, moedas, saldos iniciais e ciclo de vida | FND-006, FND-007, FND-008, FND-010 | Planejada |

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
| DR-MEM-001 | `system-administration` | Definir procedimento excepcional e fortemente auditado para recuperar a administração de uma conta sem participante apto. Operações comuns de associação devem impedir que a conta fique sem participante ativo com chaves administrativas mínimas e 2FA compatível. |
| DR-TCI-001 | `tenant-storage-provisioning` | Adotar cadastro global e armazenamento separado por conta, mantendo a localização física interna, imutável, gerada pelo sistema e resolvida sem participação da interface. A solução deve permitir evolução futura da distribuição física sem alterar a identidade do tenant. |
| DR-TCI-002 | `tenant-data-governance` | Permitir FKs físicas de tenant para global quando houver benefício de integridade. Toda FK deve usar `ON DELETE SET NULL` quando o filho puder sobreviver sem o vínculo ou `ON DELETE CASCADE` quando depender integralmente do pai. FKs de global para tenant e `ON DELETE RESTRICT`/`NO ACTION` em referências tenant para global são proibidos. |
