package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.MeshFlushResponse;
import com.demo.upimesh.dto.response.MeshGossipResponse;
import com.demo.upimesh.dto.response.MeshResetResponse;
import com.demo.upimesh.dto.response.MeshStateResponse;
import com.demo.upimesh.exception.*;
import com.demo.upimesh.mapper.MeshMapper;
import com.demo.upimesh.dto.request.MeshConnectionRequest;
import com.demo.upimesh.dto.response.MeshConnectionResponse;
import com.demo.upimesh.dto.response.PacketRouteResponse;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class MeshService {
    private final MeshSimulatorService mesh;
    private final BridgeIngestionService bridge;
    private final IdempotencyService idempotency;
    private final MeshMapper mapper;
    private final MeshTopologyService topology;

    public MeshService(MeshSimulatorService mesh, BridgeIngestionService bridge,
                       IdempotencyService idempotency, MeshMapper mapper, MeshTopologyService topology) {
        this.mesh = mesh;
        this.bridge = bridge;
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.topology = topology;
    }

    public MeshStateResponse state() {
        return mapper.toResponse(mesh.getDevices(), idempotency.size(), topology.activeConnections().size());
    }

    public MeshGossipResponse gossip() {
        MeshSimulatorService.GossipResult result = mesh.gossipOnce();
        return new MeshGossipResponse(result.transfers(), result.deviceCounts(), result.transfers());
    }

    public MeshFlushResponse flush() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();
        List<MeshFlushResponse.Result> results = new ArrayList<>();
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        uploads.parallelStream().forEach(upload -> {
            String previous = MDC.get(TraceIdFilter.TRACE_ID);
            if (traceId != null) MDC.put(TraceIdFilter.TRACE_ID, traceId);
            try {
                BridgeIngestionService.IngestResult result = ingestUpload(upload);
                synchronized (results) {
                    results.add(new MeshFlushResponse.Result(upload.bridgeNodeId(),
                            upload.packet().getPacketId().substring(0, 8), result.outcome(),
                            result.reason() == null ? "" : result.reason(),
                            result.transactionId() == null ? -1 : result.transactionId(),
                            upload.hopCount()));
                }
            } finally {
                if (previous == null) MDC.remove(TraceIdFilter.TRACE_ID);
                else MDC.put(TraceIdFilter.TRACE_ID, previous);
            }
        });
        return new MeshFlushResponse(uploads.size(), results);
    }

    private BridgeIngestionService.IngestResult ingestUpload(MeshSimulatorService.BridgeUpload upload) {
        try {
            return bridge.ingest(upload.packet(), upload.bridgeNodeId(), upload.hopCount());
        } catch (DomainException e) {
            String outcome = e instanceof DuplicatePaymentException ? "DUPLICATE"
                    : e instanceof ExpiredPacketException ? "EXPIRED"
                    : e instanceof InsufficientBalanceException ? "REJECTED"
                    : e instanceof InvalidPacketException || e instanceof InvalidSignatureException ? "INVALID"
                    : "FAILED";
            return new BridgeIngestionService.IngestResult(outcome, null, e.getErrorCode(), null);
        }
    }

    public MeshResetResponse reset() {
        mesh.resetMesh();
        idempotency.clear();
        return new MeshResetResponse("mesh and idempotency cache cleared");
    }

    public MeshConnectionResponse saveConnection(MeshConnectionRequest request) {
        return topology.saveConnection(request);
    }

    public List<MeshConnectionResponse> connections() { return topology.listConnections(); }

    public List<PacketRouteResponse> routes(String packetId, UUID paymentId) {
        return topology.listRoutes(packetId, paymentId);
    }
}
