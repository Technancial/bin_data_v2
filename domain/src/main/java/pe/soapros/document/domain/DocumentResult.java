package pe.soapros.document.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of a document generation operation.
 * Contains both the document bytes and the path where it was saved.
 */
@Data
public class DocumentResult {
    /**
     * The generated document bytes
     */
    private byte[] documentBytes;

    /**
     * The local file path where the document was saved
     */
    private String localPath;

    /**
     * The repository path where the document was persisted (e.g., S3 path).
     * Null if the document was not persisted.
     */
    private String repositoryPath;

    /**
     * Creates a result with only local path (no persistence).
     *
     * @param documentBytes the document bytes
     * @param localPath the local file path
     */
    public DocumentResult(byte[] documentBytes, String localPath) {
        //this(documentBytes, localPath, null);
        this.documentBytes = documentBytes;
        this.localPath = localPath;
    }
}
