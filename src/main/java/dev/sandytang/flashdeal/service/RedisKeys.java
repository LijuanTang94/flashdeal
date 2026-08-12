package dev.sandytang.flashdeal.service;

final class RedisKeys {
    private RedisKeys() {}
    static String stock(long voucherId) { return "flashdeal:voucher:" + voucherId + ":stock"; }
    static String buyers(long voucherId) { return "flashdeal:voucher:" + voucherId + ":buyers"; }
    static String token(long voucherId, long userId) {
        return "flashdeal:voucher:" + voucherId + ":token:" + userId;
    }
    static String orderState(long orderId) { return "flashdeal:order:" + orderId + ":state"; }
}
