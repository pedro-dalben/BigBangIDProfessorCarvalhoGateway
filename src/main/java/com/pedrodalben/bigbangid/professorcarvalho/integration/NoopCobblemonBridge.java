package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

public final class NoopCobblemonBridge implements CobblemonProfileBridge {
    @Override public JsonObject collect(ServerPlayer player) { JsonObject result = new JsonObject(); result.addProperty("available", false); return result; }
    @Override public boolean available() { return false; }
}
