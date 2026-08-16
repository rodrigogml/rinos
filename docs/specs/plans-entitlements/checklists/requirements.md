# Checklist de requisitos

- [x] Há contrato obrigatório para toda identidade ativa e todo tenant operacional.
- [x] Catálogo, padrão, fallback e avaliação distinguem `PERSONAL` e `TENANT`.
- [x] `PERSONAL/FREE` possui composição vazia sem direitos inferidos.
- [x] `TENANT/FREE` contém limite explícito de dez usuários associados.
- [x] Estados da associação não liberam ocupação.
- [x] Convite reserva capacidade e aceite não conta duas vezes.
- [x] Contrato é separado da atribuição e preparado como fronteira de billing.
- [x] Publicação não migra contratos silenciosamente.
- [x] Backfill e bootstrap são idempotentes.
- [x] Billing e demais franquias permanecem fora do slice.
