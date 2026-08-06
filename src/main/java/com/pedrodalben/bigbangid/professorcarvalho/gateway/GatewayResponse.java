package com.pedrodalben.bigbangid.professorcarvalho.gateway;

public record GatewayResponse(int statusCode, String body) {
    public String code() {
        try {
            var json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            return json.has("code") ? json.get("code").getAsString() : "";
        } catch (RuntimeException ignored) { return ""; }
    }

    public boolean accepted(String eventId) {
        try {
            var json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            return json.has("accepted") && json.get("accepted").getAsBoolean() && (!json.has("eventId") || eventId.equals(json.get("eventId").getAsString()));
        } catch (RuntimeException ignored) { return false; }
    }
}
