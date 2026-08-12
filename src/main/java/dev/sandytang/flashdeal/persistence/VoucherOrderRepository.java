package dev.sandytang.flashdeal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VoucherOrderRepository extends JpaRepository<VoucherOrder, Long> {
    boolean existsByVoucherIdAndUserId(long voucherId, long userId);
    Optional<VoucherOrder> findByIdAndUserId(long id, long userId);
}
