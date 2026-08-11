# Evidência 5.1.4 — contrato responsivo e acessível do login

O login utiliza exclusivamente o renderer e os tokens do RFW. A implementação não cria CSS estrutural paralelo nem controles fora dos slots públicos. O RFW mantém foco inicial, foco no primeiro campo inválido, região de feedback assertiva, labels e estados ocupados; os testes de acessibilidade do fluxo real exercitam desktop/telefone, claro/escuro e reflow sem overflow.

O teste E2E do Rinos também verifica a etapa sign_in, labels de e-mail/senha, foco por teclado e ausência de overflow em 1440px e 390px. A localização permanece responsabilidade dos bundles RFW/Rinos, sem textos de estado embutidos no componente hospedeiro.
