package com.learnhub.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service that handles uploading/deleting files on AWS S3.
 * <p>
 * Video upload flow (Presigned URL):
 * 1. Frontend requests a presigned URL from the Backend
 * 2. Backend generates a presigned URL (15-minute TTL) and returns it to the Frontend
 * 3. Frontend uploads the file directly to S3 using the presigned URL
 * 4. Frontend notifies the Backend once the upload is done → Backend saves the S3 key to the DB
 * <p>
 * Benefits:
 * - File does NOT pass through the Backend server → Backend is not overloaded
 * - Saves bandwidth and cost
 * - Faster upload (direct to S3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.cloudfront.domain:}")
    private String cdnDomain;

    /**
     * Generates a presigned URL for the Frontend to upload a file to S3.
     *
     * @param folder      Folder in S3 (e.g.: "videos", "thumbnails", "documents")
     * @param fileName    Original file name (e.g.: "lesson-1.mp4")
     * @param contentType MIME type (e.g.: "video/mp4", "image/jpeg")
     * @return PresignedUploadResponse containing uploadUrl and s3Key
     */
    public PresignedUploadResponse generatePresignedUploadUrl(
            String folder, String fileName, String contentType) {

        // Generate a unique key to avoid conflicts
        String uniqueKey = folder + "/" + UUID.randomUUID() + "_" + sanitizeFileName(fileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(uniqueKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpiry))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String cdnUrl = buildCdnUrl(uniqueKey);

        log.info("Generated presigned URL for key: {}", uniqueKey);

        return PresignedUploadResponse.builder()
                .uploadUrl(presignedRequest.url().toString())
                .s3Key(uniqueKey)
                .cdnUrl(cdnUrl)
                .expiresInSeconds(presignedUrlExpiry)
                .build();
    }

    /**
     * Deletes a file from S3.
     */
    public void deleteFile(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) return;

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        s3Client.deleteObject(deleteRequest);
        log.info("Deleted S3 file: {}", s3Key);
    }

    /**
     * Builds the public URL of a file: through CloudFront when configured (the bucket is private),
     * otherwise the direct S3 URL (local development).
     */
    public String buildCdnUrl(String s3Key) {
        return (hasCdn() ? cdnBaseUrl() : s3BaseUrl()) + s3Key;
    }

    /**
     * Returns the S3 key behind one of our file URLs (CloudFront or direct S3); empty for external
     * URLs (e.g. seed images on Unsplash), so those are never treated as files in our bucket.
     */
    public Optional<String> keyFromUrl(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        for (String base : hasCdn() ? List.of(cdnBaseUrl(), s3BaseUrl()) : List.of(s3BaseUrl())) {
            if (url.startsWith(base) && url.length() > base.length()) {
                return Optional.of(url.substring(base.length()));
            }
        }
        return Optional.empty();
    }

    private boolean hasCdn() {
        return cdnDomain != null && !cdnDomain.isBlank();
    }

    private String cdnBaseUrl() {
        String base = cdnDomain.startsWith("http") ? cdnDomain : "https://" + cdnDomain;
        return base.replaceAll("/+$", "") + "/";
    }

    private String s3BaseUrl() {
        return String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
    }

    // Remove special characters from the file name
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // Inner Response Class
    @lombok.Data
    @lombok.Builder
    public static class PresignedUploadResponse {
        private String uploadUrl;       // URL for the Frontend to PUT the file to
        private String s3Key;           // Key in S3 (saved to DB)
        private String cdnUrl;          // Public URL after upload completes
        private long expiresInSeconds;
    }
}