package com.urlshortner.controller;

import java.net.URI;

import com.urlshortner.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Redirect")
public class RedirectController {

    private final UrlService urlService;
    private final HttpStatus redirectStatus;

    public RedirectController(UrlService urlService, @Value("${app.redirect-status:302}") int redirectStatusCode) {
        this.urlService = urlService;
        // 302 by default: browsers cache 301s indefinitely, which would bypass click counting and expiry
        this.redirectStatus = redirectStatusCode == 301 ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;
    }

    @GetMapping("/{shortCode:[A-Za-z0-9_-]{3,32}}")
    @Operation(summary = "Redirect to the original URL")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String target = urlService.resolveAndTrack(shortCode);
        return ResponseEntity.status(redirectStatus).header(HttpHeaders.LOCATION, URI.create(target).toString()).build();
    }
}
