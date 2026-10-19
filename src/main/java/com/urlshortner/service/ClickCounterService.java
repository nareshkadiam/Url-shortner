package com.urlshortner.service;

import com.urlshortner.config.AppConfig;
import com.urlshortner.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ClickCounterService {

    private static final Logger log = LoggerFactory.getLogger(ClickCounterService.class);

    private final ShortUrlRepository repository;

    public ClickCounterService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Async(AppConfig.CLICK_EXECUTOR)
    public void recordClick(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
        } catch (RuntimeException e) {
            log.warn("Failed to record click for '{}': {}", shortCode, e.getMessage());
        }
    }
}
