# Vinculação BigBang ID

`/vincular` no Discord gera `CARVALHO-XXXXXXXX` com ao menos 40 bits de entropia
e expiração de dez minutos. A API armazena somente
`HMAC-SHA256(IDENTITY_LINK_CODE_PEPPER, código normalizado)`, nunca o código.

`/professor vincular <código>` normaliza comprimento, espaços e maiúsculas, mas
nunca aceita UUID ou nome fornecidos pelo texto. Ele obtém ambos do
`ServerPlayer`, envia o pedido assinado sem spool e retorna de modo assíncrono à
thread do servidor. O código não é registrado em log.

A transação da API consome o código, limita tentativas a cinco por padrão e faz
cumprir uma conta Discord ativa por UUID Minecraft e um UUID ativo por Discord.
O histórico é preservado; desfazer vínculo apenas inativa o vínculo e exige
confirmação efêmera no Discord. Uma resposta `IDENTITY_NOT_LINKED` a um perfil
remove o UUID do cache local.
