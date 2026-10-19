package com.urlshortner.service;

import java.time.Clock;
import java.util.List;

import com.urlshortner.config.AppProperties;
import com.urlshortner.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges expired links from Supabase and Redis in batches. If you run several instances, add a
 * distributed lock (e.g. ShedLock) or run the job on one instance only; the delete is idempotent so
 * overlapping runs are safe, just wasteful.
 */
@Component
public class ExpiredUrlCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredUrlCleanupJob.class);

    private final ShortUrlRepository repository;
    private final UrlCacheService cache;
    private final AppProperties props;
    private final Clock clock;

    public ExpiredUrlCleanupJob(ShortUrlRepository repository, UrlCacheService cache, AppProperties props,
                                Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cleanup-cron:0 0 * * * *}")
    public void purgeExpired() {
        int total = purgeExpiredBatches();
        if (total > 0) {
            log.info("Expired URL cleanup removed {} entries", total);
        }
    }

    int purgeExpiredBatches() {
        int total = 0;
        try {
            List<String> codes;
            do {
                codes = repository.findExpiredShortCodes(clock.instant(), PageRequest.of(0, props.cleanupBatchSize()));
                if (codes.isEmpty()) {
                    break;
                }
                cache.evictAll(codes);
                total += repository.deleteByShortCodes(codes);
            } while (codes.size() >= props.cleanupBatchSize());
        } catch (RuntimeException e) {
            log.error("Expired URL cleanup failed after removing {} entries", total, e);
        }
        return total;
    }
}
