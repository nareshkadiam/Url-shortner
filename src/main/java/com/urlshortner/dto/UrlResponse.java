package com.urlshortner.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UrlResponse(String shortCode, String shortUrl, String originalUrl, Instant createdAt, Instant expiresAt,
                          String message) {

    public UrlResponse(String shortCode, String shortUrl, String originalUrl, Instant createdAt, Instant expiresAt) {
        this(shortCode, shortUrl, originalUrl, createdAt, expiresAt, null);
    }
}
