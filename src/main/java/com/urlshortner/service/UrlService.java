package com.urlshortner.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Deliberately not {@code @Transactional}: a unique-constraint violation on insert must not poison
 * an outer transaction, so each repository call runs in its own short transaction.
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "swagger-ui", "v3", "actuator", "error", "health", "docs");

    private final ShortUrlRepository repository;
    private final UrlCacheService cache;
    private final ClickCounterService clickCounter;
    private final ShortCodeGenerator generator;
    private final AppProperties props;
    private final Clock clock;

    public UrlService(ShortUrlRepository repository, UrlCacheService cache, ClickCounterService clickCounter,
                      ShortCodeGenerator generator, AppProperties props, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.clickCounter = clickCounter;
        this.generator = generator;
        this.props = props;
        this.clock = clock;
    }

    public UrlResponse createShortUrl(CreateUrlRequest request) {
        Instant now = clock.instant();
        String originalUrl = request.url().trim();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new InvalidRequestException("expiresAt must be in the future");
        }

        ShortUrl saved = null;
        String message = null;
        if (request.customAlias() != null) {
            saved = saveWithAlias(request.customAlias(), originalUrl, now, request.expiresAt());
            if (saved == null) {
                message = "Alias '" + request.customAlias() + "' is already in use, so a generated short code was used instead";
            }
        }
        if (saved == null) {
            saved = saveWithGeneratedCode(originalUrl, now, request.expiresAt());
        }

        cache.put(saved.getShortCode(), saved.getOriginalUrl(), saved.getExpiresAt());
        return new UrlResponse(saved.getShortCode(), shortUrlFor(saved.getShortCode()), saved.getOriginalUrl(),
                saved.getCreatedAt(), saved.getExpiresAt(), message);
    }

    /** Resolves the target URL (cache first, then database) and asynchronously counts the click. */
    public String resolveAndTrack(String shortCode) {
        String url = cache.get(shortCode).orElseGet(() -> loadFromDatabase(shortCode));
        clickCounter.recordClick(shortCode);
        return url;
    }

    public UrlStatsResponse getStats(String shortCode) {
        ShortUrl url = repository.findByShortCode(shortCode).orElseThrow(() -> new UrlNotFoundException(shortCode));
        return new UrlStatsResponse(url.getShortCode(), shortUrlFor(url.getShortCode()), url.getOriginalUrl(),
                url.getClickCount(), url.getCreatedAt(), url.getExpiresAt(), url.isExpired(clock.instant()),
                url.isCustomAlias());
    }

    private String loadFromDatabase(String shortCode) {
        ShortUrl url = repository.findByShortCode(shortCode).orElseThrow(() -> new UrlNotFoundException(shortCode));
        if (url.isExpired(clock.instant())) {
            cache.evict(shortCode);
            throw new UrlExpiredException(shortCode);
        }
        cache.put(shortCode, url.getOriginalUrl(), url.getExpiresAt());
        return url.getOriginalUrl();
    }

    /** Returns the saved entity, or {@code null} if the alias is already taken. */
    private ShortUrl    saveWithAlias(String alias, String originalUrl, Instant now, Instant expiresAt) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new InvalidRequestException("Alias '" + alias + "' is reserved");
        }
        if (repository.existsByShortCode(alias)) {
            return null;
        }
        try {
            return repository.saveAndFlush(new ShortUrl(alias, originalUrl, now, expiresAt, true));
        } catch (DataIntegrityViolationException e) {
            // lost a race with a concurrent request for the same alias
            return null;
        }
    }

    private ShortUrl saveWithGeneratedCode(String originalUrl, Instant now, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = generator.generate();
            if (repository.existsByShortCode(code)) {
                continue;
            }
            try {
                return repository.saveAndFlush(new ShortUrl(code, originalUrl, now, expiresAt, false));
            } catch (DataIntegrityViolationException e) {
                log.debug("Short code collision on attempt {}", attempt);
            }
        }
        throw new ShortCodeGenerationException(
                "Failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String shortUrlFor(String code) {
        String base = props.baseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + code;
    }
}
