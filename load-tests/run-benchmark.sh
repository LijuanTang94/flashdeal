#!/usr/bin/env bash
# Reproducible load-test harness for FlashDeal.
#
#   ./load-tests/run-benchmark.sh 1     # baseline: one replica, through nginx
#   ./load-tests/run-benchmark.sh 3     # scaled:   three replicas, through nginx
#
# Both runs go through nginx so the only variable is replica count. k6 runs
# inside the compose network (Docker Desktop's host port-forwarding on macOS is
# slow enough that testing from the host measures the proxy, not the app).
set -euo pipefail

SCALE="${1:?usage: run-benchmark.sh <replica-count>}"
cd "$(dirname "$0")/.."

RESULTS_DIR="load-tests/results"
mkdir -p "$RESULTS_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$RESULTS_DIR/scale-${SCALE}-${STAMP}"
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.scale.yml)
NETWORK="$(basename "$PWD" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]')_default"

echo "=== [1/5] Tearing down any previous stack (including volumes) ==="
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true

echo "=== [2/5] Starting stack with ${SCALE} app replica(s) ==="
"${COMPOSE[@]}" up -d --build --scale "app=${SCALE}"

echo "--- waiting for the gate to answer through nginx ---"
for _ in $(seq 1 90); do
  if docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 \
        -s -o /dev/null -w '%{http_code}' -X POST \
        -H 'X-User-Id: 999999999' http://nginx:80/api/v1/vouchers/1/token \
        2>/dev/null | grep -q '200'; then
    echo "ready"; break
  fi
  sleep 2
done

echo "--- replicas registered ---"
"${COMPOSE[@]}" ps --format '{{.Name}}\t{{.Service}}' | grep -c app || true

echo "=== [3/5] Running k6 (inside the compose network, target nginx:80) ==="
set +e
docker run --rm --network "$NETWORK" \
  -v "$PWD/load-tests:/scripts" -v "$PWD/$RESULTS_DIR:/results" \
  -e BASE_URL=http://nginx:80 \
  -e PEAK_RATE="${PEAK_RATE:-10000}" \
  -e STAGE_DURATION="${STAGE_DURATION:-30s}" \
  -e MAX_VUS="${MAX_VUS:-20000}" \
  -e PRE_VUS="${PRE_VUS:-500}" \
  -e REPEAT_SHARE="${REPEAT_SHARE:-0.2}" \
  grafana/k6:0.54.0 run /scripts/seckill.js \
  --summary-export "/results/$(basename "$OUT").json" \
  | tee "${OUT}.txt"
K6_EXIT=${PIPESTATUS[0]}
set -e
echo "k6 exit code: ${K6_EXIT} (99 = a threshold failed; the run itself still completed)"

echo "=== [4/5] Draining the queue before reconciling ==="
sleep 20

echo "=== [5/5] Reconciling against MySQL ==="
"${COMPOSE[@]}" exec -T mysql mysql -uroot -proot flashdeal \
  < load-tests/reconcile.sql | tee "${OUT}-reconcile.txt"

echo
echo "Artifacts:"
echo "  ${OUT}.json           (k6 summary)"
echo "  ${OUT}.txt            (k6 console output)"
echo "  ${OUT}-reconcile.txt  (correctness queries)"
