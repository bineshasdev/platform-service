package com.flowbinary.platformservice.storage.impl;

import com.flowbinary.platformservice.config.StorageProperties;
import com.flowbinary.platformservice.exception.StorageException;
import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import com.flowbinary.platformservice.storage.StorageObject;
import com.flowbinary.platformservice.storage.StorageProvider;
import com.flowbinary.platformservice.storage.StorageUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Stores files in an S3-compatible object store (AWS S3, MinIO, etc.).
 *
 * <p>Required credentials in {@code TenantStorageConfig.credentials}:
 * <pre>
 * {
 *   "accessKeyId":     "AKIA...",
 *   "secretAccessKey": "...",
 *   "bucket":          "my-bucket",
 *   "region":          "us-east-1",
 *   "prefix":          "tenant-slug/",  // optional
 *   "endpoint":        "https://..."    // optional: MinIO / custom endpoint
 * }
 * </pre>
 *
 * <p>Object keys follow the pattern: {@code <prefix><fileType>/<uuid>_<sanitizedName>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3StorageProvider implements StorageProvider {

    private final StorageProperties storageProperties;

    @Override
    public StorageBackend backend() {
        return StorageBackend.S3;
    }

    @Override
    public StorageObject store(StorageUploadRequest request) {
        S3Credentials creds  = S3Credentials.from(request.getConfig().getCredentials());
        String        key    = buildKey(creds, request);

        try (S3Client s3 = buildClient(creds)) {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(creds.bucket())
                    .key(key)
                    .contentType(request.getContentType())
                    .contentLength(request.getSizeBytes())
                    .build();

            s3.putObject(putReq, RequestBody.fromInputStream(request.getInputStream(), request.getSizeBytes()));
            log.debug("S3: stored {} bytes → s3://{}/{}", request.getSizeBytes(), creds.bucket(), key);
        } catch (Exception ex) {
            throw new StorageException("Failed to upload to S3: " + ex.getMessage(), ex);
        }

        String publicUrl = storageProperties.isS3PublicBucket()
                ? buildPublicUrl(creds, key)
                : null;

        return StorageObject.builder()
                .storageKey(key)
                .publicUrl(publicUrl)
                .build();
    }

    @Override
    public StorageObject retrieve(TenantStorageConfig config, String storageKey,
                                  String originalName, String contentType) {
        S3Credentials creds = S3Credentials.from(config.getCredentials());
        try (S3Client s3 = buildClient(creds)) {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(creds.bucket())
                    .key(storageKey)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3.getObject(getReq);
            return StorageObject.builder()
                    .storageKey(storageKey)
                    .data(response)
                    .contentType(response.response().contentType())
                    .originalName(originalName)
                    .build();
        } catch (NoSuchKeyException ex) {
            throw new StorageException("Object not found in S3: " + storageKey, ex);
        } catch (Exception ex) {
            throw new StorageException("Failed to retrieve from S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(TenantStorageConfig config, String storageKey) {
        S3Credentials creds = S3Credentials.from(config.getCredentials());
        try (S3Client s3 = buildClient(creds)) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(creds.bucket())
                    .key(storageKey)
                    .build());
            log.debug("S3: deleted s3://{}/{}", creds.bucket(), storageKey);
        } catch (Exception ex) {
            throw new StorageException("Failed to delete from S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String presignedUrl(TenantStorageConfig config, String storageKey, Duration ttl) {
        S3Credentials creds = S3Credentials.from(config.getCredentials());
        try (S3Presigner presigner = buildPresigner(creds)) {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(creds.bucket())
                            .key(storageKey)
                            .build())
                    .build();
            return presigner.presignGetObject(req).url().toString();
        } catch (Exception ex) {
            throw new StorageException("Failed to generate pre-signed URL: " + ex.getMessage(), ex);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String buildKey(S3Credentials creds, StorageUploadRequest request) {
        String prefix = creds.prefix() != null ? creds.prefix() : "";
        String name   = sanitize(request.getOriginalName());
        return prefix + request.getFileType().name().toLowerCase()
                + "/" + UUID.randomUUID() + "_" + name;
    }

    private static String buildPublicUrl(S3Credentials creds, String key) {
        if (creds.endpoint() != null) {
            return creds.endpoint() + "/" + creds.bucket() + "/" + key;
        }
        return "https://" + creds.bucket() + ".s3." + creds.region() + ".amazonaws.com/" + key;
    }

    private static S3Client buildClient(S3Credentials creds) {
        var builder = S3Client.builder()
                .region(Region.of(creds.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(creds.accessKeyId(), creds.secretAccessKey())));
        if (creds.endpoint() != null) {
            builder.endpointOverride(URI.create(creds.endpoint()))
                   .forcePathStyle(true);
        }
        return builder.build();
    }

    private static S3Presigner buildPresigner(S3Credentials creds) {
        var builder = S3Presigner.builder()
                .region(Region.of(creds.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(creds.accessKeyId(), creds.secretAccessKey())));
        if (creds.endpoint() != null) {
            builder.endpointOverride(URI.create(creds.endpoint()));
        }
        return builder.build();
    }

    private static String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    // ── credential record ─────────────────────────────────────────────────────

    private record S3Credentials(
            String accessKeyId,
            String secretAccessKey,
            String bucket,
            String region,
            String prefix,
            String endpoint) {

        static S3Credentials from(Map<String, Object> map) {
            String accessKeyId     = require(map, "accessKeyId");
            String secretAccessKey = require(map, "secretAccessKey");
            String bucket          = require(map, "bucket");
            String region          = require(map, "region");
            String prefix          = (String) map.get("prefix");
            String endpoint        = (String) map.get("endpoint");
            return new S3Credentials(accessKeyId, secretAccessKey, bucket, region, prefix, endpoint);
        }

        private static String require(Map<String, Object> map, String key) {
            Object val = map.get(key);
            if (val == null) {
                throw new StorageException("S3 credential missing required field: " + key);
            }
            return val.toString();
        }
    }
}
