package com.pedrodalben.bigbangid.professorcarvalho.spool;

import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayClient;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayResponse;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.concurrent.ScheduledExecutorService;

public final class SpoolProcessor {
    private final FileEventSpool spool;
    private volatile GatewayClient client;
    private final RetryPolicy retryPolicy;
    private final ScheduledExecutorService executor;
    private final java.util.concurrent.Semaphore inFlight;
    private final BiConsumer<SpoolEntry, GatewayResponse> permanentResponseHandler;

    public SpoolProcessor(FileEventSpool spool, GatewayClient client, RetryPolicy retryPolicy, ScheduledExecutorService executor, int maximumInFlightRequests) {
        this(spool, client, retryPolicy, executor, maximumInFlightRequests, (entry, response) -> { });
    }

    public SpoolProcessor(FileEventSpool spool, GatewayClient client, RetryPolicy retryPolicy, ScheduledExecutorService executor, int maximumInFlightRequests, BiConsumer<SpoolEntry, GatewayResponse> permanentResponseHandler) {
        this.spool = spool; this.client = client; this.retryPolicy = retryPolicy; this.executor = executor; this.inFlight = new java.util.concurrent.Semaphore(Math.max(1, maximumInFlightRequests)); this.permanentResponseHandler = permanentResponseHandler;
    }

    public void start() { executor.scheduleWithFixedDelay(this::process, 0, 1, java.util.concurrent.TimeUnit.SECONDS); }
    public void updateClient(GatewayClient client) { this.client = client; }

    public void process() {
        if (client == null) return;
        for (SpoolEntry entry : spool.ready()) {
            if (!inFlight.tryAcquire()) return;
            try {
                client.sendEvent(entry.eventValue()).whenComplete((response, error) -> {
                    try {
                        if (error == null && response.statusCode() >= 200 && response.statusCode() < 300 && response.accepted(entry.event.get("eventId").getAsString())) spool.remove(entry);
                        else if (error == null && response.statusCode() >= 200 && response.statusCode() < 300 && "IDENTITY_NOT_LINKED".equals(response.code())) { spool.remove(entry); permanentResponseHandler.accept(entry, response); }
                        else if (error == null && response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 408 && response.statusCode() != 425 && response.statusCode() != 429) { entry.lastErrorCode = "HTTP_" + response.statusCode(); spool.deadLetter(entry); }
                        else retry(entry, error == null ? "HTTP_" + response.statusCode() : "NETWORK_ERROR");
                    } catch (Exception ignored) { }
                    finally { inFlight.release(); }
                });
            } catch (Exception exception) { try { retry(entry, "SPOOL_SEND_ERROR"); } catch (Exception ignored) { } finally { inFlight.release(); } }
        }
    }

    private void retry(SpoolEntry entry, String errorCode) throws java.io.IOException {
        entry.attemptCount++;
        entry.lastAttemptAt = Instant.now().toString();
        entry.lastErrorCode = errorCode;
        entry.nextAttemptAt = Instant.now().plus(retryPolicy.nextDelay(entry.attemptCount)).toString();
        spool.save(entry);
    }
}
