package pe.soapros.document.infrastructure.factory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.DocumentFormat;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.infrastructure.qualifier.Html;
import pe.soapros.document.infrastructure.qualifier.Pdf;
import pe.soapros.document.infrastructure.qualifier.Txt;

/**
 * Factory for creating the appropriate DocumentGenerator based on the desired output format.
 * Uses CDI qualifiers to inject specific implementations.
 * Implements the factory interface from the application layer for dependency inversion.
 */
@ApplicationScoped
@JBossLog
public class DocumentGeneratorFactory implements GenerateDocumentUseCase.DocumentGeneratorFactory {

    @Inject
    @Pdf
    DocumentGenerator pdfGenerator;

    @Inject
    @Html
    DocumentGenerator htmlGenerator;

    @Inject
    @Txt
    DocumentGenerator txtGenerator;

    /**
     * Selects the appropriate generator based on the document format.
     *
     * @param format the desired output format
     * @return the corresponding DocumentGenerator implementation
     */
    public DocumentGenerator getGenerator(DocumentFormat format) {
        log.debugf("Selecting generator for format: %s", format);

        return switch (format) {
            case PDF -> pdfGenerator;
            case HTML -> htmlGenerator;
            case TXT -> txtGenerator;
        };
    }

    /**
     * Selects the appropriate generator based on a string representation of the format.
     * Defaults to PDF if the format is null or unrecognized.
     *
     * @param fileType the format string (e.g., "pdf", "html", "txt")
     * @return the corresponding DocumentGenerator implementation
     */
    public DocumentGenerator getGenerator(String fileType) {
        DocumentFormat format = DocumentFormat.fromString(fileType);
        log.debugf("Resolved format '%s' to %s", fileType, format);
        return getGenerator(format);
    }
}
