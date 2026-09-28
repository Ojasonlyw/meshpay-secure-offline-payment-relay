package com.demo.upimesh.service;

import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dto.request.DemoSendRequest;
import com.demo.upimesh.dto.request.PaymentRequest;
import com.demo.upimesh.dto.response.PaymentResponse;
import com.demo.upimesh.dto.response.ServerKeyResponse;
import com.demo.upimesh.mapper.PaymentMapper;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.exception.InvalidSignatureException;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final DemoService demo;
    private final MeshSimulatorService mesh;
    private final ServerKeyHolder serverKey;
    private final PaymentMapper mapper;
    private final DeviceRepository devices;

    public PaymentService(DemoService demo, MeshSimulatorService mesh, ServerKeyHolder serverKey,
                          PaymentMapper mapper, DeviceRepository devices) {
        this.demo = demo;
        this.mesh = mesh;
        this.serverKey = serverKey;
        this.mapper = mapper;
        this.devices = devices;
    }

    public PaymentResponse sendDemo(DemoSendRequest request) throws Exception {
        PaymentRequest payment = mapper.toPaymentRequest(request);
        String deviceId = payment.startDevice() == null
                ? devices.findByUser_VpaAndStatusAndTrustStatus(payment.senderVpa(),
                    Device.Status.ACTIVE, Device.TrustStatus.TRUSTED).stream()
                    .map(Device::getDeviceId).sorted().findFirst()
                    .orElseThrow(() -> new InvalidSignatureException("No trusted sender device"))
                : payment.startDevice();
        MeshPacket packet = demo.createPacket(payment.senderVpa(), payment.receiverVpa(),
                payment.amount(), payment.pin(), payment.ttl(), deviceId);
        mesh.inject(deviceId, packet);
        return mapper.toResponse(packet, deviceId);
    }

    public ServerKeyResponse publicKey() {
        return new ServerKeyResponse(serverKey.getPublicKeyBase64(), "RSA-2048 / OAEP-SHA256",
                "RSA-OAEP encrypts an AES-256-GCM session key");
    }
}
