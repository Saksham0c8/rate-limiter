package com.example.ratelimiter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tells Spring MVC to use our RateLimitInterceptor.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    // Spring injects our interceptor here
    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only apply the rate limiter to URLs starting with /api/
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
