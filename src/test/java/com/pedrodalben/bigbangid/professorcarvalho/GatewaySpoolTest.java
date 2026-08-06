package com.pedrodalben.bigbangid.professorcarvalho;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangid.professorcarvalho.config.GatewayConfig;
import com.pedrodalben.bigbangid.professorcarvalho.gateway.GatewayEvent;
import com.pedrodalben.bigbangid.professorcarvalho.spool.FileEventSpool;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewaySpoolTest {
    @Test
    void persistsEventsAndIgnoresSymlinkedFiles() throws Exception {
        Path root = Files.createTempDirectory("professor-gateway-spool-");
        GatewayConfig.Spool config = new GatewayConfig.Spool();
        config.maximumEvents = 2;
        FileEventSpool spool = new FileEventSpool(root, config);

        JsonObject payload = new JsonObject();
        payload.addProperty("minecraftName", "José");
        spool.enqueue(GatewayEvent.create("player.session.started", "bigmoncraft", payload), "HIGH");
        assertEquals(1, spool.ready().size());
        assertEquals(1, new FileEventSpool(root, config).count());

        Path outside = Files.createTempFile("outside-", ".json");
        Path link = root.resolve(config.directory).resolve("linked.json");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException ignored) {
            return;
        }
        assertEquals(1, spool.count());
        assertTrue(spool.ready().stream().allMatch(entry -> !entry.event.toString().contains("outside")));
    }
}
