package com.demo.upimesh.repository;

import com.demo.upimesh.model.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceId(String deviceId);
    List<Device> findAllByOrderByDeviceIdAsc();
    List<Device> findByStatusOrderByDeviceIdAsc(Device.Status status);
    List<Device> findByStatusAndInternetCapabilityTrue(Device.Status status);
    List<Device> findByUser_VpaAndStatusAndTrustStatus(String vpa, Device.Status status,
            Device.TrustStatus trustStatus);
    long countByStatus(Device.Status status);
    long countByTrustStatus(Device.TrustStatus trustStatus);
    long countByStatusAndInternetCapabilityTrue(Device.Status status);
    long countByStatusAndInternetCapabilityFalse(Device.Status status);
}
