package com.pedrodalben.bigbangid.professorcarvalho.gateway;

import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfig;
import com.pedrodalben.bigbangid.professorcarvalho.security.HmacSigner;
import com.pedrodalben.bigbangid.professorcarvalho.util.UuidV7Generator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class GatewayClient {
    private final GatewayConfig config;
    private final String secret;
    private final HttpClient client;
    private final Executor executor;

    public GatewayClient(GatewayConfig config, String secret, Executor executor) {
        this.config = config;
        this.secret = secret;
        this.executor = executor;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMillis)).executor(executor).build();
    }

    public CompletableFuture<GatewayResponse> post(String path, byte[] body) {
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UuidV7Generator.next().toString();
        String hash = HmacSigner.bodyHash(body);
        String canonical = HmacSigner.canonical("POST", path, config.serverId, timestamp, requestId, "1", hash);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.apiBaseUrl + path))
            .timeout(Duration.ofMillis(config.requestTimeoutMillis))
            .header("Content-Type", "application/json")
            .header("X-Professor-Server", config.serverId)
            .header("X-Professor-Timestamp", timestamp)
            .header("X-Professor-Request-Id", requestId)
            .header("X-Professor-Gateway-Version", "1")
            .header("X-Professor-Signature", HmacSigner.sign(secret, canonical))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> new GatewayResponse(response.statusCode(), response.body()));
    }

    public CompletableFuture<GatewayResponse> sendEvent(GatewayEvent event) { return post("/v1/gateway/events", event.bytes()); }

    public CompletableFuture<GatewayResponse> link(String code, UUID minecraftUuid, String minecraftName) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("code", code);
        json.addProperty("minecraftUuid", minecraftUuid.toString());
        json.addProperty("minecraftName", minecraftName);
        json.addProperty("serverId", config.serverId);
        json.addProperty("requestedAt", java.time.Instant.now().toString());
        return post("/v1/gateway/identity/link", json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
