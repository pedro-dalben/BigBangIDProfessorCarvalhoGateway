package com.pedrodalben.bigbangid.professorcarvalho;

import com.google.gson.JsonParser;
import com.pedrodalben.bigbangid.professorcarvalho.security.HmacSigner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayProtocolTest {
    @Test
    void reproducesGoldenHmacVector() throws Exception {
        var fixture = JsonParser.parseString(Files.readString(Path.of("contracts/gateway-v1.json"))).getAsJsonObject();
        byte[] body = fixture.get("body").getAsString().getBytes(StandardCharsets.UTF_8);
        String hash = HmacSigner.bodyHash(body);
        String canonical = HmacSigner.canonical(fixture.get("method").getAsString(), fixture.get("path").getAsString(), fixture.get("serverId").getAsString(), fixture.get("timestamp").getAsString(), fixture.get("requestId").getAsString(), fixture.get("gatewayVersion").getAsString(), hash);
        assertEquals(fixture.get("bodySha256").getAsString(), hash);
        assertEquals(fixture.get("signature").getAsString(), HmacSigner.sign(fixture.get("testOnlySecret").getAsString(), canonical));
    }
}
