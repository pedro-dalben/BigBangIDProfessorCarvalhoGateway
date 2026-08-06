# BigBang ID & Professor Carvalho Gateway

Mod Fabric exclusivamente de servidor que vincula a conta Minecraft ao Professor
Carvalho e envia dados do BigMonCraft somente por HTTP de saída.

O mod não contém token do Discord, credenciais de PostgreSQL ou Redis e não abre
nenhuma porta HTTP no servidor Minecraft. O protocolo atual é a versão `1`.

## Estado

Contrato de arquitetura aprovado para implementação local. Ainda não há JAR nem
integração executável nesta etapa.

Consulte [a arquitetura](docs/architecture.md), [o protocolo](docs/protocol.md)
e [a vinculação](docs/identity-linking.md).
