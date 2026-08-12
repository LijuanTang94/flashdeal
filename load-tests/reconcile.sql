-- Run after the RabbitMQ queue drains. All three values should agree.
SELECT stock AS mysql_remaining_stock FROM seckill_voucher WHERE id = 1;
SELECT COUNT(*) AS persisted_orders FROM voucher_order WHERE voucher_id = 1 AND status = 'CREATED';
SELECT user_id, COUNT(*) AS duplicate_count
FROM voucher_order WHERE voucher_id = 1
GROUP BY user_id HAVING COUNT(*) > 1;
