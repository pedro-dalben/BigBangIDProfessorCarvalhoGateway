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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class GatewayRuntime {
    private final GatewayConfigLoader configLoader = new GatewayConfigLoader();
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(4, runnable -> named("professor-gateway-http", runnable));
    private final ExecutorService profileExecutor = Executors.newFixedThreadPool(2, runnable -> named("professor-gateway-profile", runnable));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> named("professor-gateway-scheduler", runnable));
    private final Map<UUID, JsonObject> profiles = new ConcurrentHashMap<>();
    private volatile GatewayConfigLoader.LoadedConfig loaded;
    private volatile GatewayClient client;
    private volatile FileEventSpool spool;
    private volatile LinkedPlayerCache cache;
    private volatile SpoolProcessor processor;
    private volatile PlayerProfileCollector collector;
    private volatile MinecraftServer server;
    private volatile String status = "DESLIGADO";
    private volatile long startedAt;

    public void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        startedAt = System.currentTimeMillis();
        profileExecutor.execute(this::initialize);
    }

    private void initialize() {
        try {
            loaded = configLoader.load();
            cache = new LinkedPlayerCache(loaded.root());
            spool = new FileEventSpool(loaded.root(), loaded.config().spool);
            collector = new PlayerProfileCollector(loadEssentialsBridge(), loadCobblemonBridge(), profileExecutor, modVersion());
            if (!loaded.valid()) {
                status = loaded.config().enabled ? "DEGRADED" : "DESABILITADO";
                BigBangIdProfessorGatewayMod.LOGGER.warn("Gateway degradado: {}", loaded.errors());
                return;
            }
            client = new GatewayClient(loaded.config(), loaded.secret(), httpExecutor);
            processor = new SpoolProcessor(spool, client, new RetryPolicy(loaded.config().spool), scheduler, loaded.config().maximumInFlightRequests);
            processor.start();
            status = "CONECTADO";
            sendEvent(GatewayEvent.create("gateway.started", loaded.config().serverId, new JsonObject()), "CRITICAL");
            scheduler.scheduleWithFixedDelay(this::heartbeat, 0, loaded.config().heartbeatIntervalSeconds, TimeUnit.SECONDS);
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
        client.link(normalized, player.getUUID(), player.getGameProfile().getName()).whenComplete((response, error) -> server.execute(() -> {
            if (error != null) { player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes.")); return; }
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    try { cache.put(player.getUUID(), player.getGameProfile().getName()); } catch (java.io.IOException ignored) { }
                    player.sendSystemMessage(Component.literal("✅ Conta vinculada com sucesso! O Professor Carvalho agora poderá acompanhar seu progresso no BigMonCraft. Use /perfil no Discord para consultar sua ficha de treinador."));
                    sendEvent(GatewayEvent.create("identity.link.completed", config.serverId, new JsonObject()), "CRITICAL");
                    syncProfile(player);
                } else player.sendSystemMessage(Component.literal(json.has("message") ? json.get("message").getAsString() : "Não foi possível concluir a vinculação."));
            } catch (RuntimeException parseError) { player.sendSystemMessage(Component.literal("O Professor Carvalho retornou uma resposta inválida. Tente novamente em alguns instantes.")); }
        }));
    }

    public void syncProfile(ServerPlayer player) {
        if (client == null || collector == null || cache == null || !cache.contains(player.getUUID())) { player.sendSystemMessage(Component.literal("Não encontrei uma vinculação ativa. Use /vincular no Discord e depois /professor vincular <código>.")); return; }
        player.sendSystemMessage(Component.literal("⏳ Atualizando sua ficha de treinador..."));
        collector.collect(player, true, FabricLoader.getInstance().isModLoaded("cobblemon"))
            .thenAccept(payload -> {
                GatewayEvent event = GatewayEvent.create("player.profile.snapshot", config().serverId, payload);
                client.post("/v1/gateway/profiles", event.bytes()).whenComplete((response, error) -> server.execute(() -> {
                    if (error != null) { player.sendSystemMessage(Component.literal("O Professor Carvalho está temporariamente indisponível. Tente novamente em alguns instantes.")); return; }
                    try {
                        JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                        if (json.has("accepted") && json.get("accepted").getAsBoolean()) { profiles.put(player.getUUID(), payload); try { cache.markSynced(player.getUUID()); } catch (java.io.IOException ignored) { } player.sendSystemMessage(Component.literal("✅ Ficha atualizada! Use /perfil no Discord para consultar seus dados.")); }
                        else if ("IDENTITY_NOT_LINKED".equals(json.has("code") ? json.get("code").getAsString() : "")) { try { cache.remove(player.getUUID()); } catch (java.io.IOException ignored) { } player.sendSystemMessage(Component.literal("Não encontrei uma vinculação ativa no Professor Carvalho.")); }
                    } catch (RuntimeException ignored) { }
                }));
            })
            .exceptionally(error -> null);
    }

    public void playerJoin(ServerPlayer player) {
        if (loaded != null) sendEvent(GatewayEvent.create("player.session.started", config().serverId, playerPayload(player)), "NORMAL");
        if (loaded != null && cache != null && cache.contains(player.getUUID())) {
            scheduler.schedule(() -> server.execute(() -> syncProfile(player)), 5, TimeUnit.SECONDS);
        }
    }

    public void playerLeave(ServerPlayer player) {
        if (loaded != null) sendEvent(GatewayEvent.create("player.session.ended", config().serverId, playerPayload(player)), "HIGH");
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

    public void reload() { profileExecutor.execute(this::reloadAsync); }

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
        try { if (loaded != null && client != null) sendEvent(GatewayEvent.create("gateway.stopping", config().serverId, new JsonObject()), "CRITICAL"); } catch (Exception ignored) { }
        scheduler.shutdown(); httpExecutor.shutdown(); profileExecutor.shutdown();
        try { scheduler.awaitTermination(config().shutdownFlushTimeoutSeconds, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        status = "DESLIGADO";
    }

    private void stop() { shutdown(); }

    private void heartbeat() {
        if (client == null || loaded == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("gatewayVersion", modVersion()); payload.addProperty("protocolVersion", "1"); payload.addProperty("minecraftVersion", SharedConstants.VERSION_STRING); payload.addProperty("onlinePlayers", server.getPlayerList().getPlayerCount()); payload.addProperty("linkedPlayersOnline", linkedOnline()); payload.addProperty("spoolPending", pending()); payload.addProperty("deadLetterCount", deadLetter()); payload.addProperty("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000); JsonObject modules = new JsonObject(); modules.addProperty("bigBangEssentials", FabricLoader.getInstance().isModLoaded("bigbangessentials")); modules.addProperty("cobblemon", FabricLoader.getInstance().isModLoaded("cobblemon")); payload.add("modules", modules);
        sendEvent(GatewayEvent.create("gateway.heartbeat", config().serverId, payload), "LOW");
    }

    private void sendEvent(GatewayEvent event, String priority) { try { if (spool != null && config().spool.enabled) spool.enqueue(event, priority); } catch (Exception exception) { BigBangIdProfessorGatewayMod.LOGGER.warn("Não foi possível armazenar evento {}.", event.eventType()); } }
    private JsonObject playerPayload(ServerPlayer player) { JsonObject payload = new JsonObject(); payload.addProperty("minecraftUuid", player.getUUID().toString()); payload.addProperty("minecraftName", player.getGameProfile().getName()); return payload; }
    private int linkedOnline() { int count = 0; for (ServerPlayer player : server.getPlayerList().getPlayers()) if (cache != null && cache.contains(player.getUUID())) count++; return count; }
    private String modVersion() { return FabricLoader.getInstance().getModContainer(BigBangIdProfessorGatewayMod.MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("0.1.0"); }
    private EssentialsProfileBridge loadEssentialsBridge() { return loadOptional("com.pedrodalben.bigbangid.professorcarvalho.integration.BigBangEssentialsBridge", EssentialsProfileBridge.class, new NoopEssentialsBridge()); }
    private CobblemonProfileBridge loadCobblemonBridge() { return loadOptional("com.pedrodalben.bigbangid.professorcarvalho.integration.CobblemonBridge", CobblemonProfileBridge.class, new NoopCobblemonBridge()); }
    private static <T> T loadOptional(String className, Class<T> type, T fallback) { try { return type.cast(Class.forName(className).getDeclaredConstructor().newInstance()); } catch (Throwable ignored) { return fallback; } }
    private static Thread named(String name, Runnable runnable) { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; }
}
