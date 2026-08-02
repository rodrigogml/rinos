# Evidência da tarefa 7.4.5

## Limite do resultado SMTP

O estado `ACCEPTED` significa somente que o servidor SMTP aceitou a mensagem durante a sessão de
transporte posterior ao commit. Ele não comprova:

- entrega na caixa postal;
- tempo de entrega;
- leitura pelo destinatário;
- bounce posterior;
- classificação como spam;
- aceitação por filtros ou encaminhadores intermediários.

O Rinos mede tentativas, resultado do dispatch e duração até o aceite ou falha SMTP. Eventos
posteriores somente poderão ser medidos quando um provedor futuro fornecer contrato autenticado
para eles; até lá, nenhum desses estados é inferido.

## Linguagem pública

As mensagens de cadastro e reenvio agora informam que a instrução foi aceita ou encaminhada ao
serviço de e-mail e que a entrega pode levar alguns minutos. Elas não afirmam que a mensagem chegou
ao destinatário.

O SLO de 100 cadastros e o smoke real medem exclusivamente aceite SMTP. Nenhum deles declara
throughput do ambiente, entrega final ou qualidade do provedor.

O teste focal `RFWRegistrationProviderAdapterTest` confirmou os 20 mapeamentos de resultado que
selecionam essas mensagens, sem falhas, erros ou testes ignorados.
