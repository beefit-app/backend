package com.beefit.api.infra.cloudflare;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class R2StorageService {

    private static final Duration DEFAULT_PRESIGNED_DURATION = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    /**
     * Faz o upload dos bytes da mídia diretamente para o bucket Cloudflare R2.
     *
     * @param storageKey  identificador/caminho único do objeto no bucket
     * @param bytes       conteúdo binário do arquivo
     * @param contentType tipo MIME da imagem (ex: image/jpeg, image/png)
     */
    public void upload(String storageKey, byte[] bytes, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
    }

    /**
     * Gera uma URL pré-assinada para acesso temporário e direto ao arquivo no R2.
     *
     * @param storageKey identificador/caminho único do objeto no bucket
     * @param duration   tempo de validade da assinatura
     * @return URL pré-assinada temporária
     */
    public String generatePresignedUrl(String storageKey, Duration duration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Sobrecarga conveniente que gera a URL pré-assinada com validade padrão de 15 minutos.
     *
     * @param storageKey identificador/caminho único do objeto no bucket
     * @return URL pré-assinada temporária
     */
    public String generatePresignedUrl(String storageKey) {
        return generatePresignedUrl(storageKey, DEFAULT_PRESIGNED_DURATION);
    }

    /**
     * Exclui o arquivo do bucket Cloudflare R2.
     *
     * @param storageKey identificador/caminho único do objeto no bucket
     */
    public void delete(String storageKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * Recupera os bytes brutos do arquivo diretamente do R2 (caso necessário em testes ou tarefas internas).
     *
     * @param storageKey identificador/caminho único do objeto no bucket
     * @return array de bytes do arquivo
     */
    public byte[] getBytes(String storageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
    }
}
