package com.urlshortner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.urlshortner.config.AppProperties;
import com.urlshortner.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExpiredUrlCleanupJobTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Mock ShortUrlRepository repository;
    @Mock UrlCacheService cache;

    ExpiredUrlCleanupJob job;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost", 302, 7, Duration.ofHours(1), 2);
        job = new ExpiredUrlCleanupJob(repository, cache, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void doesNothingWhenNothingExpired() {
        when(repository.findExpiredShortCodes(any(Instant.class), any(Pageable.class))).thenReturn(List.of());

        assertThat(job.purgeExpiredBatches()).isZero();

        verify(cache, never()).evictAll(anyCollection());
        verify(repository, never()).deleteByShortCodes(anyCollection());
    }

    @Test
    void purgesInBatchesFromRedisAndDatabase() {
        List<String> first = List.of("a1", "a2");
        List<String> second = List.of("b1");
        when(repository.findExpiredShortCodes(any(Instant.class), any(Pageable.class)))
                .thenReturn(first, second);
        when(repository.deleteByShortCodes(first)).thenReturn(2);
        when(repository.deleteByShortCodes(second)).thenReturn(1);

        assertThat(job.purgeExpiredBatches()).isEqualTo(3);

        verify(cache).evictAll(first);
        verify(cache).evictAll(second);
    }

    @Test
    void swallowsFailuresSoTheScheduleKeepsRunning() {
        when(repository.findExpiredShortCodes(any(Instant.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(job.purgeExpiredBatches()).isZero();
    }
}
