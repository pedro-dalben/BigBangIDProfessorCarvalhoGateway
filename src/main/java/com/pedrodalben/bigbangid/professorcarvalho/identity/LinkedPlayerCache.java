package com.pedrodalben.bigbangid.professorcarvalho.identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LinkedPlayerCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public LinkedPlayerCache(Path root) throws IOException {
        path = root.resolve("linked-players-cache.json");
        Files.createDirectories(root);
        if (Files.exists(path)) {
            Map<?, ?> loaded = GSON.fromJson(Files.readString(path), Map.class);
            if (loaded != null) for (Object key : loaded.keySet()) try { Entry value = GSON.fromJson(GSON.toJson(loaded.get(key)), Entry.class); entries.put(UUID.fromString(String.valueOf(key)), value); } catch (RuntimeException ignored) { }
        }
    }

    public synchronized boolean contains(UUID uuid) { return entries.containsKey(uuid); }
    public synchronized void put(UUID uuid, String name) throws IOException { entries.put(uuid, new Entry(name, Instant.now().toString(), Instant.now().toString())); save(); }
    public synchronized void markSynced(UUID uuid) throws IOException { Entry current = entries.get(uuid); if (current != null) { entries.put(uuid, new Entry(current.lastKnownName, current.linkedAt, Instant.now().toString())); save(); } }
    public synchronized void remove(UUID uuid) throws IOException { entries.remove(uuid); save(); }
    public synchronized int size() { return entries.size(); }

    private void save() throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(entries), StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record Entry(String lastKnownName, String linkedAt, String lastSuccessfulSyncAt) {}
}
