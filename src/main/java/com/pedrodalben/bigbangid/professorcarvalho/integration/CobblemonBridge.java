package com.pedrodalben.bigbangid.professorcarvalho.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

public final class CobblemonBridge implements CobblemonProfileBridge {
    public boolean available() { return Cobblemon.INSTANCE != null && Cobblemon.INSTANCE.getStorage() != null; }

    public JsonObject collect(ServerPlayer player) {
        JsonObject result = new JsonObject();
        if (!available()) { result.addProperty("available", false); return result; }
        result.addProperty("available", true);
        JsonArray party = new JsonArray();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player).toGappyList()) {
            if (pokemon == null) continue;
            JsonObject item = new JsonObject();
            item.addProperty("species", pokemon.getSpecies().getName());
            item.addProperty("form", pokemon.getForm() == null ? null : pokemon.getForm().getName());
            item.addProperty("displayName", pokemon.getDisplayName(false).getString());
            item.addProperty("level", pokemon.getLevel());
            item.addProperty("shiny", pokemon.getShiny());
            party.add(item);
        }
        result.add("party", party);
        PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player.getUUID());
        if (pokedex != null) {
            int seen = 0; int caught = 0;
            for (var record : pokedex.getSpeciesRecords().values()) {
                if (record.getKnowledge().compareTo(PokedexEntryProgress.ENCOUNTERED) >= 0) seen++;
                if (record.getKnowledge().compareTo(PokedexEntryProgress.CAUGHT) >= 0) caught++;
            }
            JsonObject progress = new JsonObject(); progress.addProperty("available", true); progress.addProperty("seen", seen); progress.addProperty("caught", caught); result.add("pokedex", progress);
        }
        return result;
    }
}
