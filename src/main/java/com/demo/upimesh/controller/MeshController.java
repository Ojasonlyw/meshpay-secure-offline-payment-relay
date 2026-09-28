package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.MeshFlushResponse;
import com.demo.upimesh.dto.response.MeshGossipResponse;
import com.demo.upimesh.dto.response.MeshResetResponse;
import com.demo.upimesh.dto.response.MeshStateResponse;
import com.demo.upimesh.service.MeshService;
import com.demo.upimesh.dto.request.MeshConnectionRequest;
import com.demo.upimesh.dto.response.MeshConnectionResponse;
import com.demo.upimesh.dto.response.PacketRouteResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeshController {
    private final MeshService mesh;

    public MeshController(MeshService mesh) {
        this.mesh = mesh;
    }

    @GetMapping("/mesh/state")
    public MeshStateResponse state() {
        return mesh.state();
    }

    @PostMapping("/mesh/gossip")
    public MeshGossipResponse gossip() {
        return mesh.gossip();
    }

    @PostMapping("/mesh/flush")
    public MeshFlushResponse flush() {
        return mesh.flush();
    }

    @PostMapping("/mesh/reset")
    public MeshResetResponse reset() {
        return mesh.reset();
    }

    @PostMapping("/mesh/connections")
    public MeshConnectionResponse saveConnection(@Valid @RequestBody MeshConnectionRequest request) {
        return mesh.saveConnection(request);
    }

    @GetMapping("/mesh/connections")
    public List<MeshConnectionResponse> connections() { return mesh.connections(); }

    @GetMapping("/mesh/routes")
    public List<PacketRouteResponse> routes(@RequestParam(required = false) String packetId,
            @RequestParam(required = false) UUID paymentId) {
        return mesh.routes(packetId, paymentId);
    }
}
