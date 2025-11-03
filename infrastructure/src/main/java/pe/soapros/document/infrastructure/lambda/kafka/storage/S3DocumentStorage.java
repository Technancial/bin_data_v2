package pe.soapros.document.infrastructure.lambda.kafka.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Service for storing generated documents in AWS S3.
 * Organizes documents by date and generates unique keys.
 */
@ApplicationScoped
@JBossLog
public class S3DocumentStorage {

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "aws.s3.bucket.documents", defaultValue = "my-documents-bucket")
    String documentsBucket;

    @ConfigProperty(name = "aws.s3.prefix.documents", defaultValue = "generated-documents")
    String documentsPrefix;

    /**
     * Saves a PDF document to S3 with metadata.
     *
     * @param pdfBytes the PDF document bytes
     * @param templatePath the template path used to generate the document
     * @param metadata additional metadata to store with the document
     * @return the S3 key where the document was stored
     */
    public String saveDocument(byte[] pdfBytes, String templatePath, Map<String, String> metadata) {
        // Generar key único con estructura: prefix/yyyy/MM/dd/uuid.pdf
        String s3Key = generateS3Key(templatePath);

        log.infof("Saving document to S3: s3://%s/%s (%d bytes)",
                 documentsBucket, s3Key, pdfBytes.length);

        try {
            // Construir el request con metadata
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(documentsBucket)
                    .key(s3Key)
                    .contentType("application/pdf")
                    .contentLength((long) pdfBytes.length);

            // Agregar metadata si existe
            if (metadata != null && !metadata.isEmpty()) {
                requestBuilder.metadata(metadata);
            }

            // Subir a S3
            s3Client.putObject(
                    requestBuilder.build(),
                    RequestBody.fromBytes(pdfBytes)
            );

            log.infof("Document successfully saved to S3: s3://%s/%s", documentsBucket, s3Key);

            return s3Key;

        } catch (Exception e) {
            log.errorf(e, "Failed to save document to S3: s3://%s/%s", documentsBucket, s3Key);
            throw new RuntimeException("Failed to save document to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a unique S3 key for the document.
     * Format: {prefix}/{year}/{month}/{day}/{template-name}-{uuid}.pdf
     *
     * Example: generated-documents/2025/11/03/plantilla-550e8400-e29b-41d4-a716-446655440000.pdf
     *
     * @param templatePath the template path
     * @return the generated S3 key
     */
    private String generateS3Key(String templatePath) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());

        // Extraer nombre del template sin extensión
        String templateName = extractTemplateName(templatePath);

        // Generar UUID único
        String uuid = UUID.randomUUID().toString();

        // Construir key: prefix/yyyy/MM/dd/template-uuid.pdf
        return String.format("%s/%s/%s/%s/%s-%s.pdf",
                documentsPrefix, year, month, day, templateName, uuid);
    }

    /**
     * Extracts template name from path, removing extension.
     *
     * @param templatePath the full template path
     * @return the template name without extension
     */
    private String extractTemplateName(String templatePath) {
        // Obtener solo el nombre del archivo
        String fileName = templatePath;
        if (templatePath.contains("/")) {
            fileName = templatePath.substring(templatePath.lastIndexOf("/") + 1);
        }

        // Remover extensión
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }

        return fileName;
    }

    /**
     * Gets the S3 URI for a given key.
     *
     * @param s3Key the S3 key
     * @return the full S3 URI (s3://bucket/key)
     */
    public String getS3Uri(String s3Key) {
        return String.format("s3://%s/%s", documentsBucket, s3Key);
    }

    /**
     * Gets the HTTPS URL for a given key.
     *
     * @param s3Key the S3 key
     * @param region the AWS region
     * @return the HTTPS URL
     */
    public String getHttpsUrl(String s3Key, String region) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                documentsBucket, region, s3Key);
    }
}
