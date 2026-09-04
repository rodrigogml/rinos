# Evidência — Convites Persistentes

Em 2026-08-16, `MembershipPersistenceIT` no MySQL 9.7.2 comprovou:

- normalização do destinatário e uma única linha pendente por conta/e-mail;
- emissão concorrente convergindo em um convite, sem duas provas válidas;
- prova aleatória vinculada ao UUID por HMAC versionado, nunca exposta por `toString` ou persistida em claro;
- identidade ativa com e-mail correspondente, conta/tenant operacionais e capacidade de plano como gates distintos;
- prova incorreta sem efeito e aceite concorrente por dois dispositivos criando no máximo uma associação;
- replay do convite consumido negado e eventos/outbox confirmados na mesma transação.
- reenvio cria novo ciclo, invalida a prova anterior e revogação invalida a prova substituta;
- expiração horária materializa estado terminal em lotes e permite um novo convite sem reativar o anterior.
- rate limit por conta, convidante, destinatário e origem, com rollback das quatro reservas quando uma dimensão bloqueia;
- entrega durável recupera a prova de um envelope AEAD versionado, nunca do payload, envia pelo RFW e apaga o envelope;
- consumo, recusa, revogação, substituição e expiração cancelam entregas ainda pendentes sem conservar o segredo.
