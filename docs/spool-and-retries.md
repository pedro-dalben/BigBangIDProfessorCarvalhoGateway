# Spool e tentativas

Eventos são gravados antes do envio. A fila usa backoff exponencial de 1 a 300 segundos, jitter configurável e limite de requisições em voo. O heartbeat pendente é coalescido para evitar milhares de arquivos durante uma indisponibilidade.

Respostas 2xx só removem um arquivo quando `accepted=true` e o `eventId` coincide. Respostas de esquema, assinatura ou conflito vão para dead-letter; rede, timeout, 408, 425, 429 e 5xx são tentados novamente.
