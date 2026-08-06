# Instalação local

1. Execute `./gradlew clean check build` com Java 21.
2. Copie o JAR `build/libs/bigbangid-professorcarvalho-gateway-fabric-0.1.0+1.21.1.jar` para um servidor Fabric de teste 1.21.1.
3. Configure `PROFESSOR_GATEWAY_SHARED_SECRET` com um valor gerado por `openssl rand -hex 32`.
4. Coloque `config.json` na pasta indicada e confirme `/pcgateway status`.

Não copie o JAR para produção nesta fase.
