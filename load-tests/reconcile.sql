-- Run after the RabbitMQ queue drains.
--
-- The point of this file is to prove the correctness claims with queries rather
-- than assert them. Compare `persisted_orders` against the `business_orders_accepted`
-- counter k6 reports: the gate accepted that many reservations, so that many rows
-- must exist. Anything missing is a lost order, not a rounding error.

-- 1. Stock actually left in the source of truth. Must never go below zero.
SELECT stock AS mysql_remaining_stock FROM seckill_voucher WHERE id = 1;

-- 2. Orders that made it all the way through the queue into MySQL.
--    Expected: equal to k6's business_orders_accepted, and equal to (1000 - remaining stock).
SELECT COUNT(*) AS persisted_orders
FROM voucher_order WHERE voucher_id = 1 AND status = 'CREATED';

-- 3. Oversell check: sold + remaining must reconcile to the initial 1000 units.
SELECT (SELECT COUNT(*) FROM voucher_order WHERE voucher_id = 1 AND status = 'CREATED')
     + (SELECT stock FROM seckill_voucher WHERE id = 1) AS sold_plus_remaining;

-- 4. One order per user. Must return zero rows.
--    Only meaningful when the load test reuses user ids (see REPEAT_SHARE in seckill.js);
--    with a fresh id per iteration this branch is never exercised.
SELECT user_id, COUNT(*) AS duplicate_count
FROM voucher_order WHERE voucher_id = 1
GROUP BY user_id HAVING COUNT(*) > 1;

-- 5. Which replicas actually served traffic.
--    The order id is a Snowflake: (timestamp << 22) | (workerId << 12) | sequence,
--    so bits 12..21 recover the worker id. Each app replica derives a distinct
--    worker id from its container hostname, so a scaled run should show one row
--    per replica -- this is what demonstrates the load balancer spread traffic
--    AND that concurrent replicas minted non-colliding ids.
SELECT (id >> 12) & 1023 AS snowflake_worker_id, COUNT(*) AS orders_minted
FROM voucher_order WHERE voucher_id = 1
GROUP BY snowflake_worker_id ORDER BY snowflake_worker_id;

-- 6. Duplicate order ids. Must return zero rows (id is the PK, so this is a
--    belt-and-braces check that the Snowflake generator never collided).
SELECT id, COUNT(*) AS times
FROM voucher_order GROUP BY id HAVING COUNT(*) > 1;
