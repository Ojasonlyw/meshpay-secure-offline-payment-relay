package com.demo.upimesh.repository;

import com.demo.upimesh.model.DeviceKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select k from DeviceKey k where k.device.id = :deviceId and k.active = true")
    Optional<DeviceKey> lockActiveKey(@org.springframework.data.repository.query.Param("deviceId") Long deviceId);
    Optional<DeviceKey> findByDevice_IdAndActiveTrue(Long deviceId);
    java.util.List<DeviceKey> findByDevice_IdInAndActiveTrue(java.util.Collection<Long> ids);
    boolean existsByKeyFingerprint(String keyFingerprint);
}
