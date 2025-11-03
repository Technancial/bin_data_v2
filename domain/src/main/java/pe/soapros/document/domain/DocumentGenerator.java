package pe.soapros.document.domain;

import pe.soapros.document.domain.exception.DocumentGenerationException;

/**
 * Port interface for document generation.
 * Implementations should generate PDF documents from templates.
 */
public interface DocumentGenerator {

    /**
     * Generates a PDF document from the provided template request.
     *
     * @param templateData the template request containing template path, data, and images
     * @return byte array containing the generated PDF document
     * @throws DocumentGenerationException if any error occurs during document generation
     */
    byte[] generate(TemplateRequest templateData) throws DocumentGenerationException;
}
