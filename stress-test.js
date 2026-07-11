import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 50 },    // Ramp to 50 users
    { duration: '2m', target: 100 },   // Ramp to 100 users
    { duration: '2m', target: 200 },   // Ramp to 200 users (STRESS)
    { duration: '2m', target: 300 },   // Ramp to 300 users (EXTREME)
    { duration: '3m', target: 0 },     // Ramp down
  ],
};

export default function () {
  const res = http.get('http://localhost:8080/api/health');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  sleep(1);
}