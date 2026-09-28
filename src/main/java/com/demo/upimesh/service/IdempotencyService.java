package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** Distributed packet state tracking retained in Redis for the configured TTL. */
@Service
public class IdempotencyService {
    private static final String KEY_PREFIX = "upi:mesh:idempotency:";
    private static final ScanOptions SCAN_OPTIONS = ScanOptions.scanOptions()
            .match(KEY_PREFIX + "*").count(1000).build();

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public IdempotencyService(StringRedisTemplate redis,
            @Value("${upi.mesh.idempotency-ttl-seconds:86400}") long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Idempotency TTL must be positive");
        }
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public enum PacketState {
        RECEIVED,
        PROCESSING,
        SETTLED,
        FAILED_RETRYABLE,
        REJECTED,
        EXPIRED
    }

    public record ClaimResult(boolean claimed, PacketState state) {}

    /** Returns true for the first claim. Kept for lower-level idempotency tests. */
    public boolean claim(String packetHash) {
        return claimProcessing(packetHash).claimed();
    }

    public ClaimResult claimProcessing(String packetHash) {
        String key = key(packetHash);
        Boolean claimed = redis.opsForValue().setIfAbsent(key, PacketState.PROCESSING.name(), ttl);
        if (Boolean.TRUE.equals(claimed)) {
            return new ClaimResult(true, PacketState.PROCESSING);
        }

        PacketState current = getState(packetHash);
        if (current == PacketState.FAILED_RETRYABLE) {
            redis.opsForValue().set(key, PacketState.PROCESSING.name(), ttl);
            return new ClaimResult(true, PacketState.PROCESSING);
        }
        return new ClaimResult(false, current);
    }

    public PacketState getState(String packetHash) {
        String value = redis.opsForValue().get(key(packetHash));
        if (value == null) {
            return null;
        }
        try {
            return PacketState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return PacketState.RECEIVED;
        }
    }

    public void markSettled(String packetHash) {
        mark(packetHash, PacketState.SETTLED);
    }

    public void markRejected(String packetHash) {
        mark(packetHash, PacketState.REJECTED);
    }

    public void markExpired(String packetHash) {
        mark(packetHash, PacketState.EXPIRED);
    }

    public void markFailedRetryable(String packetHash) {
        mark(packetHash, PacketState.FAILED_RETRYABLE);
    }

    private void mark(String packetHash, PacketState state) {
        redis.opsForValue().set(key(packetHash), state.name(), ttl);
    }

    private String key(String packetHash) {
        return KEY_PREFIX + packetHash;
    }

    /** Approximate count when claims or expirations occur during the scan. */
    public int size() {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redis.scan(SCAN_OPTIONS)) {
            cursor.forEachRemaining(keys::add);
        }
        return keys.size();
    }

    /** Demo/test reset; other Redis namespaces are untouched. */
    public void clear() {
        try (Cursor<String> cursor = redis.scan(SCAN_OPTIONS)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }
}
