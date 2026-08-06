package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.api.professorcarvalho.BigBangEssentialsApiProvider;
import com.pedrodalben.bigbangessentials.api.professorcarvalho.JobProgressSnapshot;
import com.pedrodalben.bigbangessentials.api.professorcarvalho.PlayerEssentialsProfileSnapshot;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class BigBangEssentialsBridge implements EssentialsProfileBridge {
    public CompletableFuture<JsonObject> collect(UUID playerUuid) {
        return BigBangEssentialsApiProvider.get().map(api -> api.getPlayerProfile(playerUuid).orTimeout(3, TimeUnit.SECONDS).thenApply(this::toJson)).orElseGet(() -> CompletableFuture.completedFuture(unavailable()));
    }

    public boolean available() { return BigBangEssentialsApiProvider.get().isPresent(); }

    private JsonObject toJson(PlayerEssentialsProfileSnapshot snapshot) {
        JsonObject result = new JsonObject();
        JsonObject progression = new JsonObject();
        snapshot.rankDisplayName().ifPresent(value -> progression.addProperty("rank", value));
        snapshot.playtimeSeconds().ifPresent(value -> progression.addProperty("playtimeSeconds", value));
        JsonArray jobs = new JsonArray();
        for (JobProgressSnapshot job : snapshot.jobs()) { JsonObject item = new JsonObject(); item.addProperty("id", job.jobId()); item.addProperty("displayName", job.displayName()); item.addProperty("level", job.level()); item.addProperty("experience", job.experience()); jobs.add(item); }
        progression.add("jobs", jobs);
        JsonObject economy = new JsonObject();
        snapshot.coinBalance().ifPresent(value -> { JsonObject coins = new JsonObject(); coins.addProperty("available", true); coins.addProperty("amount", value.toPlainString()); coins.addProperty("formatted", value.toPlainString() + " moedas"); economy.add("coins", coins); });
        snapshot.gemBalance().ifPresent(value -> { JsonObject gems = new JsonObject(); gems.addProperty("available", true); gems.addProperty("amount", value); gems.addProperty("formatted", value + " Gemas"); economy.add("gems", gems); });
        result.add("progression", progression); result.add("economy", economy);
        JsonObject modules = new JsonObject(); var capabilities = BigBangEssentialsApiProvider.get().map(api -> api.capabilities()).orElse(null); modules.addProperty("bigBangEssentials", "available"); modules.addProperty("economy", capabilities != null && capabilities.economy() ? "available" : "unavailable"); modules.addProperty("gems", capabilities != null && capabilities.gems() ? "available" : "unavailable"); modules.addProperty("jobs", capabilities != null && capabilities.jobs() ? "available" : "unavailable"); result.add("modules", modules); return result;
    }

    private static JsonObject unavailable() { JsonObject result = new JsonObject(); result.add("progression", new JsonObject()); result.add("economy", new JsonObject()); JsonObject modules = new JsonObject(); modules.addProperty("bigBangEssentials", "unavailable"); result.add("modules", modules); return result; }
}
