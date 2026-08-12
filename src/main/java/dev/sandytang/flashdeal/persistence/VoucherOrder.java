package dev.sandytang.flashdeal.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "voucher_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_voucher_user", columnNames = {"voucher_id", "user_id"})
})
@Getter @Setter @NoArgsConstructor
public class VoucherOrder {
    @Id private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "voucher_id", nullable = false) private Long voucherId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public VoucherOrder(long id, long userId, long voucherId, Status status, Instant createdAt) {
        this.id = id; this.userId = userId; this.voucherId = voucherId;
        this.status = status; this.createdAt = createdAt;
    }
    public enum Status { CREATED, CANCELLED }
}
