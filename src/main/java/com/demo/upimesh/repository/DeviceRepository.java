package com.demo.upimesh.repository;

import com.demo.upimesh.model.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Device d where d.deviceId = :deviceId")
    Optional<Device> lockByDeviceId(@org.springframework.data.repository.query.Param("deviceId") String deviceId);
    Optional<Device> findByDeviceId(String deviceId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    List<Device> findTop100ByOrderByDeviceIdAsc();
    List<Device> findTop100ByStatusOrderByDeviceIdAsc(Device.Status status);
    List<Device> findByStatusAndInternetCapabilityTrue(Device.Status status);
    List<Device> findByUser_VpaAndStatusAndTrustStatus(String vpa, Device.Status status,
            Device.TrustStatus trustStatus);
    long countByStatus(Device.Status status);
    long countByTrustStatus(Device.TrustStatus trustStatus);
    long countByStatusAndInternetCapabilityTrue(Device.Status status);
    long countByStatusAndInternetCapabilityFalse(Device.Status status);
}
