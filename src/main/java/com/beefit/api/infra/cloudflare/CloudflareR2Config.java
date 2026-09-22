package com.beefit.api.infra.cloudflare;
// import required packages for presigning
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;

@Configuration
public class CloudflareR2Config {

    private static final String DEFAULT_DUMMY_ENDPOINT = "https://dummy.r2.cloudflarestorage.com";
    private static final String DEFAULT_DUMMY_CREDENTIAL = "dummy-credential";

    @Value("${cloudflare.r2.account-id:dummy-account}")
    private String accountId;

    @Value("${cloudflare.r2.access-key-id:" + DEFAULT_DUMMY_CREDENTIAL + "}")
    private String accessKey;

    @Value("${cloudflare.r2.secret-access-key:" + DEFAULT_DUMMY_CREDENTIAL + "}")
    private String secretKey;

    @Value("${cloudflare.r2.endpoint:" + DEFAULT_DUMMY_ENDPOINT + "}")
    private String endpoint;

    private URI resolveEndpointUri() {
        if (endpoint == null || endpoint.isBlank() || !endpoint.contains("://")) {
            return URI.create(DEFAULT_DUMMY_ENDPOINT);
        }
        try {
            URI uri = URI.create(endpoint);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return URI.create(DEFAULT_DUMMY_ENDPOINT);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return URI.create(DEFAULT_DUMMY_ENDPOINT);
        }
    }

    private StaticCredentialsProvider getCredentialsProvider() {
        String key = (accessKey == null || accessKey.isBlank()) ? DEFAULT_DUMMY_CREDENTIAL : accessKey;
        String secret = (secretKey == null || secretKey.isBlank()) ? DEFAULT_DUMMY_CREDENTIAL : secretKey;
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(key, secret)
        );
    }

    @Bean
    public S3Client s3Client() {
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        return S3Client.builder()
                .endpointOverride(resolveEndpointUri())
                .credentialsProvider(getCredentialsProvider())
                .region(Region.of("auto"))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(resolveEndpointUri())
                .credentialsProvider(getCredentialsProvider())
                .region(Region.of("auto"))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

}