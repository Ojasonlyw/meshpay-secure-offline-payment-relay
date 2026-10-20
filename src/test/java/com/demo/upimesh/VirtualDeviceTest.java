package com.demo.upimesh;

import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.service.VirtualDevice;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VirtualDeviceTest {
    private MeshPacket packet(int ttl, long createdAt) {
        var packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setCreatedAt(createdAt);
        packet.setTtl(ttl);
        packet.setCiphertext("opaque");
        return packet;
    }

    @Test void custodyIsCappedExpiresAndDoesNotExposeMutableState() {
        long now = System.currentTimeMillis();
        var clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.millis()).thenReturn(now);
        var device = new VirtualDevice("device", false, 2, 10, clock);
        var first = packet(3, now);
        assertTrue(device.hold(first, 7));
        first.setTtl(999);
        assertEquals(3, device.snapshot().get(0).ttl());
        assertTrue(device.hold(packet(1, now)));
        assertFalse(device.hold(packet(2, now)));
        assertEquals(2, device.packetCount());
        org.mockito.Mockito.when(clock.millis()).thenReturn(now + 10001);
        assertEquals(0, device.packetCount());
        assertFalse(device.hold(packet(1, now)));
        assertTrue(device.hold(packet(1, now + 10001)));
        device.remove(device.snapshot().get(0).packetId());
        assertEquals(0, device.packetCount());
    }

    @Test void exhaustedTtlIsDroppedExceptAtAnUploadCapableBridge() {
        long now = System.currentTimeMillis();
        assertFalse(new VirtualDevice("relay", false).hold(packet(0, now)));
        assertTrue(new VirtualDevice("bridge", true).hold(packet(0, now)));
    }

    @Test void holdClearAndSnapshotsKeepPacketAndHopsTogether() throws Exception {
        var device = new VirtualDevice("device", false);
        var packet = packet(3, System.currentTimeMillis());
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    barrier.await(5, TimeUnit.SECONDS);
                    device.hold(packet, 7);
                    for (var held : device.snapshot()) assertEquals(7, held.hops());
                }
                return true;
            });
            var resetter = pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    barrier.await(5, TimeUnit.SECONDS);
                    device.clear();
                    for (var held : device.snapshot()) assertEquals(7, held.hops());
                }
                return true;
            });
            writer.get(10, TimeUnit.SECONDS);
            resetter.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
