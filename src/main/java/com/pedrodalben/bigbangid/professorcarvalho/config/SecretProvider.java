package com.pedrodalben.bigbangid.professorcarvalho.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SecretProvider {
    private SecretProvider() {}

    public static String resolve(GatewayConfig config, Path root) throws IOException {
        String environment = System.getenv(config.sharedSecretEnvironmentVariable);
        if (environment != null && !environment.isBlank()) return environment.trim();
        if (config.sharedSecretFile != null && !config.sharedSecretFile.isBlank()) {
            Path configured = root.resolve(config.sharedSecretFile).normalize();
            if (configured.startsWith(root) && Files.isRegularFile(configured)) return Files.readString(configured, StandardCharsets.UTF_8).trim();
        }
        Path defaultFile = root.resolve("gateway.secret");
        if (Files.isRegularFile(defaultFile)) return Files.readString(defaultFile, StandardCharsets.UTF_8).trim();
        return null;
    }
}
