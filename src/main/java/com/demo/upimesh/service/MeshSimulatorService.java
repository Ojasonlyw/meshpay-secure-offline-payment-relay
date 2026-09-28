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

    public MeshSimulatorService(MeshTopologyService topology) { this.topology = topology; }

    private synchronized void refresh() {
        List<Device> active = topology.activeDevices();
        Set<String> ids = active.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        devices.keySet().removeIf(id -> !ids.contains(id));
        for (Device device : active) {
            devices.compute(device.getDeviceId(), (id, existing) -> {
                if (existing == null) return new VirtualDevice(id, device.isInternetCapability());
                existing.setInternet(device.isInternetCapability());
                return existing;
            });
        }
    }

    public Collection<VirtualDevice> getDevices() {
        refresh();
        return devices.values().stream().sorted(java.util.Comparator.comparing(VirtualDevice::getDeviceId)).toList();
    }

    public VirtualDevice getDevice(String id) {
        refresh();
        return devices.get(id);
    }

    public void inject(String senderDeviceId, MeshPacket packet) {
        refresh();
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null) throw new DeviceNotFoundException();
        sender.hold(packet);
    }

    /** Every active directed connection carries at most one copy per packet per round. */
    public synchronized GossipResult gossipOnce() {
        refresh();
        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for (VirtualDevice device : devices.values())
            snapshot.put(device.getDeviceId(), new ArrayList<>(device.getHeldPackets()));
        int transfers = 0;
        for (MeshConnection connection : topology.activeConnections()) {
            String sourceId = connection.getSourceDevice().getDeviceId();
            String targetId = connection.getTargetDevice().getDeviceId();
            VirtualDevice source = devices.get(sourceId);
            VirtualDevice target = devices.get(targetId);
            if (source == null || target == null) continue;
            for (MeshPacket packet : snapshot.getOrDefault(sourceId, List.of())) {
                if (packet.getTtl() <= 0 || target.holds(packet.getPacketId())) continue;
                MeshPacket copy = new MeshPacket();
                copy.setPacketId(packet.getPacketId());
                copy.setTtl(packet.getTtl() - 1);
                copy.setCreatedAt(packet.getCreatedAt());
                copy.setCiphertext(packet.getCiphertext());
                int hops = source.hopCount(packet.getPacketId()) + 1;
                topology.recordTransfer(packet.getPacketId(), sourceId, targetId, hops, copy.getTtl());
                target.hold(copy, hops);
                transfers++;
            }
        }
        return new GossipResult(transfers, snapshotMap());
    }

    public Map<String, Integer> snapshotMap() {
        refresh();
        Map<String, Integer> counts = new LinkedHashMap<>();
        getDevices().forEach(device -> counts.put(device.getDeviceId(), device.packetCount()));
        return counts;
    }

    public List<BridgeUpload> collectBridgeUploads() {
        refresh();
        List<BridgeUpload> uploads = new ArrayList<>();
        for (String id : topology.bridgeIds()) {
            VirtualDevice device = devices.get(id);
            if (device == null) continue;
            for (MeshPacket packet : device.getHeldPackets())
                uploads.add(new BridgeUpload(id, packet, device.hopCount(packet.getPacketId())));
        }
        return uploads;
    }

    public void resetMesh() { devices.values().forEach(VirtualDevice::clear); }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet, int hopCount) {}
}
