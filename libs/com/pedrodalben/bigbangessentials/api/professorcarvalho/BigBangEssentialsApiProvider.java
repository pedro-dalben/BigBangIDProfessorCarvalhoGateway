package com.pedrodalben.bigbangessentials.api.professorcarvalho;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
public interface BigBangEssentialsApiProvider {
    record Capabilities(boolean economy, boolean gems, boolean jobs) {}
    static Optional<BigBangEssentialsApiProvider> get() { return Optional.empty(); }
    boolean isPresent();
    Capabilities capabilities();
    CompletableFuture<PlayerEssentialsProfileSnapshot> getPlayerProfile(UUID playerUuid);
    CompletableFuture<PlayerEssentialsProfileSnapshot> fetchProfile(UUID playerUuid, boolean includeParty);
}
