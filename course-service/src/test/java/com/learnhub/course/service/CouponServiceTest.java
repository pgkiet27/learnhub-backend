package com.learnhub.course.service;

import com.learnhub.course.dto.response.CouponValidationResponse;
import com.learnhub.course.entity.Coupon;
import com.learnhub.course.repository.CouponRepository;
import com.learnhub.course.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService Unit Tests")
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CouponService couponService;

    private UUID courseId;
    private Coupon percentCoupon;
    private Coupon fixedCoupon;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();

        // 30% discount coupon
        percentCoupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("SAVE30")
                .discountType(Coupon.DiscountType.percent)
                .discountValue(BigDecimal.valueOf(30))
                .minOrderValue(BigDecimal.valueOf(100000))
                .maxUses(100)
                .usedCount(0)
                .isActive(true)
                .createdBy(UUID.randomUUID())
                .build();

        // 50,000 VND fixed discount
        fixedCoupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("FLAT50K")
                .discountType(Coupon.DiscountType.fixed)
                .discountValue(BigDecimal.valueOf(50000))
                .minOrderValue(BigDecimal.ZERO)
                .isActive(true)
                .createdBy(UUID.randomUUID())
                .build();
    }

    @Nested
    @DisplayName("validateCoupon()")
    class ValidateCouponTests {

        @Test
        @DisplayName("Percent coupon — giảm 30% trên 500,000đ → 350,000đ")
        void validateCoupon_PercentCoupon_ShouldCalculateCorrectly() {
            // Given
            BigDecimal originalPrice = BigDecimal.valueOf(500000);
            given(couponRepository.findValidCoupon(eq("SAVE30"), eq(courseId), any()))
                    .willReturn(Optional.of(percentCoupon));

            // When
            CouponValidationResponse response =
                    couponService.validateCoupon("SAVE30", courseId, originalPrice);

            // Then
            assertThat(response.isValid()).isTrue();
            assertThat(response.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(150000)); // 30% of 500,000
            assertThat(response.getFinalPrice())
                    .isEqualByComparingTo(BigDecimal.valueOf(350000)); // 500,000 - 150,000
        }

        @Test
        @DisplayName("Fixed coupon — giảm 50,000đ trên 500,000đ → 450,000đ")
        void validateCoupon_FixedCoupon_ShouldCalculateCorrectly() {
            // Given
            BigDecimal originalPrice = BigDecimal.valueOf(500000);
            given(couponRepository.findValidCoupon(eq("FLAT50K"), eq(courseId), any()))
                    .willReturn(Optional.of(fixedCoupon));

            // When
            CouponValidationResponse response =
                    couponService.validateCoupon("FLAT50K", courseId, originalPrice);

            // Then
            assertThat(response.isValid()).isTrue();
            assertThat(response.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(response.getFinalPrice())
                    .isEqualByComparingTo(BigDecimal.valueOf(450000));
        }

        @Test
        @DisplayName("Fixed coupon lớn hơn giá gốc — finalPrice = 0")
        void validateCoupon_DiscountLargerThanPrice_FinalPriceShouldBeZero() {
            // Given
            Coupon bigDiscountCoupon = Coupon.builder()
                    .id(UUID.randomUUID())
                    .code("BIGDEAL")
                    .discountType(Coupon.DiscountType.fixed)
                    .discountValue(BigDecimal.valueOf(1000000)) // Discount 1 million
                    .minOrderValue(BigDecimal.ZERO)
                    .isActive(true)
                    .createdBy(UUID.randomUUID())
                    .build();

            given(couponRepository.findValidCoupon(eq("BIGDEAL"), eq(courseId), any()))
                    .willReturn(Optional.of(bigDiscountCoupon));

            // When
            CouponValidationResponse response =
                    couponService.validateCoupon("BIGDEAL", courseId,
                            BigDecimal.valueOf(100000)); // Price is only 100k

            // Then
            assertThat(response.isValid()).isTrue();
            assertThat(response.getFinalPrice())
                    .isEqualByComparingTo(BigDecimal.ZERO); // Not negative
        }

        @Test
        @DisplayName("Coupon không hợp lệ — isValid = false")
        void validateCoupon_InvalidCoupon_ShouldReturnInvalidResponse() {
            // Given
            given(couponRepository.findValidCoupon(anyString(), any(), any()))
                    .willReturn(Optional.empty());

            // When
            CouponValidationResponse response =
                    couponService.validateCoupon("INVALID", courseId, BigDecimal.valueOf(100000));

            // Then
            assertThat(response.isValid()).isFalse();
            assertThat(response.getMessage()).contains("không hợp lệ");
        }

        @Test
        @DisplayName("Coupon hợp lệ nhưng giá trị đơn hàng dưới min — isValid = false")
        void validateCoupon_BelowMinOrderValue_ShouldReturnInvalid() {
            // Given — percentCoupon requires a min order value of 100,000đ
            given(couponRepository.findValidCoupon(eq("SAVE30"), eq(courseId), any()))
                    .willReturn(Optional.of(percentCoupon));

            // When — price is only 50,000đ (below min)
            CouponValidationResponse response =
                    couponService.validateCoupon("SAVE30", courseId, BigDecimal.valueOf(50000));

            // Then
            assertThat(response.isValid()).isFalse();
            assertThat(response.getMessage()).contains("tối thiểu");
        }
    }

    @Nested
    @DisplayName("incrementUsedCount()")
    class IncrementUsedCountTests {

        @Test
        @DisplayName("Tăng used_count thành công")
        void incrementUsedCount_ShouldIncrease() {
            // Given
            given(couponRepository.findByCode("SAVE30"))
                    .willReturn(Optional.of(percentCoupon));
            given(couponRepository.save(any())).willReturn(percentCoupon);

            // When
            couponService.incrementUsedCount("SAVE30");

            // Then
            assertThat(percentCoupon.getUsedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Đạt max_uses — tự động deactivate")
        void incrementUsedCount_ReachMaxUses_ShouldDeactivate() {
            // Given — coupon has 1 use left
            percentCoupon.setUsedCount(99);   // maxUses = 100
            given(couponRepository.findByCode("SAVE30"))
                    .willReturn(Optional.of(percentCoupon));
            given(couponRepository.save(any())).willReturn(percentCoupon);

            // When
            couponService.incrementUsedCount("SAVE30");

            // Then
            assertThat(percentCoupon.getUsedCount()).isEqualTo(100);
            assertThat(percentCoupon.isActive()).isFalse(); // Deactivated
        }
    }
}