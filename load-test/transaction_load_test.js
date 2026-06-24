import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(99)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8080';
const SOURCE_ACCOUNT = 'd716628c-cdee-4c6c-9c03-6e1f595ad196';
const DEST_ACCOUNT = '5b0e2e38-1dc3-4acb-9c9f-8d84d94783a3';

export function setup() {
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: 'test@tps.local',
    password: 'Test@12345'
  }), { headers: { 'Content-Type': 'application/json' } });

  const token = loginRes.json('data.accessToken');
  console.log(`Token acquired: ${token ? 'yes' : 'no'}`);
  return { token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
    'X-Idempotency-Key': `load-test-${__VU}-${__ITER}-${Date.now()}`,
  };

  const res = http.post(`${BASE_URL}/api/v1/transactions`, JSON.stringify({
    sourceAccountId: SOURCE_ACCOUNT,
    destinationAccountId: DEST_ACCOUNT,
    amount: 0.01,
    currency: 'USD',
    description: 'load test'
  }), { headers });

  check(res, {
    'status is 201': (r) => r.status === 201,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(0.1);
}