package pe.soapros.document.domain;

import lombok.Data;
import pe.soapros.document.domain.exception.InvalidTemplatePathException;

import java.util.Map;
import java.util.Objects;

/**
 * Domain entity representing a request to generate a document from a template.
 */
@Data
public class TemplateRequest {
    private String templatePath;
    private Map<String, Object> data;
    private Map<String, String> images;
    private boolean isPersist;
    private String fileType;

    /**
     * Sets the template path with security validation.
     * Prevents path traversal attacks by rejecting paths containing ".." or starting with "/".
     *
     * @param templatePath the path to the template file (relative path only, or protocol-based URI)
     * @throws InvalidTemplatePathException if the path is invalid or contains dangerous patterns
     */
    public void setTemplatePath(String templatePath) {
        validateTemplatePath(templatePath);
        this.templatePath = templatePath;
        this.isPersist = false;
    }

    /**
     * Sets the resolved template path without validation.
     * This is used internally by the application layer after resolving/caching templates.
     * Only call this method from trusted internal code (e.g., UseCases).
     *
     * @param resolvedPath the absolute path to the cached/resolved template file
     */
    public void setResolvedTemplatePath(String resolvedPath) {
        this.templatePath = resolvedPath;
    }

    /**
     * Validates that the template path is safe and doesn't contain dangerous patterns.
     * Allows:
     * - Relative paths (e.g., "template.odt", "reports/template.docx")
     * - Protocol-based URIs (e.g., "s3@bucket:key", "fs@/path/file", "http@https://...")
     *
     * @param path the path to validate
     * @throws InvalidTemplatePathException if the path is invalid
     */
    private void validateTemplatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new InvalidTemplatePathException("null or empty");
        }

        // Check if it's a protocol-based URI (contains @)
        boolean hasProtocol = path.contains("@");

        // If it has a protocol, skip absolute path validation
        // (protocols like fs@/path or s3@bucket:key are allowed)
        if (!hasProtocol) {
            // For non-protocol paths, prevent path traversal and absolute paths
            if (path.contains("..")) {
                throw new InvalidTemplatePathException(path);
            }

            if (path.startsWith("/") || path.startsWith("\\")) {
                throw new InvalidTemplatePathException(path);
            }

            // Prevent Windows drive letters (C:, D:, etc.)
            if (path.matches("^[a-zA-Z]:.*")) {
                throw new InvalidTemplatePathException(path);
            }
        } else {
            // For protocol-based URIs, only prevent path traversal
            if (path.contains("..")) {
                throw new InvalidTemplatePathException(path);
            }
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
               ", isPersist=" +  isPersist +
               '}';
    }
}
