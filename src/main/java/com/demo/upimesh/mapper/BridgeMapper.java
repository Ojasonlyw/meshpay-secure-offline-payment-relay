package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.request.BridgeIngestRequest;
import com.demo.upimesh.dto.response.BridgeIngestResponse;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.service.BridgeIngestionService;
import org.springframework.stereotype.Component;

@Component
public class BridgeMapper {
    public MeshPacket toPacket(BridgeIngestRequest request) {
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(request.packetId());
        packet.setTtl(request.ttl());
        packet.setCreatedAt(request.createdAt());
        packet.setCiphertext(request.ciphertext());
        return packet;
    }

    public BridgeIngestResponse toResponse(BridgeIngestionService.IngestResult result) {
        return new BridgeIngestResponse(result.outcome(), result.packetHash(),
                result.reason(), result.transactionId());
    }
}
