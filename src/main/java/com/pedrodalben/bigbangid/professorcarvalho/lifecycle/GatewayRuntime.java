package com.pedrodalben.bigbangid.professorcarvalho.lifecycle;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.BigBangIdProfessorGatewayMod;
import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfig;
import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfigLoader;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayClient;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;
import com.pedrodalben.bigbangid.professorcarvalho.identity.LinkedPlayerCache;
import com.pedrodalben.bigbangid.professorcarvalho.integration.EssentialsProfileBridge;
import com.pedrodalben.bigbangid.professorcarvalho.integration.CobblemonProfileBridge;
import com.pedrodalben.bigbangid.professorcarvalho.integration.CobblemonEventBridge;
import com.pedrodalben.bigbangid.professorcarvalho.integration.NoopEssentialsBridge;
import com.pedrodalben.bigbangid.professorcarvalho.integration.NoopCobblemonBridge;
import com.pedrodalben.bigbangid.professorcarvalho.profile.PlayerProfileCollector;
import com.pedrodalben.bigbangid.professorcarvalho.spool.FileEventSpool;
import com.pedrodalben.bigbangid.professorcarvalho.spool.RetryPolicy;
import com.pedrodalben.bigbangid.professorcarvalho.spool.SpoolProcessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class GatewayRuntime {
    private final GatewayConfigLoader configLoader = new GatewayConfigLoader();
    private final ExecutorService httpExecutor = bounded("professor-gateway-http", 4, 256);
    private final ExecutorService profileExecutor = bounded("professor-gateway-profile", 2, 128);
    private final ExecutorService spoolExecutor = bounded("professor-gateway-spool", 1, 256);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> named("professor-gateway-scheduler", runnable));
    private final Map<UUID, JsonObject> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastProfileRequests = new ConcurrentHashMap<>();
    private final Set<UUID> profileInFlight = ConcurrentHashMap.newKeySet();
    private volatile GatewayConfigLoader.LoadedConfig loaded;
    private volatile GatewayClient client;
    private volatile FileEventSpool spool;
    private volatile LinkedPlayerCache cache;
    private volatile SpoolProcessor processor;
    private volatile CobblemonProfileBridge cobblemonBridge;
    private volatile CobblemonEventBridge cobblemonEventBridge;
    private volatile PlayerProfileCollector collector;
    private volatile MinecraftServer server;
    private volatile String status = "DESLIGADO";
    private volatile long startedAt;

    public void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        startedAt = System.currentTimeMillis();
        try { profileExecutor.execute(this::initialize); } catch (RuntimeException exception) { status = "DEGRADED"; }
    }

    private void initialize() {
        try {
            loaded = configLoader.load();
            cache = new LinkedPlayerCache(loaded.root());
            spool = new FileEventSpool(loaded.root(), loaded.config().spool);
            collector = new PlayerProfileCollector(loadEssentialsBridge(), cobblemonBridge = loadCobblemonBridge(), profileExecutor, modVersion());
            cobblemonEventBridge = new CobblemonEventBridge(this::sendEvent, loaded.config().serverId);
            cobblemonEventBridge.register();
            if (!loaded.valid()) {
                status = loaded.config().enabled ? "DEGRADED" : "DESABILITADO";
                BigBangIdProfessorGatewayMod.LOGGER.warn("Gateway degradado: {}", loaded.errors());
                return;
            }
            client = new GatewayClient(loaded.config(), loaded.secret(), httpExecutor);
            processor = new SpoolProcessor(spool, client, new RetryPolicy(loaded.config().spool), scheduler, loaded.config().maximumInFlightRequests, this::handlePermanentResponse);
            processor.start();
            status = "CONECTADO";
            sendEvent(GatewayEvent.create("gateway.started", loaded.config().serverId, new JsonObject()), "CRITICAL");
            scheduler.scheduleWithFixedDelay(this::heartbeat, 0, loaded.config().heartbeatIntervalSeconds, TimeUnit.SECONDS);
            if (loaded.config().profiles.syncPeriodically)
                scheduler.scheduleWithFixedDelay(this::periodicProfiles, loaded.config().profileSyncIntervalSeconds, loaded.config().profileSyncIntervalSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            status = "DEGRADED";
            BigBangIdProfessorGatewayMod.LOGGER.error("Falha ao iniciar o gateway; Minecraft continuará funcionando.", exception);
        }
    }

    public void link(ServerPlayer player, String code) {
        GatewayConfig config = config();
        if (client == null || loaded == null || !loaded.valid()) { player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes.")); return; }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("CARVALHO-[ABCDEFGHJKMNPQRSTVWXYZ23456789]{8}")) { player.sendSystemMessage(Component.literal("O código de vinculação informado é inválido.")); return; }
        player.sendSystemMessage(Component.literal("⏳ O Professor Carvalho está verificando seu código..."));
        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        client.link(normalized, playerUuid, playerName).whenComplete((response, error) -> {
            if (error != null) { server.execute(() -> player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes."))); return; }
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    try { cache.put(playerUuid, playerName); } catch (java.io.IOException ignored) { }
                    server.execute(() -> {
                        player.sendSystemMessage(Component.literal("✅ Conta vinculada com sucesso! O Professor Carvalho agora poderá acompanhar seu progresso no BigMonCraft. Use /perfil no Discord para consultar sua ficha de treinador."));
                        sendEvent(GatewayEvent.create("identity.link.completed", config.serverId, new JsonObject()), "CRITICAL");
                        syncProfile(player);
                    });
                } else server.execute(() -> player.sendSystemMessage(Component.literal(json.has("message") ? json.get("message").getAsString() : "Não foi possível concluir a vinculação.")));
            } catch (RuntimeException parseError) { server.execute(() -> player.sendSystemMessage(Component.literal("O Professor Carvalho retornou uma resposta inválida. Tente novamente em alguns instantes."))); }
        });
    }

    public void syncProfile(ServerPlayer player) {
        syncProfile(player, true);
    }

    private void syncProfile(ServerPlayer player, boolean notify) {
        UUID playerUuid = player.getUUID();
        GatewayClient currentClient = client;
        LinkedPlayerCache currentCache = cache;
        PlayerProfileCollector currentCollector = collector;
        if (currentClient == null || currentCollector == null || currentCache == null || !currentCache.contains(playerUuid)) {
            if (notify) player.sendSystemMessage(Component.literal("Não encontrei uma vinculação ativa. Use /vincular no Discord e depois /professor vincular <código>."));
            return;
        }
        long now = System.currentTimeMillis();
        long minimumMillis = Math.max(0, config().profileMinimumIntervalSeconds) * 1000L;
        Long last = lastProfileRequests.get(playerUuid);
        if (last != null && now - last < minimumMillis) return;
        if (!profileInFlight.add(playerUuid)) return;
        lastProfileRequests.put(playerUuid, now);
        if (notify) player.sendSystemMessage(Component.literal("⏳ Atualizando sua ficha de treinador..."));
        currentCollector.collect(player, true, FabricLoader.getInstance().isModLoaded("cobblemon")).whenComplete((payload, collectError) -> {
            if (collectError != null) {
                profileInFlight.remove(playerUuid);
                if (notify) server.execute(() -> player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes.")));
                return;
            }
            GatewayEvent event = GatewayEvent.create("player.profile.snapshot", config().serverId, payload);
            currentClient.post("/v1/gateway/profiles", event.bytes()).whenComplete((response, error) -> {
                profileInFlight.remove(playerUuid);
                if (error != null) {
                    if (notify) server.execute(() -> player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes.")));
                    return;
                }
                try {
                    JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("accepted") && json.get("accepted").getAsBoolean()) {
                        profiles.put(playerUuid, payload);
                        try { currentCache.markSynced(playerUuid); } catch (java.io.IOException ignored) { }
                        if (notify) server.execute(() -> player.sendSystemMessage(Component.literal("✅ Ficha atualizada! Use /perfil no Discord para consultar seus dados.")));
                    } else if ("IDENTITY_NOT_LINKED".equals(json.has("code") ? json.get("code").getAsString() : "")) {
                        try { currentCache.remove(playerUuid); } catch (java.io.IOException ignored) { }
                        if (notify) server.execute(() -> player.sendSystemMessage(Component.literal("Não encontrei uma vinculação ativa no Professor Carvalho.")));
                    }
                } catch (RuntimeException ignored) { }
            });
        });
    }

    public void playerJoin(ServerPlayer player) {
        if (loaded != null) sendEvent(GatewayEvent.create("player.session.started", config().serverId, playerPayload(player)), "NORMAL");
        if (loaded != null && config().profiles.syncOnJoin && cache != null && cache.contains(player.getUUID())) {
            scheduler.schedule(() -> server.execute(() -> syncProfile(player)), 5, TimeUnit.SECONDS);
        }
    }

    public void playerLeave(ServerPlayer player) {
        if (loaded != null && config().profiles.syncOnLeave && cache != null && cache.contains(player.getUUID())) syncProfile(player, false);
        if (loaded != null) sendEvent(GatewayEvent.create("player.session.ended", config().serverId, playerPayload(player)), "HIGH");
    }

    private void periodicProfiles() {
        if (server == null || loaded == null || !loaded.config().profiles.enabled) return;
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (cache != null && cache.contains(player.getUUID())) syncProfile(player, false);
        });
    }

    public String status() { return status; }
    public long pending() { return spool == null ? 0 : spool.count(); }
    public long deadLetter() { return spool == null ? 0 : spool.deadLetterCount(); }
    public int linked() { return cache == null ? 0 : cache.size(); }
    public GatewayConfig config() { return loaded == null ? new GatewayConfig() : loaded.config(); }
    public void retry() { if (processor != null) scheduler.execute(processor::process); }
    public void testHeartbeat() { heartbeat(); }
    public String statusText() { return "Professor Carvalho Gateway\nEstado: " + status + "\nServidor: " + config().serverId + "\nJogadores vinculados em cache: " + linked() + "\nEventos pendentes: " + pending() + "\nEventos em dead-letter: " + deadLetter() + "\nVersão do protocolo: 1"; }
    public void profile(ServerPlayer player) {
        JsonObject profile = profiles.get(player.getUUID());
        if (profile == null) { player.sendSystemMessage(Component.literal("Ainda não há uma ficha local. Use /professor sincronizar.")); return; }
        StringBuilder text = new StringBuilder("👤 Ficha de Treinador — ").append(player.getGameProfile().getName());
        JsonObject progression = profile.has("progression") && profile.get("progression").isJsonObject() ? profile.getAsJsonObject("progression") : new JsonObject();
        if (progression.has("rank")) text.append("\n🏅 Rank: ").append(progression.get("rank").getAsString());
        JsonObject economy = profile.has("economy") && profile.get("economy").isJsonObject() ? profile.getAsJsonObject("economy") : new JsonObject();
        if (economy.has("coins") && economy.getAsJsonObject("coins").has("formatted")) text.append("\n💰 ").append(economy.getAsJsonObject("coins").get("formatted").getAsString());
        if (economy.has("gems") && economy.getAsJsonObject("gems").has("formatted")) text.append("\n💎 ").append(economy.getAsJsonObject("gems").get("formatted").getAsString());
        JsonObject cobblemon = profile.has("cobblemon") && profile.get("cobblemon").isJsonObject() ? profile.getAsJsonObject("cobblemon") : new JsonObject();
        if (cobblemon.has("party") && cobblemon.get("party").isJsonArray()) text.append("\n🎒 Equipe: ").append(cobblemon.getAsJsonArray("party").size()).append(" Pokémon");
        text.append("\n\nUse /perfil no Discord para consultar os dados completos.");
        player.sendSystemMessage(Component.literal(text.toString()));
    }

    public void reload() { try { profileExecutor.execute(this::reloadAsync); } catch (RuntimeException ignored) { } }

    private void reloadAsync() {
        try {
            GatewayConfigLoader.LoadedConfig refreshed = configLoader.load();
            loaded = refreshed;
            if (!refreshed.valid()) {
                client = null;
                if (processor != null) processor.updateClient(null);
                status = refreshed.config().enabled ? "DEGRADED" : "DESABILITADO";
                return;
            }
            client = new GatewayClient(refreshed.config(), refreshed.secret(), httpExecutor);
            if (processor != null) processor.updateClient(client);
            status = "CONECTADO";
        } catch (Exception exception) {
            BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao recarregar configuração do gateway.");
        }
    }

    public void shutdown() {
        try { if (loaded != null && client != null) profileExecutor.execute(() -> sendEvent(GatewayEvent.create("gateway.stopping", config().serverId, new JsonObject()), "CRITICAL")); } catch (Exception ignored) { }
        scheduler.shutdown(); httpExecutor.shutdown(); profileExecutor.shutdown(); spoolExecutor.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config().shutdownFlushTimeoutSeconds);
        await(scheduler, deadline);
        await(profileExecutor, deadline);
        await(httpExecutor, deadline);
        await(spoolExecutor, deadline);
        status = "DESLIGADO";
    }

    private void stop() { shutdown(); }

    private void heartbeat() {
        if (client == null || loaded == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("gatewayVersion", modVersion()); payload.addProperty("protocolVersion", "1"); payload.addProperty("minecraftVersion", SharedConstants.VERSION_STRING); payload.addProperty("onlinePlayers", server.getPlayerList().getPlayerCount()); payload.addProperty("linkedPlayersOnline", linkedOnline()); payload.addProperty("spoolPending", pending()); payload.addProperty("deadLetterCount", deadLetter()); payload.addProperty("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000); JsonObject modules = new JsonObject(); modules.addProperty("bigBangEssentials", FabricLoader.getInstance().isModLoaded("bigbangessentials")); modules.addProperty("cobblemon", FabricLoader.getInstance().isModLoaded("cobblemon")); payload.add("modules", modules);
        sendEvent(GatewayEvent.create("gateway.heartbeat", config().serverId, payload), "LOW");
    }

    private void sendEvent(GatewayEvent event, String priority) {
        FileEventSpool currentSpool = spool;
        if (currentSpool == null || !config().spool.enabled) return;
        try { spoolExecutor.execute(() -> { try { currentSpool.enqueue(event, priority); } catch (Exception exception) { BigBangIdProfessorGatewayMod.LOGGER.warn("Não foi possível armazenar evento {}.", event.eventType()); } }); }
        catch (RuntimeException ignored) { }
    }
    private void handlePermanentResponse(com.pedrodalben.bigbangid.professorcarvalho.spool.SpoolEntry entry, com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayResponse response) {
        if (!"IDENTITY_NOT_LINKED".equals(response.code()) || cache == null) return;
        try {
            JsonObject player = entry.event.getAsJsonObject("payload").getAsJsonObject("player");
            cache.remove(UUID.fromString(player.get("minecraftUuid").getAsString()));
        } catch (Exception ignored) { }
    }
    private JsonObject playerPayload(ServerPlayer player) { JsonObject payload = new JsonObject(); payload.addProperty("minecraftUuid", player.getUUID().toString()); payload.addProperty("minecraftName", player.getGameProfile().getName()); return payload; }
    private int linkedOnline() { int count = 0; for (ServerPlayer player : server.getPlayerList().getPlayers()) if (cache != null && cache.contains(player.getUUID())) count++; return count; }
    private String modVersion() { return FabricLoader.getInstance().getModContainer(BigBangIdProfessorGatewayMod.MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("0.1.0"); }
    private EssentialsProfileBridge loadEssentialsBridge() { return loadOptional("com.pedrodalben.bigbangid.professorcarvalho.integration.BigBangEssentialsBridge", EssentialsProfileBridge.class, new NoopEssentialsBridge()); }
    private CobblemonProfileBridge loadCobblemonBridge() { return loadOptional("com.pedrodalben.bigbangid.professorcarvalho.integration.CobblemonBridge", CobblemonProfileBridge.class, new NoopCobblemonBridge()); }
    private static <T> T loadOptional(String className, Class<T> type, T fallback) { try { return type.cast(Class.forName(className).getDeclaredConstructor().newInstance()); } catch (Throwable ignored) { return fallback; } }
    private static Thread named(String name, Runnable runnable) { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; }
    private static ExecutorService bounded(String name, int threads, int queueSize) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueSize), runnable -> named(name, runnable), new ThreadPoolExecutor.AbortPolicy());
    }
    private static void await(ExecutorService executor, long deadline) {
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
