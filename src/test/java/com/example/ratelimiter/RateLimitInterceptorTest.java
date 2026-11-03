package com.example.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the rate limiter.
 * Port 1 is used for Redis on purpose: nothing runs there, so these tests
 * also check that the local ConcurrentHashMap backup works.
 */
@SpringBootTest(properties = {
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=500ms",
        // set the limit here too, so the tests do not depend on application.properties
        "rate-limit.max-requests=5",
        "rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class RateLimitInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsPong() throws Exception {
        mockMvc.perform(get("/api/ping").header("user-id", "test-pong"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void remainingHeaderGoesDown() throws Exception {
        mockMvc.perform(get("/api/ping").header("user-id", "test-remaining"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "4"));
        mockMvc.perform(get("/api/ping").header("user-id", "test-remaining"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "3"));
    }

    @Test
    void sixthRequestIsBlocked() throws Exception {
        // first 5 requests are fine
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("user-id", "test-blocked"))
                    .andExpect(status().isOk());
        }
        // the 6th one is too many
        mockMvc.perform(get("/api/ping").header("user-id", "test-blocked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void differentUsersHaveSeparateLimits() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/ping").header("user-id", "test-user-a"));
        }
        // user B has not made any requests yet, so should still be allowed
        mockMvc.perform(get("/api/ping").header("user-id", "test-user-b"))
                .andExpect(status().isOk());
    }
}