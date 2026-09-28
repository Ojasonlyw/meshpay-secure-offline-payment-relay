package com.demo.upimesh.controller;

import com.demo.upimesh.dto.request.DeviceRegistrationRequest;
import com.demo.upimesh.dto.request.DeviceStatusUpdateRequest;
import com.demo.upimesh.dto.response.DeviceResponse;
import com.demo.upimesh.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService devices;

    public DeviceController(DeviceService devices) { this.devices = devices; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@Valid @RequestBody DeviceRegistrationRequest request) {
        return devices.register(request);
    }

    @GetMapping
    public List<DeviceResponse> list() { return devices.list(); }

    @GetMapping("/{deviceId}")
    public DeviceResponse find(@PathVariable String deviceId) { return devices.find(deviceId); }

    @PatchMapping("/{deviceId}/status")
    public DeviceResponse update(@PathVariable String deviceId,
            @RequestBody DeviceStatusUpdateRequest request) {
        return devices.update(deviceId, request);
    }
}
