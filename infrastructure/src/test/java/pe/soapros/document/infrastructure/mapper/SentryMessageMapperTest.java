package pe.soapros.document.infrastructure.mapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SentryMessageMapper utility methods.
 */
class SentryMessageMapperTest {

    @Test
    void testExtractFilenameFromS3Uri_WithFullPath() throws Exception {
        // Given
        String uri = "s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("2.0/capacniam/bn_ripley/template_producto/10_112.odt", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithSimplePath() throws Exception {
        // Given
        String uri = "s3@bucket:templates/plantilla.docx";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("templates/plantilla.docx", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithOnlyFilename() throws Exception {
        // Given
        String uri = "s3@bucket:plantilla.docx";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("plantilla.docx", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithoutColon() throws Exception {
        // Given - URI sin formato esperado
        String uri = "plantilla.docx";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then - Debería retornar tal cual
        assertEquals("plantilla.docx", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithEmptyString() throws Exception {
        // Given
        String uri = "";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithNull() throws Exception {
        // Given
        String uri = null;

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("", result);
    }

    @Test
    void testExtractFilenameFromS3Uri_WithNestedPath() throws Exception {
        // Given
        String uri = "s3@storage.example.com:v1/company/project/docs/report.pdf";

        // When
        String result = invokePrivateExtractMethod(uri);

        // Then
        assertEquals("v1/company/project/docs/report.pdf", result);
    }

    /**
     * Helper method to invoke the private extractFilenameFromS3Uri method using reflection.
     */
    private String invokePrivateExtractMethod(String uri) throws Exception {
        Method method = SentryMessageMapper.class.getDeclaredMethod("extractFilenameFromS3Uri", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, uri);
    }
}
