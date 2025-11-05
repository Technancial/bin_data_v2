package pe.soapros.document.application;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.DocumentRepository;
import pe.soapros.document.domain.DocumentResult;
import pe.soapros.document.domain.TemplateRepository;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.domain.exception.TemplateNotFoundException;

import java.io.File;
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

    @Mock
    private GenerateDocumentUseCase.DocumentGeneratorFactory mockFactory;

    @Mock
    private DocumentRepository mockRepository;

    @Mock
    private TemplateRepository templateRepository;

    private GenerateDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        // Configure factory to return our mock generator for any fileType (including null)
        when(mockFactory.getGenerator(any())).thenReturn(mockGenerator);

        // Configure templateRepository to always return true for isLocal (template already cached)
        when(templateRepository.isLocal(any())).thenReturn(true);

        useCase = new GenerateDocumentUseCase(mockFactory, mockRepository, templateRepository);
    }

    @Test
    void shouldGenerateDocumentSuccessfully() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        byte[] expectedPdf = new byte[]{1, 2, 3, 4, 5};
        String pathFile = getPathFile();

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenReturn(expectedPdf);

        // Act
        DocumentResult result = useCase.execute(request, pathFile);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getDocumentBytes());
        assertArrayEquals(expectedPdf, result.getDocumentBytes());
        assertEquals(pathFile, result.getLocalPath());
        assertNull(result.getRepositoryPath()); // Not persisted
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
                () -> useCase.execute(request, getPathFile())
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

        when(mockGenerator.generate(any(TemplateRequest.class))).thenReturn(expectedPdf);

        // Act
        DocumentResult result = useCase.execute(request, getPathFile());

        // Assert
        assertNotNull(result);
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
        DocumentResult result = useCase.execute(request, getPathFile());

        // Assert
        assertNotNull(result);
        assertNotNull(result.getDocumentBytes());
        assertEquals(0, result.getDocumentBytes().length);
    }

    @Test
    void shouldPersistDocumentWhenRequested() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        request.setPersist(true);
        byte[] expectedPdf = new byte[]{1, 2, 3, 4, 5};
        String pathFile = getPathFile();
        String expectedS3Path = "s3@:my-bucket/generated-documents/2025/11/04/doc-uuid.pdf";

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenReturn(expectedPdf);
        when(mockRepository.save(any(String.class)))
                .thenReturn(expectedS3Path);

        // Act
        DocumentResult result = useCase.execute(request, pathFile);

        // Assert
        assertNotNull(result);
        assertArrayEquals(expectedPdf, result.getDocumentBytes());
        assertEquals(pathFile, result.getLocalPath());
        assertEquals(expectedS3Path, result.getRepositoryPath());
        verify(mockRepository, times(1)).save(pathFile);
    }

    @Test
    void shouldNotPersistDocumentWhenNotRequested() throws DocumentGenerationException {
        // Arrange
        TemplateRequest request = createValidTemplateRequest();
        request.setPersist(false);
        byte[] expectedPdf = new byte[]{1, 2, 3};

        when(mockGenerator.generate(any(TemplateRequest.class)))
                .thenReturn(expectedPdf);

        // Act
        DocumentResult result = useCase.execute(request, getPathFile());

        // Assert
        assertNotNull(result);
        assertNull(result.getRepositoryPath());
        verify(mockRepository, never()).save(any());
    }

    // Helper method to create valid test data
    private TemplateRequest createValidTemplateRequest() {
        TemplateRequest request = new TemplateRequest();
        request.setTemplatePath("plantilla.docx");
        request.setFileType("pdf"); // Default to PDF for tests

        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");
        data.put("amount", 1000);
        request.setData(data);

        return request;
    }

    private String getPathFile () {
        String tempDir = System.getProperty("java.io.tmpdir");
        Ulid nameFile = UlidCreator.getUlid();
        return  tempDir + File.separator + nameFile.toString() + ".pdf";
    }
}
