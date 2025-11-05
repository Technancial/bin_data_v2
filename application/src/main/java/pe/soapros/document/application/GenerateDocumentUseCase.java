package pe.soapros.document.application;

import pe.soapros.document.domain.*;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.domain.exception.TemplateProcessingException;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Use case for generating documents from templates in various formats (PDF, HTML, TXT).
 * This orchestrates the document generation process using the appropriate generator
 * based on the requested format.
 */
public class GenerateDocumentUseCase {
    private final DocumentGeneratorFactory generatorFactory;
    private final DocumentRepository repository;
    private final TemplateRepository templateRepository;

    /**
     * Constructor injection with factory for multi-format support.
     *
     * @param generatorFactory factory to select the appropriate generator
     * @param repository repository for document persistence
     * @param templateRepository repository for template
     */
    public GenerateDocumentUseCase(DocumentGeneratorFactory generatorFactory, DocumentRepository repository, TemplateRepository templateRepository) {
        this.generatorFactory = generatorFactory;
        this.repository = repository;
        this.templateRepository = templateRepository;
    }

    /**
     * Interface for the factory to allow dependency inversion.
     * This allows the application layer to depend on an abstraction, not a concrete infrastructure class.
     */
    public interface DocumentGeneratorFactory {
        DocumentGenerator getGenerator(String fileType);
    }

    /**
     * Executes the document generation use case.
     * Selects the appropriate generator based on the fileType in the request.
     *
     * @param data the template request with all required data
     * @param pathFile the path where the document should be saved locally
     * @return DocumentResult containing the document bytes and paths
     * @throws DocumentGenerationException if any error occurs during generation
     */
    public DocumentResult execute(TemplateRequest data, String pathFile) throws DocumentGenerationException {
        DocumentGenerator generator = generatorFactory.getGenerator(data.getFileType());

        // Obtener el template (descargarlo/copiarlo al caché si tiene protocolo)
        if (!templateRepository.isLocal(data.getTemplatePath())) {
            java.io.File localTemplateFile = templateRepository.getTemplate(data.getTemplatePath());
            // Update the template path to point to the local cached file (bypasses validation)
            data.setResolvedTemplatePath(localTemplateFile.getAbsolutePath());
        }

        // Generate the document
        byte[] documentGenerate = generator.generate(data);

        // Write to local filesystem
        try (FileOutputStream os = new FileOutputStream(pathFile)) {
            os.write(documentGenerate);
        } catch (IOException e) {
            throw new TemplateProcessingException("Failed to write document to file path: " + pathFile, e);
        }

        String localPathWithProtocol = "fs@" + pathFile;
        return new DocumentResult(documentGenerate, localPathWithProtocol);
    }
}
