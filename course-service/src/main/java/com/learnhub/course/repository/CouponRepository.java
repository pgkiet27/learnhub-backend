package com.learnhub.course.repository;

import com.learnhub.course.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    // Find a valid coupon: active + not expired + not fully used
    @Query("""
            SELECT c FROM Coupon c
            WHERE c.code = :code
              AND c.isActive = true
              AND (c.expiresAt IS NULL OR c.expiresAt > :now)
              AND (c.maxUses IS NULL OR c.usedCount < c.maxUses)
              AND (c.course IS NULL OR c.course.id = :courseId)
            """)
    Optional<Coupon> findValidCoupon(String code, UUID courseId, Instant now);
}