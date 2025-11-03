package pe.soapros.document.domain.exception;

/**
 * Exception thrown when a template path contains invalid or dangerous characters.
 * This helps prevent path traversal attacks.
 */
public class InvalidTemplatePathException extends DocumentGenerationException {

    private final String invalidPath;

    public InvalidTemplatePathException(String invalidPath) {
        super("Invalid template path: " + invalidPath + ". Path must not contain '..' or absolute paths.");
        this.invalidPath = invalidPath;
    }

    public String getInvalidPath() {
        return invalidPath;
    }
}
