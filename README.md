# API Rate Limiter (Spring Boot + Redis)

A small project that limits how many requests a user can make to an API.
It uses a **Fixed Window** approach: each user gets **5 requests per 60 seconds**.

## How it works

1. A request arrives at `/api/...`
2. `RateLimitInterceptor.preHandle()` runs **before** the controller.
3. It finds the user: the `user-id` header, or the IP address if there is no header.
4. It counts the request in Redis with one small Lua script (`INCR` + a 60 second expiry in a
   single atomic call). If Redis is down, it prints a warning and counts in a
   `ConcurrentHashMap` instead.
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
    └── test/java/com/example/ratelimiter/
        ├── RateLimitInterceptorTest.java (basic behaviour)
        └── ConcurrencyTest.java          (200 simultaneous requests)
benchmarks/
├── load-test.js                          (k6 load test)
└── RESULTS.md                            (my measured results)
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

This runs 5 tests. One of them (`ConcurrencyTest`) fires 200 requests at once from a
single user with a limit of 5, and checks that exactly 5 are allowed.

## Load test

Install [k6](https://k6.io/docs/get-started/installation/), set
`rate-limit.max-requests=1000000` so the limit does not block the test, start the app, then:

```bash
k6 run benchmarks/load-test.js
```

Write your results in `benchmarks/RESULTS.md`.

## Why a Lua script for Redis?

The first version used two Redis commands: `INCR`, then `EXPIRE` on the first request.
If the app crashed between them, the counter would never expire and that user could
be blocked forever. The Lua script runs both steps inside Redis as one unit, so this
cannot happen. It also cuts Redis round trips on a user's first request from 2 to 1.

## Change the limit

Open `src/main/resources/application.properties` and edit:

```properties
rate-limit.max-requests=5
rate-limit.window-seconds=60
```

Restart the app after changing them. No code changes are needed.

## Known limitations (good ideas for improvement)

- Fixed windows allow a burst at the window edge (5 requests at 0:59 and 5 more at 1:00).
  A sliding window or token bucket fixes this.
- The local backup map is per-server and never cleans up old users.
- `request.getRemoteAddr()` shows the proxy's IP if the app is behind a load balancer.
  You would then read the `X-Forwarded-For` header.
- The `user-id` header can be faked, so a real app would use login/API keys.
- One limit applies to every endpoint. Per-endpoint limits would be a good next step.