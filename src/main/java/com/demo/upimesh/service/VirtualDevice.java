package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, transient packet custody. Every access shares the same monitor. */
public class VirtualDevice {
    public record HeldPacket(String packetId, int ttl, long createdAt, String ciphertext, int hops, long expiresAt) {
        public MeshPacket packet() {
            MeshPacket packet = new MeshPacket();
            packet.setPacketId(packetId);
            packet.setTtl(ttl);
            packet.setCreatedAt(createdAt);
            packet.setCiphertext(ciphertext);
            return packet;
        }
    }
    private final String deviceId;
    private boolean hasInternet;
    private final int maxPackets;
    private final long maxAgeMillis;
    private final Clock clock;
    private final Map<String, HeldPacket> held = new LinkedHashMap<>();

    public VirtualDevice(String id, boolean internet) { this(id, internet, 256, 86400, Clock.systemUTC()); }
    public VirtualDevice(String id, boolean internet, int maxPackets, long maxAgeSeconds, Clock clock) {
        if (maxPackets < 1 || maxAgeSeconds < 1) throw new IllegalArgumentException("Positive custody bounds required");
        this.deviceId = id;
        this.hasInternet = internet;
        this.maxPackets = maxPackets;
        this.maxAgeMillis = Math.multiplyExact(maxAgeSeconds, 1000);
        this.clock = clock;
    }
    public String getDeviceId() { return deviceId; }
    public synchronized boolean hasInternet() { return hasInternet; }
    public synchronized void setInternet(boolean value) { hasInternet = value; }
    public synchronized boolean hold(MeshPacket packet) { return hold(packet, 0); }
    public synchronized boolean hold(MeshPacket packet, int hops) {
        evictExpired();
        long now = clock.millis();
        if (packet.getCreatedAt() == null || packet.getCreatedAt() <= now - maxAgeMillis
                || packet.getTtl() < 0 || (packet.getTtl() == 0 && !hasInternet)
                || held.containsKey(packet.getPacketId()) || held.size() >= maxPackets) return false;
        held.put(packet.getPacketId(), new HeldPacket(packet.getPacketId(), packet.getTtl(), packet.getCreatedAt(),
                packet.getCiphertext(), hops, Math.min(now, packet.getCreatedAt()) + maxAgeMillis));
        return true;
    }
    private void evictExpired() {
        long now = clock.millis();
        held.values().removeIf(packet -> packet.expiresAt() <= now || (packet.ttl() == 0 && !hasInternet));
    }
    public synchronized List<HeldPacket> snapshot() { evictExpired(); return List.copyOf(held.values()); }
    public synchronized Collection<MeshPacket> getHeldPackets() { return snapshot().stream().map(HeldPacket::packet).toList(); }
    public synchronized boolean holds(String id) { evictExpired(); return held.containsKey(id); }
    public synchronized int packetCount() { evictExpired(); return held.size(); }
    public synchronized int hopCount(String id) { evictExpired(); return held.containsKey(id) ? held.get(id).hops() : 0; }
    public synchronized void remove(String id) { held.remove(id); }
    public synchronized void clear() { held.clear(); }
}
