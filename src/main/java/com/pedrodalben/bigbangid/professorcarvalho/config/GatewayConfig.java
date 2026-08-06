package com.pedrodalben.bigbangid.professorcarvalho.config;

import java.util.ArrayList;
import java.util.List;

public final class GatewayConfig {
    public int configVersion = 1;
    public boolean enabled = true;
    public String serverId = "bigmoncraft";
    public String apiBaseUrl = "http://127.0.0.1:3080";
    public String sharedSecretEnvironmentVariable = "PROFESSOR_GATEWAY_SHARED_SECRET";
    public String sharedSecretFile = "";
    public boolean requirePrivateAddress = true;
    public int connectTimeoutMillis = 3000;
    public int requestTimeoutMillis = 5000;
    public int heartbeatIntervalSeconds = 30;
    public int profileSyncIntervalSeconds = 300;
    public int profileMinimumIntervalSeconds = 30;
    public int shutdownFlushTimeoutSeconds = 10;
    public int maximumInFlightRequests = 4;
    public Spool spool = new Spool();
    public Profiles profiles = new Profiles();
    public Events events = new Events();

    public static final class Spool {
        public boolean enabled = true;
        public String directory = "spool";
        public String deadLetterDirectory = "dead-letter";
        public String quarantineDirectory = "quarantine";
        public int maximumEvents = 10_000;
        public int maximumEventAgeDays = 7;
        public int retryInitialSeconds = 1;
        public int retryMaximumSeconds = 300;
        public int retryJitterPercent = 20;
    }

    public static final class Profiles {
        public boolean enabled = true;
        public boolean syncOnLink = true;
        public boolean syncOnJoin = true;
        public boolean syncOnLeave = true;
        public boolean syncPeriodically = true;
        public boolean includeEconomy = true;
        public boolean includeGems = true;
        public boolean includeRank = true;
        public boolean includePlaytime = true;
        public boolean includeJobs = true;
        public boolean includeParty = true;
        public boolean includePokedex = true;
    }

    public static final class Events {
        public boolean playerJoin = true;
        public boolean playerLeave = true;
        public boolean profileSnapshot = true;
    }

    public List<String> validate(String secret) {
        List<String> errors = new ArrayList<>();
        if (serverId == null || serverId.isBlank()) errors.add("serverId vazio");
        try {
            var uri = java.net.URI.create(apiBaseUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) errors.add("apiBaseUrl deve usar HTTP ou HTTPS");
            if (uri.getHost() == null) errors.add("apiBaseUrl sem host");
            if (requirePrivateAddress && uri.getHost() != null) {
                var address = java.net.InetAddress.getByName(uri.getHost());
                if (!(address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress())) errors.add("apiBaseUrl não aponta para endereço privado");
            }
        } catch (Exception exception) {
            errors.add("apiBaseUrl inválida");
        }
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) errors.add("segredo ausente ou menor que 32 bytes");
        if (connectTimeoutMillis < 1 || requestTimeoutMillis < 1) errors.add("timeouts inválidos");
        if (heartbeatIntervalSeconds < 1 || profileSyncIntervalSeconds < 1 || profileMinimumIntervalSeconds < 0) errors.add("intervalos inválidos");
        if (maximumInFlightRequests < 1) errors.add("maximumInFlightRequests inválido");
        if (spool == null || spool.maximumEvents < 1 || spool.retryInitialSeconds < 1 || spool.retryMaximumSeconds < spool.retryInitialSeconds) errors.add("spool inválido");
        return errors;
    }
}
