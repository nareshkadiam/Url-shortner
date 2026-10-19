package com.urlshortner.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.urlshortner.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis cache of shortCode -> original URL. Redis outages are logged and swallowed so that
 * redirects keep working from the database.
 */
@Service
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);
    static final String KEY_PREFIX = "short:url:";

    private final StringRedisTemplate redis;
    private final AppProperties props;
    private final Clock clock;

    public UrlCacheService(StringRedisTemplate redis, AppProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + shortCode));
        } catch (RuntimeException e) {
            log.warn("Redis read failed for '{}': {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    /** Caches the URL for the default TTL, capped so it never outlives the link's expiration. */
    public void put(String shortCode, String originalUrl, Instant expiresAt) {
        Duration ttl = props.cacheTtl();
        if (expiresAt != null) {
            Duration untilExpiry = Duration.between(clock.instant(), expiresAt);
            if (untilExpiry.compareTo(ttl) < 0) {
                ttl = untilExpiry;
            }
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed for '{}': {}", shortCode, e.getMessage());
        }
    }

    public void evict(String shortCode) {
        evictAll(List.of(shortCode));
    }

    public void evictAll(Collection<String> shortCodes) {
        if (shortCodes.isEmpty()) {
            return;
        }
        try {
            redis.delete(shortCodes.stream().map(c -> KEY_PREFIX + c).toList());
        } catch (RuntimeException e) {
            log.warn("Redis eviction failed for {} keys: {}", shortCodes.size(), e.getMessage());
        }
    }
}
