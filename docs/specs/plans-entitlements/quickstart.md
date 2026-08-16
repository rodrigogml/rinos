# Quickstart — Cenários mínimos

1. Ative uma identidade: confirme contrato único `PERSONAL/FREE` com composição vazia.
2. Crie um tenant: confirme contrato único `TENANT/FREE`, atribuição publicada e ocupação do fundador `1/10`.
3. Envie nove convites a identidades distintas: cada envio reserva uma vaga e o total chega a `10/10`.
4. Tente o décimo convite adicional: deve falhar antes do envio com `PLAN_LIMIT_REACHED`.
5. Revogue um convite não aceito: a reserva é liberada; um novo convite pode ser enviado.
6. Aceite um convite: a reserva vira ocupação sem alterar o total duas vezes.
7. Remova esse participante: a ocupação permanece e nenhuma vaga é liberada.
8. Reassocie a mesma identidade: reutiliza a ocupação histórica; identidade diferente continua bloqueada no limite.
9. Avalie direito tenant em outro tenant ou direito pessoal como tenant: deve retornar contexto inválido.
10. Execute chave global administrativa sem requisito: o contrato pessoal do administrador não participa.

Os cenários concorrentes devem ser executados no MySQL real com duas transações/instâncias e comprovar teto igual a dez.
