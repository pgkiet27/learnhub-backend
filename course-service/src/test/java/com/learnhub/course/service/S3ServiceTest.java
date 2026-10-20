package com.learnhub.course.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3Service Unit Tests")
class S3ServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3Service, "bucketName", "learnhub-media-123");
        ReflectionTestUtils.setField(s3Service, "region", "ap-southeast-1");
    }

    @Test
    @DisplayName("No CloudFront domain → direct S3 URL")
    void buildCdnUrl_WithoutCloudFront_ReturnsS3Url() {
        ReflectionTestUtils.setField(s3Service, "cdnDomain", "");

        assertThat(s3Service.buildCdnUrl("thumbnails/c1/a.png"))
                .isEqualTo("https://learnhub-media-123.s3.ap-southeast-1.amazonaws.com/thumbnails/c1/a.png");
    }

    @Test
    @DisplayName("CloudFront domain without scheme → https CloudFront URL")
    void buildCdnUrl_WithBareDomain_ReturnsCloudFrontUrl() {
        ReflectionTestUtils.setField(s3Service, "cdnDomain", "d123.cloudfront.net");

        assertThat(s3Service.buildCdnUrl("thumbnails/c1/a.png"))
                .isEqualTo("https://d123.cloudfront.net/thumbnails/c1/a.png");
    }

    @Test
    @DisplayName("CloudFront domain with scheme and trailing slash → no double slash")
    void buildCdnUrl_WithSchemeAndTrailingSlash_ReturnsCleanUrl() {
        ReflectionTestUtils.setField(s3Service, "cdnDomain", "https://d123.cloudfront.net/");

        assertThat(s3Service.buildCdnUrl("thumbnails/c1/a.png"))
                .isEqualTo("https://d123.cloudfront.net/thumbnails/c1/a.png");
    }

    @Test
    @DisplayName("keyFromUrl recognises CloudFront and direct S3 URLs, ignores external ones")
    void keyFromUrl_OwnAndExternalUrls() {
        ReflectionTestUtils.setField(s3Service, "cdnDomain", "https://d123.cloudfront.net");

        assertThat(s3Service.keyFromUrl("https://d123.cloudfront.net/thumbnails/c1/a.png"))
                .contains("thumbnails/c1/a.png");
        assertThat(s3Service.keyFromUrl("https://learnhub-media-123.s3.ap-southeast-1.amazonaws.com/thumbnails/c1/a.png"))
                .contains("thumbnails/c1/a.png");
        assertThat(s3Service.keyFromUrl("https://images.unsplash.com/photo-1")).isEmpty();
        assertThat(s3Service.keyFromUrl("https://d123.cloudfront.net/")).isEmpty();
        assertThat(s3Service.keyFromUrl(null)).isEmpty();
    }
}
