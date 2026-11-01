package com.learnhub.payment.client;

import com.learnhub.payment.dto.response.CouponValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CourseServiceClient coupon calls")
class CourseServiceClientTest {

    private MockRestServiceServer server;
    private CourseServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://course");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CourseServiceClient(builder.build());
    }

    @Test
    @DisplayName("validateCoupon reads the fields the Course Service returns")
    void validateCoupon_ShouldParseResponse() {
        UUID courseId = UUID.randomUUID();
        server.expect(requestTo("http://course/api/v1/internal/coupons/validate?code=SAVE15&courseId="
                        + courseId + "&originalPrice=49.99"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success": true, "message": "OK", "data": {
                          "couponCode": "SAVE15", "discountType": "percent", "discountValue": 15,
                          "discountAmount": 7.50, "originalPrice": 49.99, "finalPrice": 42.49,
                          "message": "Áp dụng coupon thành công", "isValid": true}}
                        """, MediaType.APPLICATION_JSON));

        CouponValidationResponse coupon = client.validateCoupon("SAVE15", courseId, new BigDecimal("49.99"));

        assertThat(coupon.isValid()).isTrue();
        assertThat(coupon.couponCode()).isEqualTo("SAVE15");
        assertThat(coupon.discountAmount()).isEqualByComparingTo("7.50");
        assertThat(coupon.finalPrice()).isEqualByComparingTo("42.49");
        server.verify();
    }

    @Test
    @DisplayName("markCouponUsed posts to the internal endpoint")
    void markCouponUsed_ShouldPost() {
        server.expect(requestTo("http://course/api/v1/internal/coupons/SAVE15/use"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.markCouponUsed("SAVE15");

        server.verify();
    }
}
