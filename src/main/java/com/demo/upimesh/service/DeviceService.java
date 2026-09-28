package com.demo.upimesh.service;

import com.demo.upimesh.dto.request.DeviceRegistrationRequest;
import com.demo.upimesh.dto.request.DeviceStatusUpdateRequest;
import com.demo.upimesh.dto.response.DeviceResponse;
import com.demo.upimesh.exception.AccountNotFoundException;
import com.demo.upimesh.exception.DeviceConflictException;
import com.demo.upimesh.exception.DeviceNotFoundException;
import com.demo.upimesh.exception.InvalidPacketException;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.DeviceKey;
import com.demo.upimesh.model.User;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.DeviceKeyRepository;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.repository.UserRepository;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final DeviceRepository devices;
    private final DeviceKeyRepository keys;

    public DeviceService(AccountRepository accounts, UserRepository users,
            DeviceRepository devices, DeviceKeyRepository keys) {
        this.accounts = accounts;
        this.users = users;
        this.devices = devices;
        this.keys = keys;
    }

    @Transactional
    public DeviceResponse register(DeviceRegistrationRequest request) {
        if (devices.findByDeviceId(request.deviceId()).isPresent()) throw new DeviceConflictException("Device ID already exists");
        if (!DeviceSignatureService.ALGORITHM.equals(request.algorithm())) throw new InvalidPacketException("Unsupported key algorithm");
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(request.publicKey());
            KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new InvalidPacketException("Invalid public key", e);
        }
        String fingerprint = DeviceSignatureService.sha256Hex(encoded);
        if (keys.existsByKeyFingerprint(fingerprint)) throw new DeviceConflictException("Key is already registered");
        Account account = accounts.findById(request.userVpa()).orElseThrow(AccountNotFoundException::new);
        User user = users.findByVpa(account.getVpa()).orElseGet(() -> {
            User created = new User();
            created.setUserId(account.getVpa());
            created.setVpa(account.getVpa());
            created.setDisplayName(account.getHolderName());
            created.setStatus("ACTIVE");
            return users.save(created);
        });
        Device device = new Device();
        device.setDeviceId(request.deviceId());
        device.setUser(user);
        device.setDeviceName(request.deviceName());
        device.setStatus(Device.Status.ACTIVE);
        device.setTrustStatus(Device.TrustStatus.UNTRUSTED);
        device.setInternetCapability(request.internetCapability());
        devices.saveAndFlush(device);
        DeviceKey key = new DeviceKey();
        key.setDevice(device);
        key.setAlgorithm("Ed25519");
        key.setPublicKey(request.publicKey());
        key.setKeyFingerprint(fingerprint);
        keys.saveAndFlush(key);
        return response(device, key);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        return devices.findAllByOrderByDeviceIdAsc().stream().map(d -> response(d, activeKey(d))).toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse find(String deviceId) {
        Device device = devices.findByDeviceId(deviceId).orElseThrow(DeviceNotFoundException::new);
        return response(device, activeKey(device));
    }

    @Transactional
    public DeviceResponse update(String deviceId, DeviceStatusUpdateRequest request) {
        Device device = devices.findByDeviceId(deviceId).orElseThrow(DeviceNotFoundException::new);
        if (request.status() == null && request.trustStatus() == null)
            throw new InvalidPacketException("Status or trust status required");
        if (device.getStatus() == Device.Status.REVOKED && request.status() != null
                && request.status() != Device.Status.REVOKED)
            throw new DeviceConflictException("Revoked devices cannot be reactivated");
        if (request.status() != null) device.setStatus(request.status());
        if (request.trustStatus() != null) device.setTrustStatus(request.trustStatus());
        return response(devices.saveAndFlush(device), activeKey(device));
    }

    private DeviceKey activeKey(Device device) {
        return keys.findByDevice_IdAndActiveTrue(device.getId()).orElse(null);
    }

    private DeviceResponse response(Device device, DeviceKey key) {
        return new DeviceResponse(device.getDeviceId(), device.getUser().getVpa(),
                device.getDeviceName(), device.getStatus().name(), device.isInternetCapability(),
                device.getTrustStatus().name(), key == null ? null : key.getKeyFingerprint(),
                device.getRegisteredAt(), device.getLastSeenAt());
    }
}
