import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 20 },    // 20 users - normal load
    { duration: '2m', target: 50 },    // 50 users - moderate load
    { duration: '2m', target: 100 },   // 100 users - high load
    { duration: '2m', target: 150 },   // 150 users - stress test
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    // Connection-related thresholds
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],  // 95% under 500ms, 99% under 1s
    'http_req_failed': ['rate<0.1'],   // Less than 0.1% failure rate
  },
};

export default function () {
  // Test multiple endpoints to stress database connections
  const endpoints = [
    'http://localhost:8080/api/health',
    // Add other endpoints that use database if available
    // 'http://localhost:8080/api/users',
    // 'http://localhost:8080/api/dashboard',
  ];

  const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];

  const res = http.get(endpoint);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'no timeout errors': (r) => r.status !== 408 && r.status !== 504,
  });

  sleep(0.5); // Shorter sleep = more concurrent connections
}