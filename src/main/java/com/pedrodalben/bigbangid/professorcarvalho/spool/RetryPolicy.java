package com.pedrodalben.bigbangid.professorcarvalho.spool;

import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfig;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {
    private final GatewayConfig.Spool config;
    public RetryPolicy(GatewayConfig.Spool config) { this.config = config; }
    public Duration nextDelay(int attempt) {
        long base = Math.min(config.retryMaximumSeconds, config.retryInitialSeconds * (1L << Math.min(attempt, 30)));
        long jitter = Math.round(base * (config.retryJitterPercent / 100.0));
        return Duration.ofSeconds(Math.max(1, base + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1)));
    }
}
