package dev.sandytang.flashdeal.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SeckillVoucherRepository extends JpaRepository<SeckillVoucher, Long> {
    @Modifying
    @Query("update SeckillVoucher v set v.stock = v.stock - 1 where v.id = :id and v.stock > 0")
    int decrementIfAvailable(@Param("id") long voucherId);
}
