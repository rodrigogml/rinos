# Evidência 7.5.5 — smoke test do JAR atrás do proxy

Data da validação: 2026-08-11.

## Procedimento

Foram executadas somente sondas de leitura no servidor de release `Turing`:

```text
systemctl is-active rinos       -> active
systemctl is-enabled rinos     -> enabled
systemctl show rinos           -> User=rinos; ExecStart=/opt/java/jdk-25.0.2/bin/java -jar /opt/rinos/rinos-1.0.0.jar
systemctl is-active apache2     -> active
curl -k -H 'Host: app.rinos.com.br' https://127.0.0.1/login
                                -> 200 text/html;charset=utf-8
curl https://app.rinos.com.br/login
                                -> 200 text/html;charset=utf-8; Server=cloudflare
```

O resultado comprova o caminho JAR → Apache → Cloudflare e o carregamento da rota pública de login. Nenhum serviço foi
reiniciado e nenhuma configuração foi alterada durante a verificação.
