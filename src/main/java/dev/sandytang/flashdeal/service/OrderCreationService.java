package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.domain.OrderMessage;
import dev.sandytang.flashdeal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationService {
    private final VoucherOrderRepository orders;
    private final SeckillVoucherRepository vouchers;

    public OrderCreationService(VoucherOrderRepository orders, SeckillVoucherRepository vouchers) {
        this.orders = orders; this.vouchers = vouchers;
    }

    @Transactional
    public void create(OrderMessage event) {
        if (orders.existsById(event.orderId()) ||
                orders.existsByVoucherIdAndUserId(event.voucherId(), event.userId())) return;
        if (vouchers.decrementIfAvailable(event.voucherId()) != 1) {
            throw new IllegalStateException("Database stock exhausted or voucher missing");
        }
        orders.saveAndFlush(new VoucherOrder(event.orderId(), event.userId(), event.voucherId(),
                VoucherOrder.Status.CREATED, event.reservedAt()));
    }
}
