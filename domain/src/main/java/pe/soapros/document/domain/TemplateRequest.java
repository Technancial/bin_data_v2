package pe.soapros.document.domain;

import lombok.Getter;
import pe.soapros.document.domain.exception.InvalidTemplatePathException;

import java.util.Map;
import java.util.Objects;

/**
 * Domain entity representing a request to generate a document from a template.
 */
@Getter
public class TemplateRequest {
    private String templatePath;
    private Map<String, Object> data;
    private Map<String, String> images;

    /**
     * Sets the template path with security validation.
     * Prevents path traversal attacks by rejecting paths containing ".." or starting with "/".
     *
     * @param templatePath the path to the template file (relative path only)
     * @throws InvalidTemplatePathException if the path is invalid or contains dangerous patterns
     */
    public void setTemplatePath(String templatePath) {
        validateTemplatePath(templatePath);
        this.templatePath = templatePath;
    }

    /**
     * Sets the data map for template variable substitution.
     *
     * @param data map of variable names to values
     */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * Sets the images map for embedding images in the document.
     *
     * @param images map of image names to Base64-encoded image data
     */
    public void setImages(Map<String, String> images) {
        this.images = images;
    }

    /**
     * Validates that the template path is safe and doesn't contain dangerous patterns.
     *
     * @param path the path to validate
     * @throws InvalidTemplatePathException if the path is invalid
     */
    private void validateTemplatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new InvalidTemplatePathException("null or empty");
        }

        // Prevent path traversal attacks
        if (path.contains("..")) {
            throw new InvalidTemplatePathException(path);
        }

        // Prevent absolute paths (should only accept relative paths)
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new InvalidTemplatePathException(path);
        }

        // Prevent Windows drive letters (C:, D:, etc.)
        if (path.matches("^[a-zA-Z]:.*")) {
            throw new InvalidTemplatePathException(path);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateRequest that = (TemplateRequest) o;
        return Objects.equals(templatePath, that.templatePath) &&
               Objects.equals(data, that.data) &&
               Objects.equals(images, that.images);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templatePath, data, images);
    }

    @Override
    public String toString() {
        return "TemplateRequest{" +
               "templatePath='" + templatePath + '\'' +
               ", dataKeys=" + (data != null ? data.keySet() : "null") +
               ", imageKeys=" + (images != null ? images.keySet() : "null") +
               '}';
    }
}
