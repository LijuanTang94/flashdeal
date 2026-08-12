package dev.sandytang.flashdeal.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "seckill_voucher")
@Getter @Setter @NoArgsConstructor
public class SeckillVoucher {
    @Id private Long id;
    @Column(nullable = false) private int stock;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Version private long version;
}
