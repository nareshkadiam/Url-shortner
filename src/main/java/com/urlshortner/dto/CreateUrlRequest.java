package com.urlshortner.dto;

import java.time.Instant;

import com.urlshortner.validation.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to create a short URL")
public record CreateUrlRequest(
        @Schema(example = "https://example.com/some/very/long/path?x=1")
        @NotBlank(message = "url must not be blank")
        @HttpUrl
        String url,

        @Schema(description = "Optional custom alias (3-32 chars: letters, digits, '_' or '-')", example = "my-link")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "customAlias must be 3-32 characters of letters, digits, '_' or '-'")
        String customAlias,

        @Schema(description = "Optional expiration timestamp (ISO-8601, must be in the future)",
                example = "2030-01-01T00:00:00Z")
        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {
}
