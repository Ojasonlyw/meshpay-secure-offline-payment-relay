package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.response.MeshStateResponse;
import com.demo.upimesh.service.VirtualDevice;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class MeshMapper {
    public MeshStateResponse toResponse(Collection<VirtualDevice> devices, int cacheSize, int activeConnections) {
        return new MeshStateResponse(devices.stream().map(device -> new MeshStateResponse.Device(
                device.getDeviceId(), device.hasInternet(), device.packetCount(),
                device.getHeldPackets().stream().map(packet -> packet.getPacketId().substring(0, 8)).toList()
        )).toList(), cacheSize, activeConnections);
    }
}
