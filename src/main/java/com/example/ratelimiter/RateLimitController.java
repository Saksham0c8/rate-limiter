package com.example.ratelimiter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Simple endpoints just for testing the rate limiter.
 */
@RestController
public class RateLimitController {

    // GET /api/ping
    @GetMapping("/api/ping")
    public String ping() {
        return "pong";
    }

    // GET /api/hello?name=Rahul
    @GetMapping("/api/hello")
    public String hello(@RequestParam(defaultValue = "friend") String name) {
        return "Hello, " + name + "!";
    }

    // GET /api/time
    @GetMapping("/api/time")
    public String time() {
        return "Server time is " + LocalDateTime.now();
    }
}
