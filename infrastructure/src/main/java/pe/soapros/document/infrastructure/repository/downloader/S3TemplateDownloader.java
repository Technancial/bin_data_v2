package pe.soapros.document.infrastructure.repository.downloader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Downloads templates from AWS S3.
 * Handles URIs with format: s3@host:key
 * Example: s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt
 */
@ApplicationScoped
@JBossLog
public class S3TemplateDownloader implements TemplateDownloader {

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "aws.s3.bucket.templates", defaultValue = "nexux-templates")
    String templatesBucket;

    @Override
    public String getProtocol() {
        return "s3@";
    }

    @Override
    public void download(String uri, File targetFile) throws Exception {
        log.infof("Downloading from S3: %s", uri);

        // Parse S3 URI
        S3UriInfo uriInfo = parseS3Uri(uri);
        log.debugf("Parsed S3 URI - bucket: %s, key: %s", uriInfo.bucket, uriInfo.key);

        // Download from S3
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(uriInfo.bucket)
                .key(uriInfo.key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
             FileOutputStream fos = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = s3Object.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            log.infof("Downloaded %d bytes from S3 to %s", totalBytes, targetFile.getName());
        } catch (IOException e) {
            log.errorf(e, "Failed to download from S3: %s", uri);
            throw e;
        }
    }

    /**
     * Parses an S3 URI.
     * Format: s3@host:path/to/key
     */
    private S3UriInfo parseS3Uri(String s3Uri) {
        // Remove "s3@" prefix
        String withoutProtocol = s3Uri.substring(3);

        // Find the colon that separates host from key
        int colonIndex = withoutProtocol.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid S3 URI format: " + s3Uri);
        }

        // Extract parts
        String host = withoutProtocol.substring(0, colonIndex);
        String key = withoutProtocol.substring(colonIndex + 1);

        // Use configured bucket
        return new S3UriInfo(templatesBucket, key, host);
    }

    private static class S3UriInfo {
        final String bucket;
        final String key;
        final String host;

        S3UriInfo(String bucket, String key, String host) {
            this.bucket = bucket;
            this.key = key;
            this.host = host;
        }
    }
}
