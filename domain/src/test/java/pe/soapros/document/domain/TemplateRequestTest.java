package pe.soapros.document.domain;

import org.junit.jupiter.api.Test;
import pe.soapros.document.domain.exception.InvalidTemplatePathException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateRequest entity.
 * Tests validation logic and security constraints.
 */
class TemplateRequestTest {

    @Test
    void shouldCreateValidTemplateRequest() {
        // Arrange
        TemplateRequest request = new TemplateRequest();
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");
        data.put("amount", 1000);

        // Act
        request.setTemplatePath("plantilla.docx");
        request.setData(data);

        // Assert
        assertEquals("plantilla.docx", request.getTemplatePath());
        assertEquals(data, request.getData());
    }

    @Test
    void shouldRejectPathWithDotDot() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert
        assertThrows(InvalidTemplatePathException.class, () -> {
            request.setTemplatePath("../etc/passwd");
        });
    }

    @Test
    void shouldRejectAbsolutePath() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert
        assertThrows(InvalidTemplatePathException.class, () -> {
            request.setTemplatePath("/etc/passwd");
        });
    }

    @Test
    void shouldRejectWindowsAbsolutePath() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert
        assertThrows(InvalidTemplatePathException.class, () -> {
            request.setTemplatePath("C:\\Windows\\System32\\config");
        });
    }

    @Test
    void shouldRejectNullPath() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert
        assertThrows(InvalidTemplatePathException.class, () -> {
            request.setTemplatePath(null);
        });
    }

    @Test
    void shouldRejectEmptyPath() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert
        assertThrows(InvalidTemplatePathException.class, () -> {
            request.setTemplatePath("   ");
        });
    }

    @Test
    void shouldAcceptValidRelativePath() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            request.setTemplatePath("templates/invoice.docx");
        });
    }

    @Test
    void shouldAcceptSimpleFilename() {
        // Arrange
        TemplateRequest request = new TemplateRequest();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            request.setTemplatePath("plantilla.docx");
        });
    }

    @Test
    void shouldSetImagesCorrectly() {
        // Arrange
        TemplateRequest request = new TemplateRequest();
        Map<String, String> images = new HashMap<>();
        images.put("logo", "base64encodeddata");

        // Act
        request.setTemplatePath("template.docx");
        request.setImages(images);

        // Assert
        assertEquals(images, request.getImages());
    }
}
