package pe.soapros.document.domain;

/**
 * Repository for persisting generated documents.
 */
public interface DocumentRepository {

    /**
     * Saves a generated document to the repository.
     *
     * @param filePathToUpload the local file path of the document to upload
     * @return the repository path where the document was saved (e.g., "s3@:bucket/path/file")
     */
    String save(String filePathToUpload);
}
