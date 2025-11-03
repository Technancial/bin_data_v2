package pe.soapros.document.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.domain.exception.TemplateNotFoundException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GenerateDocumentUseCase.
 * Uses mocks to isolate the use case from infrastructure dependencies.
 */
@ExtendWith(MockitoExtension.class)
class GenerateDocumentUseCaseTest {

    @Mock
    private DocumentGenerator mockGenerator;

    private GenerateDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateDocumentUseCase(mockGenerator);
    }

    @Test
    void shouldGenerateDocumentSuccessfully() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        byte[] expectedPdf = new byte[]{1, 2, 3, 4, 5};

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenReturn(expectedPdf);

        // Act
        byte[] result = useCase.execute(request);

        // Assert
        assertNotNull(result);
        assertArrayEquals(expectedPdf, result);
        verify(mockGenerator, times(1)).generate(request);
    }

    @Test
    void shouldPropagateDocumentGenerationException() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        TemplateNotFoundException expectedException = new TemplateNotFoundException("template.docx");

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenThrow(expectedException);

        // Act & Assert
        DocumentGenerationException exception = assertThrows(
                DocumentGenerationException.class,
                () -> useCase.execute(request)
        );

        assertTrue(exception instanceof TemplateNotFoundException);
        assertEquals(expectedException.getMessage(), exception.getMessage());
        verify(mockGenerator, times(1)).generate(request);
    }

    @Test
    void shouldCallGeneratorWithCorrectRequest() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        byte[] expectedPdf = new byte[]{1, 2, 3};

        when(mockGenerator.generate(request)).thenReturn(expectedPdf);

        // Act
        useCase.execute(request);

        // Assert
        verify(mockGenerator, times(1)).generate(request);
        verifyNoMoreInteractions(mockGenerator);
    }

    @Test
    void shouldReturnEmptyArrayWhenGeneratorReturnsEmpty() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        byte[] emptyPdf = new byte[0];

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenReturn(emptyPdf);

        // Act
        byte[] result = useCase.execute(request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // Helper method to create valid test data
    private TemplateRequest createValidTemplateRequest() {
        TemplateRequest request = new TemplateRequest();
        request.setTemplatePath("plantilla.docx");

        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");
        data.put("amount", 1000);
        request.setData(data);

        return request;
    }
}
