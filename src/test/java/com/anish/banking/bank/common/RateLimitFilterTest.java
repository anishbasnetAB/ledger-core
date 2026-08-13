package com.anish.banking.bank.common;

import com.anish.banking.bank.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Rate-limit thresholds themselves come from application.properties (app.ratelimit.*) — this
// test controls Redis's INCR return value directly rather than firing enough real requests
// to organically hit them, so it stays fast and deterministic regardless of what those
// numbers are set to.
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @MockitoBean StringRedisTemplate redis;
    ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private String bearerToken() {
        return "Bearer " + jwtService.generate("ratelimit-test@example.com", "USER");
    }

    private String transferBody() {
        return """
                {"sourceAccountId":1,"destinationAccountId":2,"amount":1.00}
                """;
    }

    @Test
    void allowsRequestUnderThreshold() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearerToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(transferBody()))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(429));
    }

    @Test
    void rejectsOverThresholdWithApiErrorShaped429() throws Exception {
        // Whatever app.ratelimit.transfer.max-requests is set to, this is well past it.
        when(valueOps.increment(anyString())).thenReturn(999L);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearerToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(transferBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/transfers"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());   // omitted, same as every other ApiError
    }

    @Test
    void loginIsRateLimitedSeparatelyFromTransfers() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(999L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"whatever123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        // key includes the endpoint label, so login and transfers never share a counter
        verify(redis).opsForValue();
        verify(valueOps).increment(contains(":login:"));
    }

    @Test
    void firstRequestOfANewWindowSetsExpiryOnce() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"whatever123"}
                                """))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(429));

        verify(redis).expire(anyString(), any(Duration.class));
    }

    @Test
    void subsequentRequestInTheSameWindowDoesNotResetExpiry() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(2L);   // not the window's first hit

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"whatever123"}
                                """))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(429));

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void redisFailureFailsOpenInsteadOfBlockingTheRequest() throws Exception {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"whatever123"}
                                """))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(429));
    }
}
