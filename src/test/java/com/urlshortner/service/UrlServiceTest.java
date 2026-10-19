package com.urlshortner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.urlshortner.config.AppProperties;
import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.dto.UrlStatsResponse;
import com.urlshortner.entity.ShortUrl;
import com.urlshortner.exception.InvalidRequestException;
import com.urlshortner.exception.ShortCodeGenerationException;
import com.urlshortner.exception.UrlExpiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String URL = "https://example.com/page";

    @Mock ShortUrlRepository repository;
    @Mock UrlCacheService cache;
    @Mock ClickCounterService clickCounter;
    @Mock ShortCodeGenerator generator;

    UrlService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://sho.rt/", 302, 7, Duration.ofHours(1), 100);
        service = new UrlService(repository, cache, clickCounter, generator, props,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsShortUrlWithGeneratedCodeAndWarmsCache() {
        when(generator.generate()).thenReturn("abc1234");
        when(repository.existsByShortCode("abc1234")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = service.createShortUrl(new CreateUrlRequest("  " + URL + " ", null, null));

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/abc1234");
        assertThat(response.originalUrl()).isEqualTo(URL);
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(cache).put("abc1234", URL, null);
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        when(generator.generate()).thenReturn("taken01", "free002");
        when(repository.existsByShortCode("taken01")).thenReturn(true);
        when(repository.existsByShortCode("free002")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.createShortUrl(new CreateUrlRequest(URL, null, null)).shortCode()).isEqualTo("free002");
    }

    @Test
    void retriesOnUniqueConstraintRaceThenGivesUp() {
        when(generator.generate()).thenReturn("race001");
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createShortUrl(new CreateUrlRequest(URL, null, null)))
                .isInstanceOf(ShortCodeGenerationException.class);
    }

    @Test
    void usesCustomAliasAndExpiration() {
        Instant expiry = NOW.plusSeconds(600);
        when(repository.existsByShortCode("my-link")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createShortUrl(new CreateUrlRequest(URL, "my-link", expiry));

        ArgumentCaptor<ShortUrl> saved = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getShortCode()).isEqualTo("my-link");
        assertThat(saved.getValue().isCustomAlias()).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiry);
        verify(generator, never()).generate();
    }

    @Test
    void fallsBackToGeneratedCodeWhenAliasTaken() {
        when(repository.existsByShortCode("taken")).thenReturn(true);
        when(repository.existsByShortCode("gen0001")).thenReturn(false);
        when(generator.generate()).thenReturn("gen0001");
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = service.createShortUrl(new CreateUrlRequest(URL, "taken", null));

        assertThat(response.shortCode()).isEqualTo("gen0001");
        assertThat(response.message()).contains("'taken'").contains("already in use");
    }

    @Test
    void fallsBackToGeneratedCodeWhenAliasLosesInsertRace() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(generator.generate()).thenReturn("gen0002");
        when(repository.saveAndFlush(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = service.createShortUrl(new CreateUrlRequest(URL, "racy", null));

        assertThat(response.shortCode()).isEqualTo("gen0002");
        assertThat(response.message()).isNotNull();
    }

    @Test
    void rejectsReservedAlias() {
        assertThatThrownBy(() -> service.createShortUrl(new CreateUrlRequest(URL, "API", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPastExpiration() {
        assertThatThrownBy(() -> service.createShortUrl(new CreateUrlRequest(URL, null, NOW.minusSeconds(1))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void resolveUsesCacheAndCountsClick() {
        when(cache.get("abc1234")).thenReturn(Optional.of(URL));

        assertThat(service.resolveAndTrack("abc1234")).isEqualTo(URL);

        verify(repository, never()).findByShortCode(anyString());
        verify(clickCounter).recordClick("abc1234");
    }

    @Test
    void resolveFallsBackToDatabaseAndPopulatesCache() {
        ShortUrl entity = new ShortUrl("abc1234", URL, NOW.minusSeconds(10), null, false);
        when(cache.get("abc1234")).thenReturn(Optional.empty());
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(entity));

        assertThat(service.resolveAndTrack("abc1234")).isEqualTo(URL);

        verify(cache).put("abc1234", URL, null);
        verify(clickCounter).recordClick("abc1234");
    }

    @Test
    void resolveUnknownCodeThrowsNotFoundWithoutCountingClick() {
        when(cache.get("nope123")).thenReturn(Optional.empty());
        when(repository.findByShortCode("nope123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndTrack("nope123")).isInstanceOf(UrlNotFoundException.class);
        verify(clickCounter, never()).recordClick(anyString());
    }

    @Test
    void resolveExpiredCodeThrowsGoneAndEvictsCache() {
        ShortUrl expired = new ShortUrl("old1234", URL, NOW.minusSeconds(100), NOW.minusSeconds(1), false);
        when(cache.get("old1234")).thenReturn(Optional.empty());
        when(repository.findByShortCode("old1234")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolveAndTrack("old1234")).isInstanceOf(UrlExpiredException.class);
        verify(cache).evict("old1234");
        verify(clickCounter, never()).recordClick(anyString());
    }

    @Test
    void statsReturnsMetadata() {
        ShortUrl entity = new ShortUrl("abc1234", URL, NOW.minusSeconds(10), NOW.plusSeconds(10), false);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(entity));

        UrlStatsResponse stats = service.getStats("abc1234");

        assertThat(stats.shortUrl()).isEqualTo("http://sho.rt/abc1234");
        assertThat(stats.originalUrl()).isEqualTo(URL);
        assertThat(stats.expired()).isFalse();
        assertThat(stats.clickCount()).isZero();
    }

    @Test
    void statsForUnknownCodeThrowsNotFound() {
        when(repository.findByShortCode("nope123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats("nope123")).isInstanceOf(UrlNotFoundException.class);
    }
}
