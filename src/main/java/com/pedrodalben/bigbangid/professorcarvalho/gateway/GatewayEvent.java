package com.pedrodalben.bigbangid.professorcarvalho.gateway;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangid.professorcarvalho.util.UuidV7Generator;

import java.time.Instant;
import java.util.UUID;

public record GatewayEvent(UUID eventId, String eventType, String schemaVersion, String serverId, Instant occurredAt, JsonElement payload) {
    public JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.addProperty("eventId", eventId.toString());
        json.addProperty("eventType", eventType);
        json.addProperty("schemaVersion", schemaVersion);
        json.addProperty("serverId", serverId);
        json.addProperty("occurredAt", occurredAt.toString());
        json.add("payload", payload.deepCopy());
        return json;
    }

    public byte[] bytes() { return asJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); }

    public static GatewayEvent create(String eventType, String serverId, JsonElement payload) {
        return new GatewayEvent(UuidV7Generator.next(), eventType, "1", serverId, Instant.now(), payload);
    }

    public static GatewayEvent parse(String value) {
        JsonObject json = JsonParser.parseString(value).getAsJsonObject();
        return new GatewayEvent(UUID.fromString(json.get("eventId").getAsString()), json.get("eventType").getAsString(), json.get("schemaVersion").getAsString(), json.get("serverId").getAsString(), Instant.parse(json.get("occurredAt").getAsString()), json.get("payload"));
    }
}
