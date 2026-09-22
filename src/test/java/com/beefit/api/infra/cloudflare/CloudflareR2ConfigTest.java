package com.beefit.api.infra.cloudflare;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CloudflareR2ConfigTest {

    @Test
    void instantiatesBeansWithDefaultFallbacksWhenPropertiesAreNullOrEmpty() {
        CloudflareR2Config config = new CloudflareR2Config();
        ReflectionTestUtils.setField(config, "endpoint", "");
        ReflectionTestUtils.setField(config, "accessKey", "");
        ReflectionTestUtils.setField(config, "secretKey", "");
        ReflectionTestUtils.setField(config, "accountId", "");

        try (S3Client client = config.s3Client();
             S3Presigner presigner = config.s3Presigner()) {
            assertNotNull(client);
            assertNotNull(presigner);
        }
    }

    @Test
    void instantiatesBeansWhenEndpointHasNoScheme() {
        CloudflareR2Config config = new CloudflareR2Config();
        ReflectionTestUtils.setField(config, "endpoint", "invalid-endpoint-without-scheme");
        ReflectionTestUtils.setField(config, "accessKey", "my-key");
        ReflectionTestUtils.setField(config, "secretKey", "my-secret");

        try (S3Client client = config.s3Client();
             S3Presigner presigner = config.s3Presigner()) {
            assertNotNull(client);
            assertNotNull(presigner);
        }
    }

    @Test
    void instantiatesBeansWithValidCustomEndpoint() {
        CloudflareR2Config config = new CloudflareR2Config();
        ReflectionTestUtils.setField(config, "endpoint", "https://custom-account.r2.cloudflarestorage.com");
        ReflectionTestUtils.setField(config, "accessKey", "custom-key");
        ReflectionTestUtils.setField(config, "secretKey", "custom-secret");

        try (S3Client client = config.s3Client();
             S3Presigner presigner = config.s3Presigner()) {
            assertNotNull(client);
            assertNotNull(presigner);
        }
    }
}
