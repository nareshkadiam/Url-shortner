package com.urlshortner.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com/path?q=1&r=2#frag",
            "HTTPS://Example.com:8443/a",
            "http://localhost:8080/x",
            "  https://example.com  "
    })
    void acceptsHttpUrls(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com",
            "ftp://example.com/file",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "http://",
            "https:///nohost",
            "http://exa mple.com",
            "not a url"
    })
    void rejectsNonHttpOrMalformedUrls(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {2049, 5000})
    void rejectsOverlongUrls(int length) {
        String url = "https://example.com/" + "a".repeat(length);

        assertThat(validator.isValid(url, null)).isFalse();
    }
}
