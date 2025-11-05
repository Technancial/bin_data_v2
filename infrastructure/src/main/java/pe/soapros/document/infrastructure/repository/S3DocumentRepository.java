package pe.soapros.document.infrastructure.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.soapros.document.domain.DocumentRepository;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.util.LogSanitizer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S3-based implementation of DocumentRepository.
 * Uploads generated documents to AWS S3 with metadata for searchability.
 *
 * Format returned: s3@:bucket/prefix/yyyy/MM/dd/filename-uuid.ext
 * The ':' prefix indicates using the configured bucket.
 */
@ApplicationScoped
@JBossLog
public class S3DocumentRepository implements DocumentRepository {

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "aws.s3.bucket.documents", defaultValue = "my-documents-bucket")
    String documentsBucket;

    @ConfigProperty(name = "aws.s3.prefix.documents", defaultValue = "generated-documents")
    String documentsPrefix;

    /**
     * Saves a generated document to S3 with metadata.
     *
     * @param filePathToUpload the local file path of the document to upload
     * @return the S3 path in format "s3@:bucket/key"
     * @throws DocumentGenerationException if upload fails
     */
    @Override
    public String save(String filePathToUpload) {
        File file = new File(filePathToUpload);

        if (!file.exists() || !file.canRead()) {
            throw new DocumentGenerationException("Cannot read file to upload: " + filePathToUpload);
        }

        try {
            // Generate S3 key with date-based organization
            String s3Key = generateS3Key(file);

            // Determine content type based on file extension
            String contentType = determineContentType(file.getName());

            // Prepare metadata for future searches
            Map<String, String> metadata = buildMetadata(file);

            log.infof("Uploading document to S3: %s (%s)",
                     LogSanitizer.sanitizePath(s3Key),
                     LogSanitizer.sanitizeByteCount(file.length()));

            // Build S3 put request with metadata
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(documentsBucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength(file.length())
                    .metadata(metadata)
                    .build();

            // Upload to S3
            s3Client.putObject(putRequest, RequestBody.fromFile(file));

            log.infof("Document successfully uploaded: %s", LogSanitizer.sanitizePath(s3Key));

            // Return path in protocol format: s3@:bucket/key
            // The ':' indicates using the configured bucket
            return String.format("s3@:%s/%s", documentsBucket, s3Key);

        } catch (Exception e) {
            log.errorf(e, "Failed to upload document: %s",
                LogSanitizer.sanitizeErrorMessage(e.getMessage()));
            throw new DocumentGenerationException("Failed to save document to S3", e);
        }
    }

    /**
     * Generates a unique S3 key for the document.
     * Format: {prefix}/{year}/{month}/{day}/{filename}-{uuid}.{ext}
     *
     * Example: generated-documents/2025/11/04/report-550e8400-e29b-41d4-a716-446655440000.pdf
     *
     * @param file the file to upload
     * @return the generated S3 key
     */
    private String generateS3Key(File file) {
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());

        // Extract filename without extension
        String fileName = file.getName();
        String baseName = fileName;
        String extension = "";

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot); // includes the dot
        }

        // Generate unique UUID
        String uuid = UUID.randomUUID().toString();

        // Build key: prefix/yyyy/MM/dd/basename-uuid.ext
        return String.format("%s/%s/%s/%s/%s-%s%s",
                documentsPrefix, year, month, day, baseName, uuid, extension);
    }

    /**
     * Builds metadata map for S3 object to enable future searches.
     *
     * @param file the file being uploaded
     * @return map of metadata key-value pairs
     */
    private Map<String, String> buildMetadata(File file) {
        Map<String, String> metadata = new HashMap<>();

        // Add upload timestamp
        metadata.put("upload-timestamp",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Add original filename
        metadata.put("original-filename", file.getName());

        // Add file size
        metadata.put("file-size", String.valueOf(file.length()));

        // Add generation date
        metadata.put("generated-date",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Add file type/extension
        String extension = getFileExtension(file.getName());
        if (!extension.isEmpty()) {
            metadata.put("file-type", extension);
        }

        return metadata;
    }

    /**
     * Determines the content type based on file extension.
     *
     * @param fileName the file name
     * @return the MIME content type
     */
    private String determineContentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();

        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "html", "htm" -> "text/html";
            case "txt" -> "text/plain";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "doc" -> "application/msword";
            default -> "application/octet-stream";
        };
    }

    /**
     * Extracts the file extension from a filename.
     *
     * @param fileName the file name
     * @return the extension without the dot, or empty string if none
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }
}
