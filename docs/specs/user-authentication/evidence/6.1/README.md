# Evidência 6.1 — validação integrada RFW/MySQL

- O RFW foi validado isoladamente com `mvn -q verify` no commit publicado `db3f246`.
- O Rinos teve validação de compilação, testes focados de integração e roundtrip MySQL já executados durante as tarefas; o `mvn verify` completo excedeu a janela de 120 segundos no ambiente local e permanece como execução operacional pendente.
- Os adapters RFW são tipados contra os DTOs/VOs atuais e os testes de integração verificam a descoberta dos providers reais.
- Os scripts globais/tenant e a view de versão seguem o contrato do RFW; nenhum código de UI acessa SQL diretamente.
