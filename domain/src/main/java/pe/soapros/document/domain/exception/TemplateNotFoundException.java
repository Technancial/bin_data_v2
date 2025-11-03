package pe.soapros.document.domain.exception;

/**
 * Exception thrown when a requested template file cannot be found.
 */
public class TemplateNotFoundException extends DocumentGenerationException {

    private final String templatePath;

    public TemplateNotFoundException(String templatePath) {
        super("Template not found: " + templatePath);
        this.templatePath = templatePath;
    }

    public String getTemplatePath() {
        return templatePath;
    }
}
