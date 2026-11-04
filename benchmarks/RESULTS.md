# Benchmark results

Fill this in with YOUR OWN measurements. Only write numbers you actually measured.

## Test setup

- Machine: (CPU, RAM, OS)
- Java version:
- Redis: (running in Docker / not running = local fallback)
- Tool: k6, 50 virtual users, 30 seconds, `rate-limit.max-requests=1000000`
- Command: `k6 run benchmarks/load-test.js`

## Results

| Storage | Requests/sec | p95 latency | Error rate |
|---|---|---|---|
| Redis (Lua script) | | | |
| Local fallback (no Redis) | | | |

## Correctness checks (from `mvn test`)

- 200 simultaneous requests from one user with a limit of 5: exactly 5 allowed, 195 blocked (`ConcurrencyTest`)
- Lua script: 200 parallel calls on one key gave a counter of exactly 200, with a 60 second expiry set

## Notes

(Anything you noticed, for example how the numbers changed when Redis was stopped.)