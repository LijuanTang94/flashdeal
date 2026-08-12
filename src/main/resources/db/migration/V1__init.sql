CREATE TABLE seckill_voucher (
    id BIGINT PRIMARY KEY,
    stock INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_stock_nonnegative CHECK (stock >= 0)
);

CREATE TABLE voucher_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_order_voucher FOREIGN KEY (voucher_id) REFERENCES seckill_voucher(id),
    CONSTRAINT uk_voucher_user UNIQUE (voucher_id, user_id),
    INDEX idx_order_user_created (user_id, created_at)
);

INSERT INTO seckill_voucher(id, stock, starts_at, ends_at)
VALUES (1, 1000, UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY));
