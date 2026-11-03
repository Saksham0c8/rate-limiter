# API Rate Limiter (Spring Boot + Redis)

A small project that limits how many requests a user can make to an API.
It uses a **Fixed Window** approach: each user gets **5 requests per 60 seconds**.

## How it works

1. A request arrives at `/api/...`
2. `RateLimitInterceptor.preHandle()` runs **before** the controller.
3. It finds the user: the `user-id` header, or the IP address if there is no header.
4. It counts the request in Redis (`INCR` + a 60 second expiry).
   If Redis is down, it prints a warning and counts in a `ConcurrentHashMap` instead.
5. Under the limit: the request continues. Over the limit: HTTP `429` with a JSON message.
6. Every response has `X-Rate-Limit-Limit` and `X-Rate-Limit-Remaining` headers.

## Project structure

```
rate-limiter/
├── pom.xml
├── docker-compose.yml
└── src/
    ├── main/java/com/example/ratelimiter/
    │   ├── RateLimiterApplication.java   (starts the app)
    │   ├── RateLimitInterceptor.java     (the rate limiting logic)
    │   ├── WebConfig.java                (registers the interceptor)
    │   └── RateLimitController.java      (test endpoints)
    ├── main/resources/application.properties
    └── test/java/com/example/ratelimiter/RateLimitInterceptorTest.java
```

## Requirements

- Java 17 or newer
- Maven 3.6+
- Docker (optional, only if you want to use real Redis)

## Run it

```bash
# 1. (optional) start Redis
docker compose up -d

# 2. start the app
mvn spring-boot:run
```

The app runs on http://localhost:8080. Redis is optional: without it the
app falls back to in-memory counting.

## Try it

```bash
curl -i http://localhost:8080/api/ping
```

Run it 6 times. The first 5 return `pong` and the 6th returns `429`.

Check the headers:

```
X-Rate-Limit-Limit: 5
X-Rate-Limit-Remaining: 4
```

Pretend to be a different user:

```bash
curl -i -H "user-id: alice" http://localhost:8080/api/ping
```

Other endpoints: `/api/hello?name=Sam` and `/api/time`.

## Run the tests

```bash
mvn test
```

## Change the limit

Open `RateLimitInterceptor.java` and edit:

```java
private static final int MAX_REQUESTS = 5;
private static final int WINDOW_SECONDS = 60;
```

## Known limitations (good ideas for improvement)

- Fixed windows allow a burst at the window edge (5 requests at 0:59 and 5 more at 1:00).
  A sliding window or token bucket fixes this.
- The local backup map is per-server and never cleans up old users.
- `request.getRemoteAddr()` shows the proxy's IP if the app is behind a load balancer.
  You would then read the `X-Forwarded-For` header.
- The `user-id` header can be faked, so a real app would use login/API keys.
- Settings are hard-coded constants instead of coming from `application.properties`.
