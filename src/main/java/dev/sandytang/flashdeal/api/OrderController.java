package dev.sandytang.flashdeal.api;

import dev.sandytang.flashdeal.persistence.VoucherOrder;
import dev.sandytang.flashdeal.persistence.VoucherOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final VoucherOrderRepository orders;

    public OrderController(VoucherOrderRepository orders) { this.orders = orders; }

    @GetMapping("/{orderId}")
    ResponseEntity<VoucherOrder> find(@PathVariable long orderId,
                                      @RequestHeader("X-User-Id") long userId) {
        return orders.findByIdAndUserId(orderId, userId)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
