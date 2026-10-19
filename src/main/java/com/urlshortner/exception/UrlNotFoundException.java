package com.urlshortner.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String shortCode) {
        super("No short URL found for code '" + shortCode + "'");
    }
}
