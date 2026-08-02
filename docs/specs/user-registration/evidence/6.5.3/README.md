# Evidências da tarefa 6.5.3

Data da validação: 2026-08-02

## Ciclo efêmero da prova

Ao selecionar **Confirmar cancelamento**, o renderer:

1. copia a prova para uma variável local;
2. limpa o campo imediatamente;
3. cria o DTO efêmero da RFW;
4. marca o componente como ocupado;
5. chama o `RFWRegistrationCancellationProviderAdapter` real;
6. converte para o DTO público do Rinos com correlation ID aleatório;
7. aguarda a conclusão assíncrona sem restaurar a prova no componente.

O teste mantém a facade pendente e comprova que o campo já está vazio enquanto `aria-busy` continua ativo. Também
captura o DTO na fronteira pública para confirmar que a prova atravessa somente a chamada necessária e que o adapter
não expõe entity, repository ou estado persistente à UI.

## Validação automatizada

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest,RFWRegistrationCancellationProviderAdapterTest' test
```

```text
26 testes; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

O cenário completa posteriormente a future com `CANCELLED` e verifica a saída do estado ocupado para o resultado,
sem recolocar a prova no campo ou na entrada da etapa.

Gate completo:

```powershell
mvn verify
```

```text
Testes unitários: 322; 0 falhas; 0 erros; 0 ignorados
Testes de integração: 56; 0 falhas; 0 erros; 10 E2E opt-in ignorados
BUILD SUCCESS
```

Não houve alteração adicional na RFW: a baseline `5ef0600dd7a7e10db26c321522cb143e6a6132af` já implementava a limpeza
antes da chamada; esta tarefa acrescentou a prova integrada desse contrato no Rinos.
