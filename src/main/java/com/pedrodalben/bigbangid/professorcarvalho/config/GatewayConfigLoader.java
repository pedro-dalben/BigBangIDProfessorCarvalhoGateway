package com.pedrodalben.bigbangid.professorcarvalho.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class GatewayConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private final Path configPath;

    public GatewayConfigLoader() {
        root = FabricLoader.getInstance().getConfigDir().resolve("bigbangid_professorcarvalhogateway");
        configPath = root.resolve("config.json");
    }

    public LoadedConfig load() throws IOException {
        Files.createDirectories(root);
        GatewayConfig config;
        if (Files.exists(configPath)) config = GSON.fromJson(Files.readString(configPath), GatewayConfig.class);
        else config = new GatewayConfig();
        if (config == null) config = new GatewayConfig();
        write(config);
        String secret = SecretProvider.resolve(config, root);
        return new LoadedConfig(config, secret, config.validate(secret), root);
    }

    public void write(GatewayConfig config) throws IOException {
        Files.createDirectories(root);
        Path temporary = configPath.resolveSibling("config.json.tmp");
        Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
        try { Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record LoadedConfig(GatewayConfig config, String secret, List<String> errors, Path root) {
        public boolean valid() { return config.enabled && errors.isEmpty(); }
    }
}
