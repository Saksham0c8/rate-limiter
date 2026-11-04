package com.example.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sends 200 requests at the SAME time from ONE user.
 * The limit is 5, so exactly 5 should be allowed and 195 blocked.
 * If two threads ever updated the counter at the same moment and got it wrong,
 * this test would catch it.
 */
@SpringBootTest(properties = {
        "spring.data.redis.port=1",          // no Redis here, so the local map is tested
        "spring.data.redis.timeout=500ms",
        "rate-limit.max-requests=5",
        "rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class ConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exactlyFiveOutOfTwoHundredConcurrentRequestsAreAllowed() throws Exception {
        int totalRequests = 200;

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch startSignal = new CountDownLatch(1);   // all threads wait for this
        CountDownLatch done = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            pool.submit(() -> {
                try {
                    startSignal.await(); // wait so all requests fire together
                    int status = mockMvc.perform(get("/api/ping").header("user-id", "concurrent-user"))
                            .andReturn().getResponse().getStatus();
                    if (status == 200) {
                        allowed.incrementAndGet();
                    } else if (status == 429) {
                        blocked.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        startSignal.countDown(); // go!
        assertTrue(done.await(30, TimeUnit.SECONDS), "requests did not finish in time");
        pool.shutdown();

        assertEquals(5, allowed.get(), "allowed requests");
        assertEquals(195, blocked.get(), "blocked requests");
    }
}
