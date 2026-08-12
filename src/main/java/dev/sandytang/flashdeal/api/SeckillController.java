package dev.sandytang.flashdeal.api;

import dev.sandytang.flashdeal.service.SeckillService;
import dev.sandytang.flashdeal.service.SeckillTokenService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vouchers")
public class SeckillController {
    private final SeckillService seckillService;
    private final SeckillTokenService tokenService;

    public SeckillController(SeckillService seckillService, SeckillTokenService tokenService) {
        this.seckillService = seckillService;
        this.tokenService = tokenService;
    }

    @PostMapping("/{voucherId}/token")
    Map<String, Object> token(@RequestHeader("X-User-Id") @Positive long userId,
                              @PathVariable @Positive long voucherId) {
        return tokenService.issue(userId, voucherId);
    }

    @PostMapping("/{voucherId}/seckill")
    ResponseEntity<SeckillService.AcceptedOrder> seckill(
            @RequestHeader("X-User-Id") @Positive long userId,
            @RequestHeader("X-Seckill-Token") String token,
            @PathVariable @Positive long voucherId) {
        var accepted = seckillService.reserve(userId, voucherId, token);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/orders/" + accepted.orderId()))
                .body(accepted);
    }
}
