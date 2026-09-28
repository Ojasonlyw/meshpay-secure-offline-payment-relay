package com.demo.upimesh.repository;

import com.demo.upimesh.model.DeviceKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, Long> {
    Optional<DeviceKey> findByDevice_IdAndActiveTrue(Long deviceId);
    boolean existsByKeyFingerprint(String keyFingerprint);
}
