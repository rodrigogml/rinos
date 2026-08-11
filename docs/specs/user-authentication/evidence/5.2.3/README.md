# Evidência 5.2.3 — recuperação de senha

`RFWPasswordRecoveryProviderAdapter` conecta o renderer público à fachada de recuperação. O fluxo preserva somente o identificador necessário para a etapa e usa mensagens neutras; provas, tokens e senha não permanecem no estado do componente. A mesma política de indisponibilidade e consumo único é aplicada pelo serviço de recuperação.
