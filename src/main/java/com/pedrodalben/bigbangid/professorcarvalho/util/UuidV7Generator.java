package com.pedrodalben.bigbangid.professorcarvalho.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private UuidV7Generator() {}

    public static UUID next() {
        long timestamp = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long most = (timestamp << 16) | 0x7000 | (RANDOM.nextLong() & 0x0FFFL);
        long least = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(most, least);
    }
}
