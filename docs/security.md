# Segurança

Toda requisição usa HMAC-SHA256 com timestamp, request ID novo por tentativa, event ID estável e comparação constante. O segredo tem no mínimo 32 bytes, nunca é impresso e não é aceito no arquivo JSON padrão. O gateway não abre porta HTTP no Minecraft e não contém token Discord, PostgreSQL ou Redis.

O spool é limitado ao diretório de configuração, usa escrita temporária e renomeação atômica. Arquivos inválidos são isolados em `quarantine`; falhas HTTP permanentes seguem para `dead-letter`.
