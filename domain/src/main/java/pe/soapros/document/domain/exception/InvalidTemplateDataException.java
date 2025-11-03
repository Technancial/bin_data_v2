package pe.soapros.document.domain.exception;

/**
 * Exception thrown when template data is invalid or malformed.
 */
public class InvalidTemplateDataException extends DocumentGenerationException {

    public InvalidTemplateDataException(String message) {
        super(message);
    }

    public InvalidTemplateDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
