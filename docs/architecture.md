# Arquitetura

## Limites

O `bigbangid_professorcarvalhogateway` é um único mod Fabric de ambiente
`server`. Ele é o cliente de saída entre Minecraft/Cobblemon/BigBangEssentials e
a API do Professor Carvalho. Não há servidor HTTP, RCON, bot token, Redis,
PostgreSQL ou acesso direto ao banco do BigBangEssentials no Minecraft.

```text
Minecraft + Cobblemon + BigBangEssentials
              |
              | HTTP autenticado por HMAC, somente saída
              v
Professor Carvalho API, acessível pela WireGuard
              |
        PostgreSQL, Redis, BullMQ e Discord
```

## Responsabilidades

O gateway valida sua configuração e segredo, mantém um cache local mínimo de
jogadores vinculados, assina requisições, grava eventos assíncronos antes do
envio e coleta perfis parciais. A API é a autoridade de identidade; o cache
local nunca contém Discord ID, código de vinculação, saldo ou dados de Pokémon.

O BigBangEssentials continua sendo a autoridade de moedas, gemas, rank, jobs e
tempo de jogo. O gateway usará somente uma fachada Java pública e assíncrona.
Cobblemon é opcional e seus valores mutáveis são copiados na thread do servidor
antes da serialização em segundo plano.

## Concorrência e desligamento

Eventos de jogo copiam dados primitivos e entram em filas limitadas. Disco,
HTTP e consultas de perfil usam executores nomeados e limitados; a thread do
servidor só recebe a mensagem final ao jogador. No desligamento, o gateway para
de aceitar eventos, cancela tarefas periódicas, persiste o que já recebeu e
encerra dentro do limite configurado. Arquivos já persistidos permanecem para a
próxima inicialização.

## Estados operacionais

`DISABLED` cobre configuração desativada. `DEGRADED` cobre segredo ausente ou
inválido, URL inválida ou indisponibilidade remota: o Minecraft continua
funcionando e o status administrativo informa o motivo sem revelar segredos.
`READY` permite envio. Um endereço público é recusado quando o modo de rede
privada estiver exigido.

## CSA

CSA continua independente. Esta fase não publica `spawn.detected`, portanto não
duplica alertas de spawn.
