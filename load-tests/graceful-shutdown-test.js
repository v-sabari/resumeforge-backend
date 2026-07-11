import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },    // Build up load
    { duration: '5m', target: 50 },    // Maintain load (KILL BACKEND HERE)
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    'http_req_duration': ['p(99)<2000'],  // Allow longer during shutdown
    'http_req_failed': ['rate<0.05'],     // Allow up to 5% failures during shutdown
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/health');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  sleep(0.5);
}