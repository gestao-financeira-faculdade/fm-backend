package com.emanudev.gestaofinanceira.entry.adapter.out.storage;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3AttachmentStorageAdapterTest {

    @Mock
    private S3Client s3Client;

    private S3AttachmentStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new S3AttachmentStorageAdapter(s3Client, "fiscal-attachments", true);
    }

    @Test
    @DisplayName("store should auto-create bucket when missing in MiniStack/S3 and upload object bytes")
    void shouldStoreObjectAndAutoCreateBucketWhenMissing() {
        UUID entryId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        byte[] content = "%PDF-1.7 fiscal invoice".getBytes(StandardCharsets.UTF_8);

        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        String storageKey = adapter.store(entryId, attachmentId, "nota_fiscal.pdf", content);

        assertThat(storageKey).isEqualTo(entryId + "/" + attachmentId + "_nota_fiscal.pdf");

        ArgumentCaptor<CreateBucketRequest> createBucketCaptor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(createBucketCaptor.capture());
        assertThat(createBucketCaptor.getValue().bucket()).isEqualTo("fiscal-attachments");

        ArgumentCaptor<PutObjectRequest> putObjectCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putObjectCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectCaptor.getValue().bucket()).isEqualTo("fiscal-attachments");
        assertThat(putObjectCaptor.getValue().key()).isEqualTo(storageKey);
    }

    @Test
    @DisplayName("load should return object bytes from S3 when key exists")
    void shouldLoadObjectBytesWhenKeyExists() {
        String key = UUID.randomUUID() + "/" + UUID.randomUUID() + "_invoice.pdf";
        byte[] expectedBytes = "%PDF-1.7 sample".getBytes(StandardCharsets.UTF_8);

        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength((long) expectedBytes.length).build(),
                expectedBytes
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] loaded = adapter.load(key);

        assertThat(loaded).isEqualTo(expectedBytes);
    }

    @Test
    @DisplayName("load should throw ResourceNotFoundException when key does not exist in S3")
    void shouldThrowResourceNotFoundWhenKeyMissing() {
        String key = UUID.randomUUID() + "/" + UUID.randomUUID() + "_missing.pdf";
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("No such key").build());

        assertThatThrownBy(() -> adapter.load(key))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("load should reject blank or path traversal storage keys")
    void shouldRejectInvalidStorageKeys() {
        assertThatThrownBy(() -> adapter.load("   "))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThatThrownBy(() -> adapter.load("../etc/passwd"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThatThrownBy(() -> adapter.store(UUID.randomUUID(), UUID.randomUUID(), "../secret.pdf", new byte[]{1}))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
