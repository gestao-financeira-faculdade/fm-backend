package com.emanudev.gestaofinanceira.entry.adapter.out.storage;

import com.emanudev.gestaofinanceira.entry.application.AttachmentStoragePort;
import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3AttachmentStorageAdapter implements AttachmentStoragePort {

    private final S3Client s3Client;
    private final String bucketName;
    private final boolean autoCreateBucket;
    private final AtomicBoolean bucketVerified = new AtomicBoolean(false);

    @Autowired
    public S3AttachmentStorageAdapter(S3Client s3Client, ApplicationProperties applicationProperties) {
        this(
                s3Client,
                Objects.requireNonNull(applicationProperties, "applicationProperties must not be null").getS3Bucket(),
                applicationProperties.isS3AutoCreateBucket()
        );
    }

    public S3AttachmentStorageAdapter(S3Client s3Client, String bucketName, boolean autoCreateBucket) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
        this.autoCreateBucket = autoCreateBucket;
    }

    @Override
    public String store(UUID entryId, UUID attachmentId, String sanitizedFileName, byte[] content) {
        String storageKey = validateStorageKey(entryId + "/" + attachmentId + "_" + sanitizedFileName);
        ensureBucketExistsIfConfigured();

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return storageKey;
        } catch (S3Exception ex) {
            throw new IllegalStateException("Failed to store fiscal attachment in S3: " + storageKey, ex);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        String validKey = validateStorageKey(storageKey);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(validKey)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (NoSuchKeyException | NoSuchBucketException ex) {
            throw new ResourceNotFoundException("FiscalAttachmentFile", validKey);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResourceNotFoundException("FiscalAttachmentFile", validKey);
            }
            throw new IllegalStateException("Failed to read fiscal attachment from S3: " + validKey, ex);
        }
    }

    private void ensureBucketExistsIfConfigured() {
        if (!autoCreateBucket || bucketVerified.get()) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            bucketVerified.set(true);
        } catch (NoSuchBucketException ex) {
            createBucketIdempotently();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                createBucketIdempotently();
            } else {
                throw new IllegalStateException("Failed to verify S3 bucket existence: " + bucketName, ex);
            }
        }
    }

    private void createBucketIdempotently() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            bucketVerified.set(true);
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ex) {
            bucketVerified.set(true);
        } catch (S3Exception ex) {
            throw new IllegalStateException("Failed to auto-create S3 bucket: " + bucketName, ex);
        }
    }

    private static String validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_STORAGE_KEY", "Storage key must not be blank");
        }
        String normalized = storageKey.trim().replace('\\', '/');
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw new BusinessRuleViolationException("INVALID_STORAGE_KEY", "Path traversal attempt detected in storage key");
        }
        return normalized;
    }
}
