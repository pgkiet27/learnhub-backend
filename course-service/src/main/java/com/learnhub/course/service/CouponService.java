package com.learnhub.course.service;

import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateCouponRequest;
import com.learnhub.course.dto.response.CouponResponse;
import com.learnhub.course.dto.response.CouponValidationResponse;
import com.learnhub.course.entity.Coupon;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CouponRepository;
import com.learnhub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service that manages coupons.
 * <p>
 * Coupons are used in the Payment Service:
 * 1. User enters a coupon code at checkout
 * 2. Payment Service calls GET /coupons/validate?code={code}&courseId={courseId}
 * 3. Course Service validates it and returns the discount amount
 * 4. Payment Service applies the discount to the transaction
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CourseRepository courseRepository;

    /**
     * Instructor creates a coupon for their own course.
     */
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request, UUID instructorId) {
        // Validate that the coupon code doesn't already exist
        if (couponRepository.findByCode(request.getCode().toUpperCase()).isPresent()) {
            throw new ConflictException("COUPON_CODE_EXISTS",
                    "Mã coupon đã tồn tại: " + request.getCode());
        }

        Course course = null;
        if (request.getCourseId() != null) {
            course = courseRepository.findByIdAndInstructorId(
                            request.getCourseId(), instructorId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "COURSE_NOT_FOUND",
                            "Khóa học không tồn tại hoặc bạn không có quyền"));
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase().trim())
                .discountType(Coupon.DiscountType.valueOf(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .minOrderValue(request.getMinOrderValue() != null
                        ? request.getMinOrderValue() : BigDecimal.ZERO)
                .maxUses(request.getMaxUses())
                .course(course)
                .expiresAt(request.getExpiresAt())
                .createdBy(instructorId)
                .build();

        return toCouponResponse(couponRepository.save(coupon));
    }

    /**
     * Validates a coupon before payment.
     * Called by the Payment Service.
     *
     * @return CouponValidationResponse containing:
     * - isValid: whether the coupon is valid
     * - discountAmount: amount discounted
     * - finalPrice: price after discount
     */
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(String code, UUID courseId,
                                                   BigDecimal originalPrice) {
        var couponOpt = couponRepository.findValidCoupon(
                code.toUpperCase(), courseId, Instant.now());

        if (couponOpt.isEmpty()) {
            return CouponValidationResponse.builder()
                    .isValid(false)
                    .message("Mã coupon không hợp lệ, đã hết hạn, hoặc không áp dụng cho khóa học này")
                    .build();
        }

        Coupon coupon = couponOpt.get();

        // Check the minimum order value
        if (originalPrice.compareTo(coupon.getMinOrderValue()) < 0) {
            return CouponValidationResponse.builder()
                    .isValid(false)
                    .message("Giá trị đơn hàng phải tối thiểu "
                            + coupon.getMinOrderValue() + " để áp dụng coupon này")
                    .build();
        }

        // Calculate the discount amount
        BigDecimal discountAmount;
        if (coupon.getDiscountType() == Coupon.DiscountType.percent) {
            // Percentage discount
            discountAmount = originalPrice
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));
        } else {
            // Fixed amount discount
            discountAmount = coupon.getDiscountValue()
                    .min(originalPrice); // Never discount more than the original price
        }

        BigDecimal finalPrice = originalPrice.subtract(discountAmount)
                .max(BigDecimal.ZERO);

        return CouponValidationResponse.builder()
                .isValid(true)
                .couponCode(coupon.getCode())
                .discountType(coupon.getDiscountType().name())
                .discountValue(coupon.getDiscountValue())
                .discountAmount(discountAmount)
                .originalPrice(originalPrice)
                .finalPrice(finalPrice)
                .message("Áp dụng coupon thành công")
                .build();
    }

    /**
     * Increments used_count after a successful payment.
     * Called by the Payment Service after payment succeeds.
     */
    @Transactional
    public void incrementUsedCount(String code) {
        couponRepository.findByCode(code.toUpperCase()).ifPresent(coupon -> {
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            // Automatically deactivate once fully used
            if (coupon.getMaxUses() != null
                    && coupon.getUsedCount() >= coupon.getMaxUses()) {
                coupon.setActive(false);
            }
            couponRepository.save(coupon);
        });
    }

    /**
     * Deactivate coupon.
     */
    @Transactional
    public void deactivateCoupon(UUID couponId, UUID instructorId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COUPON_NOT_FOUND", "Coupon không tồn tại"));

        if (!coupon.getCreatedBy().equals(instructorId)) {
            throw new com.learnhub.common.exception.ForbiddenException(
                    "NOT_COUPON_OWNER", "Bạn không có quyền");
        }

        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    public CouponResponse toCouponResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType().name())
                .discountValue(coupon.getDiscountValue())
                .minOrderValue(coupon.getMinOrderValue())
                .maxUses(coupon.getMaxUses())
                .usedCount(coupon.getUsedCount())
                .courseId(coupon.getCourse() != null ? coupon.getCourse().getId() : null)
                .expiresAt(coupon.getExpiresAt())
                .isActive(coupon.isActive())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}