package com.urlshortner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.urlshortner.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    UrlCacheService cache;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost", 302, 7, Duration.ofHours(1), 100);
        cache = new UrlCacheService(redis, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getReadsPrefixedKey() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("short:url:abc")).thenReturn("https://example.com");

        assertThat(cache.get("abc")).contains("https://example.com");
    }

    @Test
    void getReturnsEmptyWhenRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.get("abc")).isEqualTo(Optional.empty());
    }

    @Test
    void putUsesDefaultTtlWhenNoExpiry() {
        when(redis.opsForValue()).thenReturn(ops);

        cache.put("abc", "https://example.com", null);

        verify(ops).set("short:url:abc", "https://example.com", Duration.ofHours(1));
    }

    @Test
    void putCapsTtlAtLinkExpiry() {
        when(redis.opsForValue()).thenReturn(ops);

        cache.put("abc", "https://example.com", NOW.plusSeconds(30));

        verify(ops).set("short:url:abc", "https://example.com", Duration.ofSeconds(30));
    }

    @Test
    void putSkipsAlreadyExpiredLinks() {
        cache.put("abc", "https://example.com", NOW.minusSeconds(1));

        verify(redis, never()).opsForValue();
    }

    @Test
    void putSwallowsRedisFailures() {
        when(redis.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        cache.put("abc", "https://example.com", null); // must not throw
    }

    @Test
    void evictAllDeletesPrefixedKeys() {
        cache.evictAll(List.of("a", "b"));

        verify(redis).delete(List.of("short:url:a", "short:url:b"));
    }
}
