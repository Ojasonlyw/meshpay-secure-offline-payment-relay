package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.exception.InvalidSignatureException;
import com.demo.upimesh.exception.InvalidPacketException;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.DeviceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Simulates a sender device creating a signed, encrypted mesh packet. */
@Service
public class DemoService {
    private final HybridCryptoService crypto;
    private final ServerKeyHolder serverKey;
    private final DemoDeviceKeyService demoKeys;
    private final DeviceRepository devices;

    public DemoService(HybridCryptoService crypto, ServerKeyHolder serverKey,
            DemoDeviceKeyService demoKeys, DeviceRepository devices) {
        this.crypto = crypto;
        this.serverKey = serverKey;
        this.demoKeys = demoKeys;
        this.devices = devices;
    }

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
            BigDecimal amount, String pin, int ttl) throws Exception {
        String deviceId = devices.findByUser_VpaAndStatusAndTrustStatus(senderVpa,
                Device.Status.ACTIVE, Device.TrustStatus.TRUSTED).stream()
                .map(Device::getDeviceId).sorted().findFirst()
                .orElseThrow(() -> new InvalidSignatureException("No trusted sender device"));
        return createPacket(senderVpa, receiverVpa, amount, pin, ttl, deviceId);
    }

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
            BigDecimal amount, String pin, int ttl, String deviceId) throws Exception {
        try {
            if (amount == null || amount.signum() <= 0
                    || amount.setScale(2, RoundingMode.UNNECESSARY).precision() > 19)
                throw new InvalidPacketException("Invalid amount");
        } catch (ArithmeticException e) {
            throw new InvalidPacketException("Amount must have at most two decimal places", e);
        }
        PaymentInstruction instruction = new PaymentInstruction(UUID.randomUUID().toString(),
                senderVpa, receiverVpa, amount, UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(), deviceId);
        // PIN remains an optional demo input. It is never sent, stored, or used to authorize.
        demoKeys.sign(instruction);
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey()));
        return packet;
    }
}
