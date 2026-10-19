package com.urlshortner.dto;

import java.time.Instant;

public record UrlStatsResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        long clickCount,
        Instant createdAt,
        Instant expiresAt,
        boolean expired,
        boolean customAlias) {
}
