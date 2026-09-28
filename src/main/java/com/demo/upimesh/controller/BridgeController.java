package com.demo.upimesh.controller;

import com.demo.upimesh.dto.request.BridgeIngestRequest;
import com.demo.upimesh.dto.response.BridgeIngestResponse;
import com.demo.upimesh.service.BridgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class BridgeController {
    private final BridgeService bridge;

    public BridgeController(BridgeService bridge) {
        this.bridge = bridge;
    }

    @PostMapping("/bridge/ingest")
    public BridgeIngestResponse ingest(@Valid @RequestBody BridgeIngestRequest request,
            @NotBlank @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @Min(0) @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {
        return bridge.ingest(request, bridgeNodeId, hopCount);
    }
}
