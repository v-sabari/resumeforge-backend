import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 100 },   // Ramp to 100
    { duration: '3m', target: 200 },   // Ramp to 200
    { duration: '3m', target: 100 },   // Ramp down to 100
    { duration: '2m', target: 0 },     // Ramp down to 0
  ],
  thresholds: {
    'http_req_duration': ['p(90)<100', 'p(95)<200', 'p(99)<500'],
    'http_req_failed': ['rate<0.01'],  // Less than 1% failure
  },
};

export default function () {
  // Continuously hammer health endpoint
  const res = http.get('http://localhost:8080/api/health');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 100ms': (r) => r.timings.duration < 100,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(0.1); // Minimal sleep = max connections
}