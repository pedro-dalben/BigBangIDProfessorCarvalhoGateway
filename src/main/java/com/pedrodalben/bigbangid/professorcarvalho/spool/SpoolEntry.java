package com.pedrodalben.bigbangid.professorcarvalho.spool;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;

import java.time.Instant;

public final class SpoolEntry {
    public int spoolVersion = 1;
    public JsonObject event;
    public String bodySha256;
    public String priority = "NORMAL";
    public String createdAt;
    public int attemptCount;
    public String nextAttemptAt;
    public String lastAttemptAt;
    public String lastErrorCode;

    public GatewayEvent eventValue() { return GatewayEvent.parse(event.toString()); }
    public Instant nextAttempt() { return nextAttemptAt == null ? Instant.EPOCH : Instant.parse(nextAttemptAt); }
}
