package pe.soapros.document.domain.exception;

/**
 * Exception thrown when there's an error processing or rendering the template.
 */
public class TemplateProcessingException extends DocumentGenerationException {

    public TemplateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateProcessingException(String message) {
        super(message);
    }
}
