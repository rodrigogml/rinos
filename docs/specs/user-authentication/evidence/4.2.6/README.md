# Evidência da tarefa 4.2.6

## Resultado

Os limites próprios do Rinos e os contratos reutilizáveis da RFW possuem testes reproduzíveis para cadastro e login
descobrível, configuração inválida de RP/origin, replay, cancelamento da cerimônia e revogação individual.

## Matriz de cenários

| Cenário | Evidência |
|---|---|
| Cadastro | `SpringWebAuthnCredentialRepositoryAdapterTest` preserva todos os campos validados e exige autenticação recente; `SpringWebAuthnRepositoryAdapterIT` comprova o roundtrip no MySQL |
| Login descobrível | `RFWWebAuthnRelyingPartyOperationsConfigTest` inicia sem identidade, mantém o RP ID, exige verificação local, omite `allowCredentials` e cria desafio novo a cada pedido |
| RP/origin inválidos | `AuthenticationProtocolPropertiesValidatorConfigTest` rejeita RP em formato de URL, origin fora do domínio do RP e HTTP remoto antes da aplicação iniciar |
| Replay | `RFWPasskeyAuthenticationStoreTest` consome a conclusão uma única vez e a vincula à sessão; o adapter do Rinos rejeita contador positivo repetido/regressivo |
| Cancelamento | `RFWPasskeyJavascriptContractTest` comprova que cancelamento ou falha não conclui nem redireciona; `RFWPasskeyComponentTest` publica somente o estado tipado `CANCELLED` |
| Revogação | `AuthenticationFactorServiceTest` protege o último método e revoga somente a passkey selecionada quando há alternativa; o repository não resolve credential revogada |

> [!IMPORTANT]
> Assinatura, challenge, origin e hash do RP apresentados por uma resposta WebAuthn são verificados pelo
> `Webauthn4JRelyingPartyOperations` do Spring Security/WebAuthn4J. Os testes locais verificam a configuração entregue
> a essa autoridade e os comportamentos do Rinos antes e depois dela, sem criar um segundo verificador criptográfico.

## Evolução reutilizável da RFW

O commit `9cf48e6` acrescenta a prova explícita do pedido anônimo descobrível e documenta esse contrato em todos os
idiomas do showroom. O RFW foi validado isoladamente com `mvn -q verify` antes da atualização do submódulo.

## Validação reproduzível

```powershell
cd modules/RFW.Platform
mvn -q -DskipITs -DskipTests=false "-Dtest=RFWWebAuthnRelyingPartyOperationsConfigTest,RFWPasskeyAuthenticationStoreTest,RFWPasskeyJavascriptContractTest,RFWPasskeyComponentTest" test
mvn -q verify

cd ../..
mvn -q -DskipITs -DskipTests=false "-Dtest=AuthenticationProtocolPropertiesValidatorConfigTest,AuthenticationFactorServiceTest,SpringWebAuthnCredentialRepositoryAdapterTest,SpringWebAuthnUserRepositoryAdapterTest,RFWPasskeyAuthenticationProviderAdapterTest,PasskeyAuthenticationFacadeImplTest,RFWPasskeyManagementProviderAdapterTest,PasskeyManagementFacadeImplTest" test
mvn -q -DskipITs=false "-Dit.test=SpringWebAuthnRepositoryAdapterIT" verify
mvn -q verify
```
