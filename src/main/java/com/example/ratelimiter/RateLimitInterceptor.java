package com.example.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Checks every incoming request BEFORE it reaches the controller.
 *
 * Strategy: Fixed Window.
 * Each user is allowed maxRequests requests per windowSeconds.
 * Both numbers come from application.properties.
 * Once the window is over, the counter starts again from zero.
 *
 * Storage: Redis first. If Redis is down, we use a plain
 * ConcurrentHashMap in memory as a backup.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // Settings, read from application.properties
    private final int maxRequests;      // allowed requests per window
    private final int windowSeconds;    // window length in seconds

    // Used to talk to Redis
    private final StringRedisTemplate redisTemplate;

    // Local backup storage: key = user id, value = their counter
    private final ConcurrentHashMap<String, RequestCounter> localCounters = new ConcurrentHashMap<>();

    // Spring gives us the StringRedisTemplate automatically (constructor injection).
    // @Value reads a number from application.properties.
    // The part after the colon is the default if the property is missing.
    public RateLimitInterceptor(
            StringRedisTemplate redisTemplate,
            @Value("${rate-limit.max-requests:5}") int maxRequests,
            @Value("${rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Small helper class that remembers how many requests a user made
     * and when their current window started.
     */
    private static class RequestCounter {
        int count;
        long windowStartMillis;

        RequestCounter(long windowStartMillis) {
            this.count = 0;
            this.windowStartMillis = windowStartMillis;
        }
    }

    /**
     * Runs before the controller method.
     * Return true  -> request continues to the controller.
     * Return false -> request is stopped here.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1. Find out who is calling
        String clientId = getClientId(request);

        // 2. Count this request (Redis first, local map if Redis fails)
        int requestCount;
        try {
            requestCount = countUsingRedis(clientId);
        } catch (Exception e) {
            // Redis is probably not running, so just warn and use the backup
            System.out.println("WARNING: Redis failed, using local memory instead. Reason: " + e.getMessage());
            requestCount = countUsingLocalMap(clientId);
        }

        // 3. Work out how many requests the user has left (never below 0)
        int remaining = Math.max(0, maxRequests - requestCount);

        // 4. Add helpful headers to every response
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

        // 5. If the user went over the limit, block the request
        if (requestCount > maxRequests) {
            response.setStatus(429); // 429 = Too Many Requests
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Too many requests\", \"message\": \"Please wait and try again later.\"}");
            return false; // stop here, do not call the controller
        }

        // 6. Under the limit, let the request through
        return true;
    }

    /**
     * Decides who the user is.
     * First tries the "user-id" header, otherwise uses the IP address.
     */
    private String getClientId(HttpServletRequest request) {
        String userId = request.getHeader("user-id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        return request.getRemoteAddr();
    }

    /**
     * Fixed window counting with Redis.
     * INCR adds 1 to the key (it creates the key with value 1 if missing).
     * On the very first request we set the key to expire after windowSeconds,
     * so Redis resets the counter for us automatically.
     */
    private int countUsingRedis(String clientId) {
        String key = "rate_limit:" + clientId;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            throw new IllegalStateException("Redis returned null");
        }

        if (count == 1) {
            // first request in this window, start the timer
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return count.intValue();
    }

    /**
     * Same idea as above but stored in a normal Java map.
     * "synchronized" means only one thread can run this at a time,
     * so two requests cannot mess up the counter together.
     */
    private synchronized int countUsingLocalMap(String clientId) {
        long now = System.currentTimeMillis();

        RequestCounter counter = localCounters.get(clientId);

        // New user, or their window is over -> start a fresh counter
        if (counter == null || now - counter.windowStartMillis >= windowSeconds * 1000L) {
            counter = new RequestCounter(now);
            localCounters.put(clientId, counter);
        }

        counter.count++;
        return counter.count;
    }
}
