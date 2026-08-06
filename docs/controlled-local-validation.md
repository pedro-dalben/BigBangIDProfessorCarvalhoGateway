# Validação local controlada

Use apenas credenciais e contas de teste.

1. Suba PostgreSQL e Redis do bot e aplique `pnpm db:migrate`.
2. Ative o ingress do bot com segredo e pepper de teste.
3. Execute `./gradlew clean check build` e copie o JAR para um servidor Fabric local.
4. Configure o segredo por `PROFESSOR_GATEWAY_SHARED_SECRET`.
5. Confirme heartbeat, `/vincular`, `/professor vincular`, snapshot e `/perfil`.
6. Pare o bot, gere um evento, confirme o arquivo em `spool/`, reinicie e confirme o ACK.
7. Execute `/desvincular` e confirme a remoção do cache após `IDENTITY_NOT_LINKED`.

Neste workspace foi validado o boot Fabric sem os mods opcionais, o modo
`DEGRADED` sem segredo e o fluxo bot com PostgreSQL/Redis reais. O fluxo com
uma conta Discord e jogador Cobblemon reais ainda requer um ambiente de teste
com esses serviços e não autoriza deploy de produção.
