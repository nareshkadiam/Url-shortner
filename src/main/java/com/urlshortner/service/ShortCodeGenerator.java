package com.urlshortner.service;

import java.security.SecureRandom;

import com.urlshortner.config.AppProperties;
import org.springframework.stereotype.Component;

/** Generates random base62 codes; 62^7 is roughly 3.5 trillion combinations at the default length. */
@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public ShortCodeGenerator(AppProperties props) {
        this.length = props.codeLength();
    }

    public String generate() {
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
