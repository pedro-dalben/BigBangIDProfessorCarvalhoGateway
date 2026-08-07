package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.BigBangIdProfessorGatewayMod;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

public final class CobblemonEventBridge {
    private final EventEmitter emitter;
    private final String serverId;
    private volatile boolean registered;

    public interface EventEmitter {
        void sendEvent(GatewayEvent event, String priority);
    }

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
            var eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            var capturedField = eventsClass.getField("POKEMON_CAPTURED");
            var observable = capturedField.get(null);
            var subscribeMethod = observable.getClass().getMethod("subscribe", java.util.function.Consumer.class);
            subscribeMethod.invoke(observable, (java.util.function.Consumer<Object>) (event) -> {
                try {
                    var getPlayer = event.getClass().getMethod("getPlayer");
                    var getPokemon = event.getClass().getMethod("getPokemon");
                    var getPokeBallEntity = event.getClass().getMethod("getPokeBallEntity");

                    var player = (ServerPlayer) getPlayer.invoke(event);
                    var pokemon = getPokemon.invoke(event);
                    var ballEntity = getPokeBallEntity.invoke(event);

                    if (player == null || pokemon == null) return;

                    JsonObject payload = new JsonObject();
                    payload.addProperty("minecraftUuid", player.getUUID().toString());

                    var species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
                    if (species != null) {
                        var speciesName = species.getClass().getMethod("getName").invoke(species);
                        payload.addProperty("species", speciesName.toString().toLowerCase(java.util.Locale.ROOT));
                    }

                    var form = pokemon.getClass().getMethod("getForm").invoke(pokemon);
                    if (form != null) {
                        var formName = form.getClass().getMethod("getName").invoke(form);
                        if (formName != null && !formName.toString().isEmpty()) {
                            payload.addProperty("form", formName.toString());
                        }
                    }

                    int level = (int) pokemon.getClass().getMethod("getLevel").invoke(pokemon);
                    payload.addProperty("level", level);

                    boolean shiny = (boolean) pokemon.getClass().getMethod("getShiny").invoke(pokemon);
                    payload.addProperty("shiny", shiny);

                    try {
                        var genderMethod = pokemon.getClass().getMethod("getGender");
                        var gender = genderMethod.invoke(pokemon);
                        if (gender != null) {
                            payload.addProperty("gender", gender.toString().toLowerCase(java.util.Locale.ROOT));
                        }
                    } catch (NoSuchMethodException ignored) { }

                    if (ballEntity != null) {
                        try {
                            var ballItem = ballEntity.getClass().getMethod("getPokeBall").invoke(ballEntity);
                            if (ballItem != null) {
                                var ballName = ballItem.getClass().getMethod("getName").invoke(ballItem);
                                if (ballName != null) {
                                    payload.addProperty("ball", ballName.toString());
                                }
                            }
                        } catch (Exception ignored) { }
                    }

                    payload.addProperty("occurredAt", java.time.Instant.now().toString());

                    GatewayEvent gatewayEvent = GatewayEvent.create("pokemon.capture.completed", serverId, payload);
                    emitter.sendEvent(gatewayEvent, "NORMAL");
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
            var eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            var evolvedField = eventsClass.getField("EVOLUTION_COMPLETE");
            var observable = evolvedField.get(null);
            var subscribeMethod = observable.getClass().getMethod("subscribe", java.util.function.Consumer.class);
            subscribeMethod.invoke(observable, (java.util.function.Consumer<Object>) (event) -> {
                try {
                    var getPokemon = event.getClass().getMethod("getPokemon");
                    var pokemon = getPokemon.invoke(event);

                    Object sourcePokemon = null;
                    try {
                        var getSource = event.getClass().getMethod("getSourcePokemon");
                        sourcePokemon = getSource.invoke(event);
                    } catch (NoSuchMethodException ignored) {
                        var getOld = event.getClass().getMethod("getOldPokemon");
                        sourcePokemon = getOld.invoke(event);
                    }

                    if (pokemon == null || sourcePokemon == null) return;

                    Object toSpecies = null;
                    Object fromSpecies = null;
                    try {
                        var getSpecies = pokemon.getClass().getMethod("getSpecies");
                        toSpecies = getSpecies.invoke(pokemon);
                        fromSpecies = getSpecies.invoke(sourcePokemon);
                    } catch (Exception ex) {
                        BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao obter species de evolução: {}", ex.getMessage());
                        return;
                    }

                    if (fromSpecies == null || toSpecies == null) return;

                    var fromName = fromSpecies.getClass().getMethod("getName").invoke(fromSpecies);
                    var toName = toSpecies.getClass().getMethod("getName").invoke(toSpecies);

                    ServerPlayer player = null;
                    try {
                        var getOwner = pokemon.getClass().getMethod("getOwnerPlayer");
                        player = (ServerPlayer) getOwner.invoke(pokemon);
                    } catch (Exception ex) {
                        var getPlayer = pokemon.getClass().getMethod("getPlayer");
                        player = (ServerPlayer) getPlayer.invoke(pokemon);
                    }

                    JsonObject payload = new JsonObject();
                    if (player != null) {
                        payload.addProperty("minecraftUuid", player.getUUID().toString());
                    }
                    payload.addProperty("fromSpecies", fromName.toString().toLowerCase(java.util.Locale.ROOT));
                    payload.addProperty("toSpecies", toName.toString().toLowerCase(java.util.Locale.ROOT));

                    try {
                        var toForm = pokemon.getClass().getMethod("getForm").invoke(pokemon);
                        if (toForm != null) {
                            var formName = toForm.getClass().getMethod("getName").invoke(toForm);
                            if (formName != null && !formName.toString().isEmpty()) {
                                payload.addProperty("toForm", formName.toString());
                            }
                        }
                    } catch (Exception ignored) { }

                    payload.addProperty("occurredAt", java.time.Instant.now().toString());

                    GatewayEvent gatewayEvent = GatewayEvent.create("pokemon.evolution.completed", serverId, payload);
                    emitter.sendEvent(gatewayEvent, "NORMAL");
                } catch (Exception ex) {
                    BigBangIdProfessorGatewayMod.LOGGER.warn("Falha ao processar evento de evolução: {}", ex.getMessage());
                }
            });
        } catch (Exception ex) {
            BigBangIdProfessorGatewayMod.LOGGER.warn("Não foi possível registrar listener de evolução do Cobblemon: {}", ex.getMessage());
        }
    }
}
