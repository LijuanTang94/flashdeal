import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const accepted = new Counter('business_orders_accepted');
const rejected = new Counter('business_orders_rejected');
const tokenFailures = new Counter('business_token_failures');
const acceptanceRate = new Rate('business_acceptance_rate');
const seckillLatency = new Trend('seckill_latency', true);
http.setResponseCallback(http.expectedStatuses(200, 202, 409));

export const options = {
  scenarios: {
    staged_seckill: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 10000,
      stages: [
        { target: 1000, duration: '30s' },
        { target: 3000, duration: '30s' },
        { target: 5000, duration: '30s' },
        { target: 10000, duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    seckill_latency: ['p(95)<100'],
    business_token_failures: ['count==0'],
  },
};

export default function () {
  const userId = (__VU * 1000000) + __ITER;
  const headers = { 'X-User-Id': String(userId) };
  const tokenResponse = http.post(`${BASE}/api/v1/vouchers/1/token`, null, { headers });
  if (!check(tokenResponse, { 'token issued': r => r.status === 200 })) {
    tokenFailures.add(1);
    return;
  }

  headers['X-Seckill-Token'] = tokenResponse.json('token');
  const response = http.post(`${BASE}/api/v1/vouchers/1/seckill`, null, { headers });
  seckillLatency.add(response.timings.duration);
  const ok = response.status === 202;
  acceptanceRate.add(ok);
  if (ok) accepted.add(1); else rejected.add(1);
  check(response, { 'business response is expected': r => [202, 409].includes(r.status) });
}
