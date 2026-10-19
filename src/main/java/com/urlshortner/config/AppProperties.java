package com.urlshortner.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue("302") int redirectStatus,
        @DefaultValue("7") int codeLength,
        @DefaultValue("24h") Duration cacheTtl,
        @DefaultValue("500") int cleanupBatchSize) {
}
