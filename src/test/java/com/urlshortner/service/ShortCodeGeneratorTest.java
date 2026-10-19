package com.urlshortner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.urlshortner.config.AppProperties;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    @Test
    void generatesBase62CodesOfConfiguredLength() {
        ShortCodeGenerator generator =
                new ShortCodeGenerator(new AppProperties("http://x", 302, 9, Duration.ofHours(1), 10));

        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate()).hasSize(9).matches("[0-9A-Za-z]+");
        }
    }

    @Test
    void producesDistinctCodes() {
        ShortCodeGenerator generator =
                new ShortCodeGenerator(new AppProperties("http://x", 302, 7, Duration.ofHours(1), 10));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(10_000);
    }
}
