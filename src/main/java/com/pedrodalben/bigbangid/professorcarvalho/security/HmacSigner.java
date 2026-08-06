package com.pedrodalben.bigbangid.professorcarvalho.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSigner {
    private HmacSigner() {}

    public static String bodyHash(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 indisponível", exception); }
    }

    public static String canonical(String method, String path, String serverId, String timestamp, String requestId, String version, String bodyHash) {
        return String.join("\n", method.toUpperCase(java.util.Locale.ROOT), path, serverId, timestamp, requestId, version, bodyHash);
    }

    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("HMAC-SHA256 indisponível", exception); }
    }
}
