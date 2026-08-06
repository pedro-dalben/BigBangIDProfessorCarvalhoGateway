# Testes

Use `./gradlew clean check build`. O teste Java do vetor dourado lê `contracts/gateway-v1.json` e confirma o mesmo SHA-256 e HMAC usado pelo bot TypeScript. A validação de jogador real ainda exige um servidor Fabric de teste com PostgreSQL, Redis, bot e guild de teste.
