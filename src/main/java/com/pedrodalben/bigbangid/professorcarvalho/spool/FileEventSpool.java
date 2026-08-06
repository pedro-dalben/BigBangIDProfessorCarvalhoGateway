package com.pedrodalben.bigbangid.professorcarvalho.spool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfig;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;
import com.pedrodalben.bigbangid.professorcarvalho.security.HmacSigner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class FileEventSpool {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path spoolDirectory;
    private final Path deadLetterDirectory;
    private final Path quarantineDirectory;
    private final int maximumEvents;
    private final int maximumEventAgeDays;

    public FileEventSpool(Path root, GatewayConfig.Spool config) throws IOException {
        Path safeRoot = root.toAbsolutePath().normalize();
        spoolDirectory = child(safeRoot, config.directory);
        deadLetterDirectory = child(safeRoot, config.deadLetterDirectory);
        quarantineDirectory = child(safeRoot, config.quarantineDirectory);
        maximumEvents = config.maximumEvents;
        maximumEventAgeDays = config.maximumEventAgeDays;
        Files.createDirectories(spoolDirectory);
        Files.createDirectories(deadLetterDirectory);
        Files.createDirectories(quarantineDirectory);
    }

    public synchronized boolean enqueue(GatewayEvent event, String priority) throws IOException {
        if ("gateway.heartbeat".equals(event.eventType())) {
            for (Path pending : files(spoolDirectory)) {
                try {
                    if (Files.readString(pending).contains("\"eventType\":\"gateway.heartbeat\"")) Files.deleteIfExists(pending);
                } catch (IOException ignored) { }
            }
        }
        List<Path> existing = files(spoolDirectory);
        if (existing.size() >= maximumEvents) {
            int incomingPriority = priorityValue(priority);
            Path discard = existing.stream().filter(path -> {
                try { SpoolEntry current = GSON.fromJson(Files.readString(path), SpoolEntry.class); return current != null && priorityValue(current.priority) < incomingPriority; }
                catch (Exception ignored) { return false; }
            }).findFirst().orElse(null);
            if (discard == null) return false;
            Files.deleteIfExists(discard);
        }
        SpoolEntry entry = new SpoolEntry();
        entry.event = event.asJson();
        entry.bodySha256 = HmacSigner.bodyHash(event.bytes());
        entry.priority = priority;
        entry.createdAt = Instant.now().toString();
        entry.nextAttemptAt = Instant.EPOCH.toString();
        writeAtomic(spoolDirectory.resolve(event.eventId() + ".json"), GSON.toJson(entry));
        return true;
    }

    public synchronized List<SpoolEntry> ready() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Math.max(1, maximumEventAgeDays), ChronoUnit.DAYS);
        List<SpoolEntry> ready = new ArrayList<>();
        for (Path path : files(spoolDirectory)) {
            SpoolEntry entry = read(path);
            if (entry == null) continue;
            try {
                if (entry.createdAt != null && Instant.parse(entry.createdAt).isBefore(cutoff)) {
                    entry.lastErrorCode = "EVENT_AGE_EXCEEDED";
                    deadLetter(entry);
                    continue;
                }
            } catch (RuntimeException ignored) {
                try { move(path, quarantineDirectory); } catch (IOException ignoredMove) { }
                continue;
            } catch (IOException ignored) {
                continue;
            }
            try {
                if (!entry.nextAttempt().isAfter(now)) ready.add(entry);
            } catch (RuntimeException ignored) {
                try { move(path, quarantineDirectory); } catch (IOException ignoredMove) { }
            }
        }
        return ready;
    }

    public synchronized void save(SpoolEntry entry) throws IOException { writeAtomic(spoolDirectory.resolve(entry.event.get("eventId").getAsString() + ".json"), GSON.toJson(entry)); }

    public synchronized void remove(SpoolEntry entry) throws IOException { Files.deleteIfExists(spoolDirectory.resolve(entry.event.get("eventId").getAsString() + ".json")); }

    public synchronized void deadLetter(SpoolEntry entry) throws IOException { move(entry, deadLetterDirectory); }

    public synchronized long count() { return files(spoolDirectory).size(); }
    public synchronized long deadLetterCount() { return files(deadLetterDirectory).size(); }

    private SpoolEntry read(Path path) {
        try {
            SpoolEntry entry = GSON.fromJson(Files.readString(path), SpoolEntry.class);
            if (entry == null || entry.event == null || entry.event.get("eventId") == null) throw new IOException("evento inválido");
            return entry;
        } catch (Exception exception) {
            try { move(path, quarantineDirectory); } catch (IOException ignored) { }
            return null;
        }
    }

    private void move(SpoolEntry entry, Path destination) throws IOException { move(spoolDirectory.resolve(entry.event.get("eventId").getAsString() + ".json"), destination); }
    private void move(Path source, Path destination) throws IOException { Files.createDirectories(destination); Files.move(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING); }

    private static void writeAtomic(Path target, String contents) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static List<Path> files(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        }
        catch (IOException exception) { return List.of(); }
    }

    private static Path child(Path root, String configured) throws IOException {
        if (configured == null || configured.isBlank()) throw new IOException("diretório do spool vazio");
        Path candidate = root.resolve(configured).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) throw new IOException("diretório do spool fora da raiz");
        return candidate;
    }

    private static int priorityValue(String priority) {
        return switch (priority == null ? "NORMAL" : priority) { case "CRITICAL" -> 4; case "HIGH" -> 3; case "NORMAL" -> 2; default -> 1; };
    }
}
