package pe.soapros.document.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * CDI Producer for AWS SDK clients.
 * Configures S3 client with support for LocalStack in development.
 */
@ApplicationScoped
@JBossLog
public class AwsClientProducer {

    @ConfigProperty(name = "aws.region")
    String awsRegion;

    @ConfigProperty(name = "aws.s3.endpoint")
    Optional<String> s3Endpoint;

    /**
     * Produces an S3 client for document storage.
     * In development mode, uses LocalStack endpoint.
     * In production, uses default AWS credentials and endpoint.
     *
     * @return configured S3 client
     */
    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        Region region = Region.of(awsRegion);
        S3ClientBuilder builder = S3Client.builder().region(region);

        // Si hay endpoint configurado (LocalStack en dev), usarlo
        if (s3Endpoint.isPresent() && !s3Endpoint.get().isEmpty()) {
            String endpoint = s3Endpoint.get();
            log.infof("Creating S3 client for LocalStack at: %s", endpoint);

            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(
                       StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")
                       )
                   )
                   // Necesario para LocalStack
                   .forcePathStyle(true);
        } else {
            // Producción: usar credenciales por defecto
            log.infof("Creating S3 client for AWS region: %s", awsRegion);
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
