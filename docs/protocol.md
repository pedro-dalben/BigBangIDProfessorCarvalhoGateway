# Protocolo do Gateway v1

Base: `/v1/gateway`. Endpoints: `POST /events`, `/identity/link`, `/profiles` e
`/heartbeat`. Todo corpo é JSON UTF-8 e todo pedido possui `Content-Type:
application/json`, `X-Professor-Server`, `X-Professor-Timestamp`,
`X-Professor-Request-Id`, `X-Professor-Gateway-Version: 1` e
`X-Professor-Signature`.

## Assinatura

O HMAC-SHA256 hexadecimal em minúsculas usa exatamente os bytes UTF-8 enviados:

```text
METHOD\nPATH\nSERVER_ID\nTIMESTAMP\nREQUEST_ID\nGATEWAY_VERSION\nSHA256_BODY
```

`requestId` muda a cada tentativa; `eventId` no envelope não muda. A API aceita
repetição idempotente do mesmo evento e corpo, rejeita o mesmo `eventId` com
outro hash (`EVENT_ID_CONFLICT`) e rejeita o mesmo `requestId` dentro da janela
de replay. O timestamp é de cada tentativa e tolera 60 segundos por padrão.

## Envelope persistível

```json
{
  "eventId": "UUIDv7",
  "eventType": "player.profile.snapshot",
  "schemaVersion": "1",
  "serverId": "bigmoncraft",
  "occurredAt": "2026-08-06T19:30:00.000Z",
  "payload": {}
}
```

O gateway remove um evento do spool somente após resposta HTTP 200 ou 202 com
JSON válido, `accepted: true` e `eventId` correspondente. Falhas temporárias
(rede, timeout, 408, 425, 429 e 5xx) sofrem retry; assinatura, schema,
protocolo, fonte desativada e conflito de evento vão para dead-letter.

## Vetor dourado

O arquivo [gateway-v1.json](../contracts/gateway-v1.json) usa uma chave apenas
de teste. Java e TypeScript devem reproduzir `bodySha256` e `signature` sem
normalizar ou reserializar o corpo.
