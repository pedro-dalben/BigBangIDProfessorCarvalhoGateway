# Eventos futuros

O MVP publica somente eventos implementados e testados: início e fim de
sessão, snapshot de perfil, resultado de vinculação, heartbeat e ciclo de
vida do gateway.

Os tipos abaixo ficam reservados para fases posteriores:

- `pokemon.capture.completed`
- `pokemon.evolution.completed`
- `pokemon.trade.completed`
- `player.rank.changed`
- `economy.balance.changed`
- `gems.balance.changed`
- `reward.grant.requested`

Cada novo evento deve receber um schema versionado, testes de idempotência,
política de privacidade, prioridade de spool e tratamento de retry antes de
ser habilitado. O gateway não executa comandos remotos arbitrários e não
emite eventos de spawn; o Cobblemon Spawn Alerts permanece independente.
