package com.urlshortner.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.urlshortner.entity.ShortUrl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /** Atomic increment executed in the database, safe under concurrent clicks. */
    @Modifying
    @Transactional
    @Query("update ShortUrl u set u.clickCount = u.clickCount + 1 where u.shortCode = :code")
    int incrementClickCount(@Param("code") String code);

    @Query("select u.shortCode from ShortUrl u where u.expiresAt is not null and u.expiresAt <= :now order by u.expiresAt")
    List<String> findExpiredShortCodes(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from ShortUrl u where u.shortCode in :codes")
    int deleteByShortCodes(@Param("codes") Collection<String> codes);
}
