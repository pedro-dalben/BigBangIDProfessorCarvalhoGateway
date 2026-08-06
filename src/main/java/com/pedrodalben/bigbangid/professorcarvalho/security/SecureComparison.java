package com.pedrodalben.bigbangid.professorcarvalho.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecureComparison {
    private SecureComparison() {}
    public static boolean equalsHex(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }
}
