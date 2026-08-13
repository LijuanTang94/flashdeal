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

// Peak offered rate in iterations/s (each iteration = 2 HTTP requests).
// The default 10k ramp does not saturate a single replica on a modern laptop;
// pass a higher PEAK_RATE to look for the actual ceiling.
const PEAK = Number(__ENV.PEAK_RATE || 10000);
const STAGE = __ENV.STAGE_DURATION || '30s';

export const options = {
  scenarios: {
    staged_seckill: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 500),
      maxVUs: Number(__ENV.MAX_VUS || 20000),
      stages: [
        { target: Math.round(PEAK * 0.1), duration: STAGE },
        { target: Math.round(PEAK * 0.3), duration: STAGE },
        { target: Math.round(PEAK * 0.5), duration: STAGE },
        { target: PEAK, duration: STAGE },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    seckill_latency: ['p(95)<100'],
    business_token_failures: ['count==0'],
  },
};

// Share of iterations that deliberately reuse a small pool of user ids, so the
// one-order-per-user dedup path is actually exercised under load. With every
// iteration using a fresh id (the previous behaviour) that branch never ran,
// which meant "0 duplicate orders" had no load-test evidence behind it.
const REPEAT_SHARE = Number(__ENV.REPEAT_SHARE || 0.2);
const REPEAT_POOL = Number(__ENV.REPEAT_POOL || 200);

export default function () {
  const fresh = (__VU * 1000000) + __ITER;
  // Deterministic split: one in every 1/REPEAT_SHARE iterations draws from a
  // small shared pool, so those users retry and must be rejected as duplicates.
  const useRepeat = REPEAT_SHARE > 0 && (fresh % Math.round(1 / REPEAT_SHARE) === 0);
  const userId = useRepeat ? (fresh % REPEAT_POOL) : fresh;
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
