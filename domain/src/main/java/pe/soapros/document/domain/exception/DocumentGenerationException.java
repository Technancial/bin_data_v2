package pe.soapros.document.domain.exception;

/**
 * Base exception for all document generation errors.
 * This is a domain exception that represents business rule violations.
 */
public class DocumentGenerationException extends RuntimeException {

    public DocumentGenerationException(String message) {
        super(message);
    }

    public DocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
