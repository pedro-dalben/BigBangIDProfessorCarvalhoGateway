# Configuração

O arquivo fica em `config/bigbangid_professorcarvalhogateway/config.json`. O segredo não fica no JSON: use `PROFESSOR_GATEWAY_SHARED_SECRET` ou `gateway.secret` com permissões restritas. O gateway inicia o Minecraft em estado `DEGRADED` quando a configuração é inválida.

O endereço da API deve apontar para a rede privada WireGuard quando `requirePrivateAddress` estiver ativo. Recarregue com `/pcgateway reload`; a aplicação de alterações de rede exige reinicialização controlada do servidor de testes.
