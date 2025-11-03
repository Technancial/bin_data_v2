package pe.soapros.document.application;

import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;

/**
 * Use case for generating PDF documents from templates.
 * This orchestrates the document generation process using the domain's DocumentGenerator port.
 */
public class GenerateDocumentUseCase {
    private final DocumentGenerator generator;

    /**
     * Constructor injection of the document generator implementation.
     *
     * @param generator the document generator to use
     */
    public GenerateDocumentUseCase(DocumentGenerator generator) {
        this.generator = generator;
    }

    /**
     * Executes the document generation use case.
     *
     * @param data the template request with all required data
     * @return byte array containing the generated PDF document
     * @throws DocumentGenerationException if any error occurs during generation
     */
    public byte[] execute(TemplateRequest data) throws DocumentGenerationException {
        return generator.generate(data);
    }
}
