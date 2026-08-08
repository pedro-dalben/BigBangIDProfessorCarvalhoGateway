package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.BigBangIdProfessorGatewayMod;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Consumer;

public final class CobblemonEventBridge {
    private final EventEmitter emitter;
    private final String serverId;
    private volatile boolean registered;

    public CobblemonEventBridge(EventEmitter emitter, String serverId) {
        this.emitter = emitter;
        this.serverId = serverId;
    }

    public void register() {
        if (registered) return;
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            BigBangIdProfessorGatewayMod.LOGGER.info("Cobblemon não está carregado. Eventos de captura/evolução não serão registrados.");
            return;
        }
        registered = true;
        registerCaptureListener();
        registerEvolutionListener();
        BigBangIdProfessorGatewayMod.LOGGER.info("Eventos de captura e evolução do Cobblemon registrados.");
    }

    private void registerCaptureListener() {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field capturedField = eventsClass.getField("POKEMON_CAPTURED");
            Object observable = capturedField.get(null);
            Method subscribeMethod = observable.getClass().getMethod("subscribe", Consumer.class);
            subscribeMethod.invoke(observable, (Consumer<Object>) event -> {
                try {
                    Object pokemon = event.getClass().getMethod("getPokemon").invoke(event);
                    ServerPlayer player = (ServerPlayer) event.getClass().getMethod("getPlayer").invoke(event);
                    Object ballEntity = event.getClass().getMethod("getPokeBallEntity").invoke(event);
                    if (player == null || pokemon == null) return;
                    JsonObject payload = new JsonObject();
                    payload.addProperty("minecraftUuid", player.getUUID().toString());
                    Object species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
                    if (species != null) {
                        Object speciesName = species.getClass().getMethod("getName").invoke(species);
                        payload.addProperty("species", speciesName.toString().toLowerCase(Locale.ROOT));
                    }
                    Object form = pokemon.getClass().getMethod("getForm").invoke(pokemon);
                    if (form != null) {
                        Object formName = form.getClass().getMethod("getName").invoke(form);
                        if (formName != null && !formName.toString().isEmpty()) payload.addProperty("form", formName.toString());
                    }
                    payload.addProperty("level", (Number) pokemon.getClass().getMethod("getLevel").invoke(pokemon));
                    payload.addProperty("shiny", (Boolean) pokemon.getClass().getMethod("getShiny").invoke(pokemon));
                    try {
                        Object gender = pokemon.getClass().getMethod("getGender").invoke(pokemon);
                        if (gender != null) payload.addProperty("gender", gender.toString().toLowerCase(Locale.ROOT));
                    } catch (NoSuchMethodException ignored) { }
                    if (ballEntity != null) {
                        try {
                            Object ballItem = ballEntity.getClass().getMethod("getPokeBall").invoke(ballEntity);
                            if (ballItem != null) {
                                Object ballName = ballItem.getClass().getMethod("getName").invoke(ballItem);
                                if (ballName != null) payload.addProperty("ball", ballName.toString());
                            }
                        } catch (Exception ignored) { }
                    }
                    payload.addProperty("occurredAt", Instant.now().toString());
                    emitter.sendEvent(GatewayEvent.create("pokemon.capture.completed", serverId, (JsonElement) payload), "NORMAL");
                } catch (Exception ex) {
                    BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao processar evento de captura: {}", ex.getMessage());
                }
            });
        } catch (Exception ex) {
            BigBangIdProfessorGatewayMod.LOGGER.warn("Não foi possível registrar listener de captura do Cobblemon: {}", ex.getMessage());
        }
    }

    private void registerEvolutionListener() {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field evolvedField = eventsClass.getField("EVOLUTION_COMPLETE");
            Object observable = evolvedField.get(null);
            Method subscribeMethod = observable.getClass().getMethod("subscribe", Consumer.class);
            subscribeMethod.invoke(observable, (Consumer<Object>) event -> {
                try {
                    Object pokemon = event.getClass().getMethod("getPokemon").invoke(event);
                    Object sourcePokemon;
                    try {
                        sourcePokemon = event.getClass().getMethod("getSourcePokemon").invoke(event);
                    } catch (NoSuchMethodException ignored) {
                        sourcePokemon = event.getClass().getMethod("getOldPokemon").invoke(event);
                    }
                    if (pokemon == null || sourcePokemon == null) return;
                    Object fromSpecies;
                    Object toSpecies;
                    try {
                        Method getSpecies = pokemon.getClass().getMethod("getSpecies");
                        toSpecies = getSpecies.invoke(pokemon);
                        fromSpecies = getSpecies.invoke(sourcePokemon);
                    } catch (Exception ex) {
                        BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao obter species de evolução: {}", ex.getMessage());
                        return;
                    }
                    if (fromSpecies == null || toSpecies == null) return;
                    Object fromName = fromSpecies.getClass().getMethod("getName").invoke(fromSpecies);
                    Object toName = toSpecies.getClass().getMethod("getName").invoke(toSpecies);
                    ServerPlayer player = null;
                    try {
                        player = (ServerPlayer) pokemon.getClass().getMethod("getOwnerPlayer").invoke(pokemon);
                    } catch (Exception ex) {
                        player = (ServerPlayer) pokemon.getClass().getMethod("getPlayer").invoke(pokemon);
                    }
                    JsonObject payload = new JsonObject();
                    if (player != null) payload.addProperty("minecraftUuid", player.getUUID().toString());
                    payload.addProperty("fromSpecies", fromName.toString().toLowerCase(Locale.ROOT));
                    payload.addProperty("toSpecies", toName.toString().toLowerCase(Locale.ROOT));
                    try {
                        Object toForm = pokemon.getClass().getMethod("getForm").invoke(pokemon);
                        if (toForm != null) {
                            Object formName = toForm.getClass().getMethod("getName").invoke(toForm);
                            if (formName != null && !formName.toString().isEmpty()) payload.addProperty("toForm", formName.toString());
                        }
                    } catch (Exception ignored) { }
                    payload.addProperty("occurredAt", Instant.now().toString());
                    emitter.sendEvent(GatewayEvent.create("pokemon.evolution.completed", serverId, (JsonElement) payload), "NORMAL");
                } catch (Exception ex) {
                    BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao processar evento de evolução: {}", ex.getMessage());
                }
            });
        } catch (Exception ex) {
            BigBangIdProfessorGatewayMod.LOGGER.warn("Não foi possível registrar listener de evolução do Cobblemon: {}", ex.getMessage());
        }
    }

    public interface EventEmitter {
        void sendEvent(GatewayEvent event, String priority);
    }
}
