package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.request.DemoSendRequest;
import com.demo.upimesh.dto.request.PaymentRequest;
import com.demo.upimesh.dto.response.PaymentResponse;
import com.demo.upimesh.model.MeshPacket;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public PaymentRequest toPaymentRequest(DemoSendRequest request) {
        return new PaymentRequest(request.senderVpa(), request.receiverVpa(), request.amount(),
                request.pin(), request.ttl() == null ? 5 : request.ttl(),
                request.startDevice());
    }

    public PaymentResponse toResponse(MeshPacket packet, String deviceId) {
        return new PaymentResponse(packet.getPacketId(),
                packet.getCiphertext().substring(0, 64) + "...", packet.getTtl(), deviceId);
    }
}
