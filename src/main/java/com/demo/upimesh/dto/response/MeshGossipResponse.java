package com.demo.upimesh.dto.response;

import java.util.Map;

public record MeshGossipResponse(int transfers, Map<String, Integer> deviceCounts, int routesRecorded) {}
