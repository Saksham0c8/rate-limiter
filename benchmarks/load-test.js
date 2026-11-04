// k6 load test for the rate limiter.
//
// Install k6: https://k6.io/docs/get-started/installation/
// Start the app first (mvn spring-boot:run), then run:
//     k6 run benchmarks/load-test.js
//
// Each virtual user (VU) sends requests with its OWN user-id, so every user
// has their own counter. We raise the limit for this test so we measure
// speed, not blocking. In application.properties set:
//     rate-limit.max-requests=1000000

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,            // 50 users at the same time
  duration: '30s',    // run for 30 seconds
  thresholds: {
    http_req_failed: ['rate<0.01'],     // fewer than 1% errors
    http_req_duration: ['p(95)<100'],   // 95% of requests faster than 100 ms
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/ping', {
    headers: { 'user-id': `user-${__VU}` },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has remaining header': (r) => r.headers['X-Rate-Limit-Remaining'] !== undefined,
  });
}