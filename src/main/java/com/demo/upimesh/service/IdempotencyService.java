package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/** Disposable replay cache. PostgreSQL remains authoritative for settlement. */
@Service
public class IdempotencyService {
    private static final String KEY_PREFIX = "upi:mesh:idempotency:";
    private static final String INDEX = "upi:mesh:packet-expirations";
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[3])
            local current = redis.call('GET', KEYS[1])
            if not current or current == 'FAILED_RETRYABLE' or string.match(current, '^FAILED_RETRYABLE|')
                or (ARGV[4] ~= '' and current == ARGV[4] and not string.match(current, '^PROCESSING')) then
                local value = 'PROCESSING|' .. ARGV[1]
                redis.call('SET', KEYS[1], value, 'EX', ARGV[2])
                redis.call('ZADD', KEYS[2], ARGV[3] + ARGV[2], KEYS[1])
                redis.call('EXPIRE', KEYS[2], ARGV[5])
                return value
            end
            return current
            """, String.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= 'PROCESSING|' .. ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2] .. '|' .. ARGV[1], 'EX', ARGV[3])
            redis.call('ZADD', KEYS[2], ARGV[4] + ARGV[3], KEYS[1])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> REPAIR = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            redis.call('ZADD', KEYS[2], ARGV[4] + ARGV[3], KEYS[1])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final long leaseSeconds;
    private final long retentionSeconds;

    public IdempotencyService(StringRedisTemplate redis,
            @Value("${upi.mesh.processing-lease-seconds:90}") long leaseSeconds,
            @Value("${upi.mesh.replay-retention-seconds:86400}") long retentionSeconds) {
        if (leaseSeconds <= 0 || retentionSeconds <= 0) throw new IllegalArgumentException("Positive cache durations required");
        this.redis = redis;
        this.leaseSeconds = leaseSeconds;
        this.retentionSeconds = retentionSeconds;
    }

    public enum PacketState { RECEIVED, PROCESSING, SETTLED, FAILED_RETRYABLE, REJECTED, EXPIRED }
    public record ClaimResult(boolean claimed, PacketState state, String token) {
        String encoded() { return state.name() + (token == null ? "" : "|" + token); }
    }

    public boolean claim(String hash) { return claimProcessing(hash).claimed(); }
    public ClaimResult claimProcessing(String hash) { return claim(hash, ""); }

    /** Called only after verified database lookup disproves a stale terminal cache entry. */
    public ClaimResult reclaimAfterDatabaseCheck(String hash, ClaimResult observed) {
        return claim(hash, observed.encoded());
    }

    private ClaimResult claim(String hash, String expected) {
        String token = UUID.randomUUID().toString();
        String value = redis.execute(CLAIM, List.of(key(hash), INDEX), token,
                Long.toString(leaseSeconds), Long.toString(now()), expected, indexRetention());
        if (value == null) throw new IllegalStateException("Redis claim returned no result");
        ClaimResult parsed = parse(value);
        return new ClaimResult(token.equals(parsed.token()), parsed.state(), parsed.token());
    }

    private ClaimResult parse(String value) {
        if (value == null) return new ClaimResult(false, null, null);
        String[] parts = value.split("\\|", 2);
        PacketState state;
        try { state = PacketState.valueOf(parts[0]); }
        catch (IllegalArgumentException e) { state = PacketState.RECEIVED; }
        return new ClaimResult(false, state, parts.length == 2 ? parts[1] : null);
    }

    public PacketState getState(String hash) { return parse(redis.opsForValue().get(key(hash))).state(); }

    /** Database terminal results are immutable. CAS repair cannot overwrite a newer cache attempt. */
    public void repairTerminal(String hash, ClaimResult observed, PacketState databaseState) {
        if (databaseState != PacketState.SETTLED && databaseState != PacketState.REJECTED
                && databaseState != PacketState.EXPIRED) throw new IllegalArgumentException("Terminal DB state required");
        redis.execute(REPAIR, List.of(key(hash), INDEX), observed.encoded(), databaseState.name(),
                Long.toString(retentionSeconds), Long.toString(now()), indexRetention());
    }
    public void markSettled(String hash, String token) { mark(hash, token, PacketState.SETTLED); }
    public void markRejected(String hash, String token) { mark(hash, token, PacketState.REJECTED); }
    public void markExpired(String hash, String token) { mark(hash, token, PacketState.EXPIRED); }
    public void markFailedRetryable(String hash, String token) { mark(hash, token, PacketState.FAILED_RETRYABLE); }

    private void mark(String hash, String token, PacketState state) {
        if (token == null) throw new IllegalArgumentException("Attempt token required");
        redis.execute(FINISH, List.of(key(hash), INDEX), token, state.name(),
                Long.toString(retentionSeconds), Long.toString(now()), indexRetention());
    }
    private String indexRetention() { return Long.toString(Math.max(leaseSeconds, retentionSeconds)); }
    private long now() { return System.currentTimeMillis() / 1000; }
    private String key(String hash) { return KEY_PREFIX + hash; }

    /** Maintained expiry index: dashboard reads do not scan the key namespace. */
    public int size() {
        Long count = redis.execute(COUNT, List.of(INDEX), Long.toString(now()));
        return count == null ? 0 : (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** Infrequent demo/test reset; other namespaces are untouched. */
    public void clear() {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build())) {
            while (cursor.hasNext()) redis.delete(cursor.next());
        }
        redis.delete(INDEX);
    }
}
