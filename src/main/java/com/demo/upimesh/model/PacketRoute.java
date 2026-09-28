package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "packet_routes")
public class PacketRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "packet_id", nullable = false, length = 36)
    private String packetId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_device_id", nullable = false)
    private Device sourceDevice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_device_id", nullable = false)
    private Device destinationDevice;
    @Column(name = "hop_number", nullable = false)
    private int hopNumber;
    @Column(name = "received_at", nullable = false, columnDefinition = "timestamptz")
    private Instant receivedAt = Instant.now();
    @Column(name = "forwarded_at", columnDefinition = "timestamptz")
    private Instant forwardedAt;
    @Column(name = "ttl_after_hop", nullable = false)
    private int ttlAfterHop;

    public Long getId() { return id; }
    public String getPacketId() { return packetId; }
    public void setPacketId(String value) { packetId = value; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment value) { payment = value; }
    public Device getSourceDevice() { return sourceDevice; }
    public void setSourceDevice(Device value) { sourceDevice = value; }
    public Device getDestinationDevice() { return destinationDevice; }
    public void setDestinationDevice(Device value) { destinationDevice = value; }
    public int getHopNumber() { return hopNumber; }
    public void setHopNumber(int value) { hopNumber = value; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getForwardedAt() { return forwardedAt; }
    public void setForwardedAt(Instant value) { forwardedAt = value; }
    public int getTtlAfterHop() { return ttlAfterHop; }
    public void setTtlAfterHop(int value) { ttlAfterHop = value; }
}
