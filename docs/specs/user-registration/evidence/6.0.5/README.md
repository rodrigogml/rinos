# Publicação inicial dos documentos jurídicos

## Localização e integridade

As versões iniciais estão no diretório de publicação previsto:

- `docs/legal/approved/terms-of-use/1.0.0.md`
- `docs/legal/approved/privacy-policy/1.0.0.md`

Os arquivos `.sha256` versionados correspondem aos bytes UTF-8 dos documentos. O banco global
recebeu o mesmo conteúdo, com `required = TRUE`, hash SHA-256 e vigência aberta.

## Migration e ambiente candidato

A migration `20260812_001_update.sql` publica o conteúdo de forma idempotente e atualiza a view
de versão. A correção `20260812_002_update.sql` ajusta a representação hexadecimal do hash da
Política de Privacidade e finaliza a versão `20260812002`.

No Turing, em 12/08/2026:

- `identity_legalDocumentVersion` contém exatamente duas versões vigentes, `TERMS_OF_USE 1.0.0`
  e `PRIVACY_POLICY 1.0.0`;
- ambos são obrigatórios;
- os hashes calculados pelo MySQL coincidem com os arquivos publicados;
- o serviço `rinos` está `active`;
- `/login` retorna HTTP 200 e não exibe a mensagem de documentos indisponíveis.

## Autorização e limite da evidência

O responsável pelo projeto autorizou o uso inicial dessas versões em 12/08/2026. Esta evidência
confirma localização, publicação, integridade técnica e ativação do fluxo; não constitui revisão
jurídica independente nem valida o mérito do conteúdo.
