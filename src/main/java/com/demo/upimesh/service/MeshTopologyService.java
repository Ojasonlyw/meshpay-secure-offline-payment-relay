package com.demo.upimesh.service;

import com.demo.upimesh.dto.request.MeshConnectionRequest;
import com.demo.upimesh.dto.response.MeshConnectionResponse;
import com.demo.upimesh.dto.response.PacketRouteResponse;
import com.demo.upimesh.exception.DeviceNotFoundException;
import com.demo.upimesh.exception.InvalidPacketException;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.MeshConnection;
import com.demo.upimesh.model.PacketRoute;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.repository.MeshConnectionRepository;
import com.demo.upimesh.repository.PacketRouteRepository;
import com.demo.upimesh.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeshTopologyService {
    private final DeviceRepository devices;
    private final MeshConnectionRepository connections;
    private final PacketRouteRepository routes;
    private final PaymentRepository payments;

    public MeshTopologyService(DeviceRepository devices, MeshConnectionRepository connections,
            PacketRouteRepository routes, PaymentRepository payments) {
        this.devices = devices;
        this.connections = connections;
        this.routes = routes;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<Device> activeDevices() {
        return devices.findByStatusOrderByDeviceIdAsc(Device.Status.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<MeshConnection> activeConnections() {
        return connections.findByStatus(MeshConnection.Status.ACTIVE).stream()
                .filter(c -> c.getSourceDevice().getStatus() == Device.Status.ACTIVE
                        && c.getTargetDevice().getStatus() == Device.Status.ACTIVE).toList();
    }

    @Transactional(readOnly = true)
    public List<String> bridgeIds() {
        return devices.findByStatusAndInternetCapabilityTrue(Device.Status.ACTIVE).stream()
                .map(Device::getDeviceId).toList();
    }

    @Transactional
    public MeshConnectionResponse saveConnection(MeshConnectionRequest request) {
        if (request.sourceDeviceId().equals(request.targetDeviceId()))
            throw new InvalidPacketException("Connection endpoints must differ");
        Device source = devices.findByDeviceId(request.sourceDeviceId()).orElseThrow(DeviceNotFoundException::new);
        Device target = devices.findByDeviceId(request.targetDeviceId()).orElseThrow(DeviceNotFoundException::new);
        MeshConnection connection = connections.findBySourceDevice_IdAndTargetDevice_Id(source.getId(), target.getId())
                .orElseGet(MeshConnection::new);
        connection.setSourceDevice(source);
        connection.setTargetDevice(target);
        connection.setStatus(request.status() == null ? MeshConnection.Status.ACTIVE : request.status());
        connection.setLinkType(request.linkType() == null || request.linkType().isBlank()
                ? "BLUETOOTH" : request.linkType());
        connection.setLastSeenAt(Instant.now());
        return response(connections.saveAndFlush(connection));
    }

    @Transactional(readOnly = true)
    public List<MeshConnectionResponse> listConnections() {
        return connections.findAllByOrderByIdAsc().stream().map(this::response).toList();
    }

    @Transactional
    public void recordTransfer(String packetId, String sourceId, String destinationId,
            int hopNumber, int ttlAfterHop) {
        Device source = devices.findByDeviceId(sourceId).orElseThrow(DeviceNotFoundException::new);
        Device destination = devices.findByDeviceId(destinationId).orElseThrow(DeviceNotFoundException::new);
        if (routes.findByPacketIdAndDestinationDevice_Id(packetId, destination.getId()).isPresent()) return;
        PacketRoute route = new PacketRoute();
        route.setPacketId(packetId);
        route.setSourceDevice(source);
        route.setDestinationDevice(destination);
        route.setHopNumber(hopNumber);
        route.setTtlAfterHop(ttlAfterHop);
        route.setForwardedAt(Instant.now());
        routes.saveAndFlush(route);
    }

    @Transactional
    public void linkPayment(String packetId, Long paymentId) {
        var payment = payments.findById(paymentId).orElseThrow();
        for (PacketRoute route : routes.findByPacketId(packetId)) {
            if (route.getPayment() == null) route.setPayment(payment);
        }
    }

    @Transactional(readOnly = true)
    public List<PacketRouteResponse> listRoutes(String packetId, UUID paymentId) {
        if (packetId != null && paymentId != null)
            throw new InvalidPacketException("Choose packetId or paymentId");
        List<PacketRoute> result = packetId != null
                ? routes.findTop100ByPacketIdOrderByReceivedAtDescIdDesc(packetId)
                : paymentId != null
                ? routes.findTop100ByPayment_PaymentIdOrderByReceivedAtDescIdDesc(paymentId)
                : routes.findTop100ByOrderByReceivedAtDescIdDesc();
        return result.stream().map(route -> new PacketRouteResponse(route.getPacketId(),
                route.getPayment() == null ? null : route.getPayment().getPaymentId(),
                route.getSourceDevice().getDeviceId(), route.getDestinationDevice().getDeviceId(),
                route.getHopNumber(), route.getReceivedAt(), route.getForwardedAt(),
                route.getTtlAfterHop())).toList();
    }

    private MeshConnectionResponse response(MeshConnection connection) {
        return new MeshConnectionResponse(connection.getId(),
                connection.getSourceDevice().getDeviceId(), connection.getTargetDevice().getDeviceId(),
                connection.getStatus().name(), connection.getLinkType(),
                connection.getCreatedAt(), connection.getLastSeenAt());
    }
}
