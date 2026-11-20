package com.beefit.api.infra.cloudflare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new R2StorageService(s3Client, s3Presigner);
        ReflectionTestUtils.setField(storageService, "bucketName", "test-bucket");
    }

    @Test
    void uploadSendsExpectedRequestToS3Client() {
        byte[] bytes = new byte[]{1, 2, 3};
        storageService.upload("media/user1/test.jpg", bytes, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertEquals("test-bucket", req.bucket());
        assertEquals("media/user1/test.jpg", req.key());
        assertEquals("image/jpeg", req.contentType());
        assertEquals("no-store", req.cacheControl());
    }

    @Test
    void generatePresignedUrlDelegatesToPresigner() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://presigned.example.com/test.jpg").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = storageService.generatePresignedUrl("media/user1/test.jpg", Duration.ofMinutes(10));
        assertEquals("https://presigned.example.com/test.jpg", url);

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        GetObjectPresignRequest presignReq = captor.getValue();
        assertEquals("test-bucket", presignReq.getObjectRequest().bucket());
        assertEquals("media/user1/test.jpg", presignReq.getObjectRequest().key());
        assertEquals(Duration.ofMinutes(10), presignReq.signatureDuration());
    }

    @Test
    void deleteCallsDeleteObject() {
        storageService.delete("media/user1/test.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("media/user1/test.jpg", captor.getValue().key());
    }

    @Test
    void getBytesReturnsByteArrayFromS3Client() {
        byte[] expected = new byte[]{4, 5, 6};
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = storageService.getBytes("media/user1/test.jpg");
        assertArrayEquals(expected, result);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("media/user1/test.jpg", captor.getValue().key());
    }
}
