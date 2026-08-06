# BigBang ID & Professor Carvalho Gateway

Mod Fabric exclusivamente de servidor que vincula a conta Minecraft ao Professor
Carvalho e envia dados do BigMonCraft somente por HTTP de saída.

O mod não contém token do Discord, credenciais de PostgreSQL ou Redis e não abre
nenhuma porta HTTP no servidor Minecraft. O protocolo atual é a versão `1`.

## Estado

MVP executável para validação local. O artefato é server-side e usa Java 21,
Fabric 1.21.1 e protocolo de gateway v1. A vinculação, o spool, o heartbeat e
os snapshots usam somente comunicação de saída.

Consulte [a arquitetura](docs/architecture.md), [o protocolo](docs/protocol.md),
[a configuração](docs/configuration.md), [a segurança](docs/security.md) e
[a instalação](docs/installation.md).
