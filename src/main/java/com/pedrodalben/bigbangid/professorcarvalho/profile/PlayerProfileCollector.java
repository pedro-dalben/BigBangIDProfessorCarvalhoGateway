package com.pedrodalben.bigbangid.professorcarvalho.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.integration.EssentialsProfileBridge;
import com.pedrodalben.bigbangid.professorcarvalho.integration.CobblemonProfileBridge;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PlayerProfileCollector {
    private final EssentialsProfileBridge essentials;
    private final CobblemonProfileBridge cobblemon;
    private final Executor executor;
    private final String modVersion;

    public PlayerProfileCollector(EssentialsProfileBridge essentials, CobblemonProfileBridge cobblemon, Executor executor, String modVersion) {
        this.essentials = essentials; this.cobblemon = cobblemon; this.executor = executor; this.modVersion = modVersion;
    }

    public CompletableFuture<JsonObject> collect(ServerPlayer player, boolean includeEssentials, boolean includeCobblemon) {
        JsonObject identity = new JsonObject();
        identity.addProperty("minecraftUuid", player.getUUID().toString());
        identity.addProperty("minecraftName", player.getGameProfile().getName());
        identity.addProperty("online", true);
        CompletableFuture<JsonObject> essentialsFuture = includeEssentials ? essentials.collect(player.getUUID()).exceptionally(ignored -> unavailableEssentials()) : CompletableFuture.completedFuture(unavailableEssentials());
        JsonObject cobblemonSnapshot = includeCobblemon ? cobblemon.collect(player) : unavailableCobblemon();
        return essentialsFuture.thenApplyAsync(essentialsSnapshot -> {
            JsonObject payload = new JsonObject();
            payload.add("player", identity);
            payload.add("progression", essentialsSnapshot.getAsJsonObject("progression"));
            payload.add("economy", essentialsSnapshot.getAsJsonObject("economy"));
            payload.add("cobblemon", cobblemonSnapshot);
            payload.add("modules", essentialsSnapshot.getAsJsonObject("modules"));
            JsonObject gateway = new JsonObject(); gateway.addProperty("modVersion", modVersion); gateway.addProperty("protocolVersion", "1"); payload.add("gateway", gateway);
            return payload;
        }, executor);
    }

    private static JsonObject unavailableEssentials() {
        JsonObject result = new JsonObject();
        result.add("progression", new JsonObject()); result.add("economy", new JsonObject());
        JsonObject modules = new JsonObject(); modules.addProperty("bigBangEssentials", "unavailable"); result.add("modules", modules); return result;
    }
    private static JsonObject unavailableCobblemon() { JsonObject result = new JsonObject(); result.addProperty("available", false); return result; }
}
