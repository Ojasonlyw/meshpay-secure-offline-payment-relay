package com.demo.upimesh.service;

import com.demo.upimesh.dto.request.BridgeIngestRequest;
import com.demo.upimesh.dto.response.BridgeIngestResponse;
import com.demo.upimesh.mapper.BridgeMapper;
import org.springframework.stereotype.Service;

@Service
public class BridgeService {
    private final BridgeIngestionService ingestion;
    private final BridgeMapper mapper;

    public BridgeService(BridgeIngestionService ingestion, BridgeMapper mapper) {
        this.ingestion = ingestion;
        this.mapper = mapper;
    }

    public BridgeIngestResponse ingest(BridgeIngestRequest request, String bridgeNodeId, int hopCount) {
        // Envelope limits and settlement rules stay in BridgeIngestionService.
        return mapper.toResponse(ingestion.ingest(mapper.toPacket(request), bridgeNodeId, hopCount));
    }
}
