package com.urlshortner.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortner.exception.GlobalExceptionHandler;
import com.urlshortner.exception.UrlExpiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@Import(GlobalExceptionHandler.class)
class RedirectControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UrlService urlService;

    @Test
    void redirectsWith302ToOriginalUrl() throws Exception {
        when(urlService.resolveAndTrack("abc1234")).thenReturn("https://example.com/target?x=1");

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target?x=1"));
    }

    @Test
    void unknownCodeReturnsJson404() throws Exception {
        when(urlService.resolveAndTrack("nope123")).thenThrow(new UrlNotFoundException("nope123"));

        mvc.perform(get("/nope123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No short URL found for code 'nope123'"));
    }

    @Test
    void expiredCodeReturns410() throws Exception {
        when(urlService.resolveAndTrack("old1234")).thenThrow(new UrlExpiredException("old1234"));

        mvc.perform(get("/old1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void pathsThatCannotBeCodesAreNotTreatedAsRedirects() throws Exception {
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
    }
}
