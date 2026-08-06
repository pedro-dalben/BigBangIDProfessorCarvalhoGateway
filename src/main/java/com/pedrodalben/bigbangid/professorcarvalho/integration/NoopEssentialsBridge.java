package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NoopEssentialsBridge implements EssentialsProfileBridge {
    @Override public CompletableFuture<JsonObject> collect(UUID playerUuid) { JsonObject result = new JsonObject(); result.add("progression", new JsonObject()); result.add("economy", new JsonObject()); JsonObject modules = new JsonObject(); modules.addProperty("bigBangEssentials", "unavailable"); result.add("modules", modules); return CompletableFuture.completedFuture(result); }
    @Override public boolean available() { return false; }
}
