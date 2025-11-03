package pe.soapros.document.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.extern.jbosslog.JBossLog;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * CDI Producer for AWS SDK clients.
 * Configures S3 client with default credentials and region.
 */
@ApplicationScoped
@JBossLog
public class AwsClientProducer {

    /**
     * Produces an S3 client for document storage.
     * Uses default AWS credentials chain (environment variables, IAM role, etc.)
     * and region from AWS_REGION environment variable or us-east-1 as default.
     *
     * @return configured S3 client
     */
    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        String regionName = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        Region region = Region.of(regionName);

        log.infof("Creating S3 client for region: %s", regionName);

        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
