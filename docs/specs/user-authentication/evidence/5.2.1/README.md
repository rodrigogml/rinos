# Evidência 5.2.1 — fatores e recuperação no renderer RFW

O renderer padrão do RFW já oferece seleção explícita de TOTP, código por e-mail, passkey e recovery code. A composição Rinos registra providers reais de segundo fator, gestão e recuperação; a emissão de e-mail ocorre somente após seleção explícita do método. Testes do componente cobrem seleção, reenvio, cooldown, indisponibilidade e prova não criada quando o método não tem emissor.
