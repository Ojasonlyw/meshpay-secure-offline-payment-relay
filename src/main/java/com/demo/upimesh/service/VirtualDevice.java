package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simulated phone in the mesh. Holds packets it has seen.
 *
 * In the real system, this state would be on a physical Android device,
 * with packets exchanged via BLE GATT characteristics.
 */
public class VirtualDevice {

    private final String deviceId;
    private volatile boolean hasInternet;
    private final Map<String, MeshPacket> heldPackets = new ConcurrentHashMap<>();
    private final Map<String, Integer> hopCounts = new ConcurrentHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId() { return deviceId; }
    public boolean hasInternet() { return hasInternet; }
    public void setInternet(boolean value) { hasInternet = value; }

    public void hold(MeshPacket packet) {
        hold(packet, 0);
    }

    public void hold(MeshPacket packet, int hops) {
        if (heldPackets.putIfAbsent(packet.getPacketId(), packet) == null)
            hopCounts.put(packet.getPacketId(), hops);
    }

    public Collection<MeshPacket> getHeldPackets() {
        return heldPackets.values();
    }

    public boolean holds(String packetId) {
        return heldPackets.containsKey(packetId);
    }

    public int packetCount() {
        return heldPackets.size();
    }

    public int hopCount(String packetId) { return hopCounts.getOrDefault(packetId, 0); }

    public void clear() {
        heldPackets.clear();
        hopCounts.clear();
    }
}
