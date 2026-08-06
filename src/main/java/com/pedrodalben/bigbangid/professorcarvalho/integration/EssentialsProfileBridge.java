package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EssentialsProfileBridge {
    CompletableFuture<JsonObject> collect(UUID playerUuid);
    boolean available();
}
