package dev.sandytang.flashdeal.domain;

import java.time.Instant;

public record OrderMessage(long orderId, long userId, long voucherId, Instant reservedAt) {}
