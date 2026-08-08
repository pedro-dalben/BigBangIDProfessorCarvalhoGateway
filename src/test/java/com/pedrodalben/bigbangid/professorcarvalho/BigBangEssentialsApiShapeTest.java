package com.pedrodalben.bigbangid.professorcarvalho;

import com.pedrodalben.bigbangid.professorcarvalho.integration.BigBangEssentialsBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigBangEssentialsApiShapeTest {
    @Test
    void providerIsFinalClassNotInterface() {
        assertFalse(BigBangEssentialsBridge.class.isInterface());
        assertFalse(Optional.class.isInterface());
    }

    @Test
    void providerGetIsStaticClassMethod() throws Exception {
        Class<?> provider = Class.forName("com.pedrodalben.bigbangessentials.api.professorcarvalho.BigBangEssentialsApiProvider");
        assertFalse(provider.isInterface(), "BigBangEssentialsApiProvider must be a class; an interface here produced IncompatibleClassChangeError at runtime");
        Method get = provider.getMethod("get");
        assertTrue(Modifier.isStatic(get.getModifiers()));
        assertTrue(Modifier.isFinal(provider.getModifiers()));
        assertEquals(Optional.class, get.getReturnType());
    }

    @Test
    void bridgeCollectResolves() throws Exception {
        Class<?> provider = Class.forName("com.pedrodalben.bigbangessentials.api.professorcarvalho.BigBangEssentialsApiProvider");
        assertNotNull(provider.getMethod("get").invoke(null));
    }
}
