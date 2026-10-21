package com.demo.upimesh.service;

import com.demo.upimesh.exception.DeviceNotFoundException;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.MeshConnection;
import com.demo.upimesh.model.MeshPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Packet holdings are transient; registered devices and links come from the database. */
@Service
public class MeshSimulatorService {
    private final MeshTopologyService topology;
    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    private final int maxPackets;
    private final long maxAgeSeconds;
    public MeshSimulatorService(MeshTopologyService topology,
            @org.springframework.beans.factory.annotation.Value("${mesh.max-packets-per-device:256}") int maxPackets,
            @org.springframework.beans.factory.annotation.Value("${upi.mesh.packet-max-age-seconds:86400}") long maxAgeSeconds) {
        this.topology = topology;
        this.maxPackets = maxPackets;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    private synchronized void refresh() {
        List<Device> active = topology.activeDevices();
        Set<String> ids = active.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        devices.keySet().removeIf(id -> !ids.contains(id));
        for (Device device : active) {
            devices.compute(device.getDeviceId(), (id, existing) -> {
                if (existing == null) return new VirtualDevice(id, device.isInternetCapability(), maxPackets, maxAgeSeconds, java.time.Clock.systemUTC());
                existing.setInternet(device.isInternetCapability());
                return existing;
            });
        }
    }

    public synchronized Collection<VirtualDevice> getDevices() {
        refresh();
        return devices.values().stream().sorted(java.util.Comparator.comparing(VirtualDevice::getDeviceId)).toList();
    }

    public synchronized VirtualDevice getDevice(String id) {
        refresh();
        return devices.get(id);
    }

    public synchronized void inject(String senderDeviceId, MeshPacket packet) {
        refresh();
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null) throw new DeviceNotFoundException();
        if (!sender.hold(packet)) throw new com.demo.upimesh.exception.InvalidPacketException("Device packet capacity or expiry limit reached");
    }

    /** Every active directed connection carries at most one copy per packet per round. */
    public synchronized GossipResult gossipOnce() {
        refresh();
        Map<String, List<VirtualDevice.HeldPacket>> snapshot = new HashMap<>();
        for (VirtualDevice device : devices.values())
            snapshot.put(device.getDeviceId(), device.snapshot());
        int transfers = 0;
        for (MeshConnection connection : topology.activeConnections()) {
            String sourceId = connection.getSourceDevice().getDeviceId();
            String targetId = connection.getTargetDevice().getDeviceId();
            VirtualDevice source = devices.get(sourceId);
            VirtualDevice target = devices.get(targetId);
            if (source == null || target == null) continue;
            for (VirtualDevice.HeldPacket custody : snapshot.getOrDefault(sourceId, List.of())) {
                MeshPacket packet = custody.packet();
                if (packet.getTtl() <= 0 || target.holds(packet.getPacketId())) continue;
                MeshPacket copy = new MeshPacket();
                copy.setPacketId(packet.getPacketId());
                copy.setTtl(packet.getTtl() - 1);
                copy.setCreatedAt(packet.getCreatedAt());
                copy.setCiphertext(packet.getCiphertext());
                int hops = custody.hops() + 1;
                if (target.hold(copy, hops)) {
                    try {
                        topology.recordTransfer(packet.getPacketId(), sourceId, targetId, hops, copy.getTtl());
                        transfers++;
                    } catch (RuntimeException failure) {
                        target.remove(copy.getPacketId());
                        throw failure;
                    }
                }
            }
        }
        return new GossipResult(transfers, snapshotMap());
    }

    public synchronized Map<String, Integer> snapshotMap() {
        refresh();
        Map<String, Integer> counts = new LinkedHashMap<>();
        getDevices().forEach(device -> counts.put(device.getDeviceId(), device.packetCount()));
        return counts;
    }

    public synchronized List<BridgeUpload> collectBridgeUploads() {
        refresh();
        List<BridgeUpload> uploads = new ArrayList<>();
        for (String id : topology.bridgeIds()) {
            VirtualDevice device = devices.get(id);
            if (device == null) continue;
            for (VirtualDevice.HeldPacket custody : device.snapshot())
                uploads.add(new BridgeUpload(id, custody.packet(), custody.hops()));
        }
        return uploads;
    }

    public synchronized void removeDelivered(String packetId) {
        devices.values().forEach(device -> device.remove(packetId));
    }

    public synchronized void resetMesh() { devices.values().forEach(VirtualDevice::clear); }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet, int hopCount) {}
}
