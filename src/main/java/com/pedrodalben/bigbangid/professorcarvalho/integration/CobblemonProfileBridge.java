package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

public interface CobblemonProfileBridge {
    JsonObject collect(ServerPlayer player);
    boolean available();
}
