package com.urlshortner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.dto.UrlStatsResponse;
import com.urlshortner.exception.GlobalExceptionHandler;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.service.UrlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
@Import(GlobalExceptionHandler.class)
class UrlControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UrlService urlService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(urlService.createShortUrl(any())).thenReturn(new UrlResponse(
                "abc1234", "http://localhost:8080/abc1234", "https://example.com", Instant.parse("2030-01-01T00:00:00Z"), null));

        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"customAlias\":\"abc1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));

        ArgumentCaptor<CreateUrlRequest> captor = ArgumentCaptor.forClass(CreateUrlRequest.class);
        verify(urlService).createShortUrl(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().customAlias()).isEqualTo("abc1234");
    }

    @Test
    void createRejectsInvalidUrlAndAliasWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\",\"customAlias\":\"a b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void createRejectsBlankUrl() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
    }

    @Test
    void createRejectsPastExpiration() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2001-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expiresAt"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void statsReturnsMetadata() throws Exception {
        when(urlService.getStats("abc1234")).thenReturn(new UrlStatsResponse("abc1234", "http://localhost:8080/abc1234",
                "https://example.com", 42, Instant.parse("2030-01-01T00:00:00Z"), null, false, false));

        mvc.perform(get("/api/v1/urls/abc1234/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(42))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void statsReturns404ForUnknownCode() throws Exception {
        when(urlService.getStats("nope123")).thenThrow(new UrlNotFoundException("nope123"));

        mvc.perform(get("/api/v1/urls/nope123/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
