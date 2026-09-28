package com.demo.upimesh.repository;

import com.demo.upimesh.model.PacketRoute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface PacketRouteRepository extends JpaRepository<PacketRoute, Long> {
    Optional<PacketRoute> findByPacketIdAndDestinationDevice_Id(String packetId, Long deviceId);
    List<PacketRoute> findTop100ByOrderByReceivedAtDescIdDesc();
    List<PacketRoute> findTop100ByPacketIdOrderByReceivedAtDescIdDesc(String packetId);
    List<PacketRoute> findTop100ByPayment_PaymentIdOrderByReceivedAtDescIdDesc(java.util.UUID paymentId);
    List<PacketRoute> findByPacketId(String packetId);
    @Query("select coalesce(avg(r.hopNumber), 0) from PacketRoute r")
    Double averageHopCount();
    @Query("select max(r.receivedAt) from PacketRoute r")
    Instant latestReceivedAt();
    @Query(value = "select d.device_id from packet_routes r join devices d on d.id=r.destination_device_id where d.internet_capability=true group by d.device_id order by count(*) desc, d.device_id limit 1", nativeQuery = true)
    List<String> mostActiveBridges();
}
