# Wireframe — Explicação do acesso efetivo

```text
+--------------------------------------------------------------------------------+
| Explicação de acesso — Ana — Conta Aurora                                     |
| Resultado: NEGADO                                                             |
| Condição decisiva: bloqueio vigente em “Confirmar lançamento”                |
+--------------------------------------------------------------------------------+
| Chave                         Resultado       Permissões        Bloqueios     |
| Confirmar lançamento          Bloqueado       Grupo Financeiro  Regra direta  |
| Consultar lançamentos         Permitido       Grupo Financeiro  —             |
+--------------------------------------------------------------------------------+
| [v] Condições estruturais  [v] Plano  [v] Garantia de autenticação            |
| [Voltar]                                                                     |
+--------------------------------------------------------------------------------+
```

O painel mostra apenas origens do mesmo contexto que o administrador pode conhecer. Falta de plano aparece como gate independente, não como bloqueio.
