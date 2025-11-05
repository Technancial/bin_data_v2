package pe.soapros.document.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.DocumentRepository;
import pe.soapros.document.domain.TemplateRepository;
import pe.soapros.document.infrastructure.factory.DocumentGeneratorFactory;

/**
 * CDI Producer for application use cases.
 * Configures and wires dependencies for use cases.
 */
@ApplicationScoped
public class UseCaseProducer {

    /**
     * Produces the GenerateDocumentUseCase with all required dependencies.
     * Now uses DocumentGeneratorFactory to support multiple output formats.
     *
     * @param factory the factory for selecting appropriate generators
     * @param repository the repository for document persistence
     * @return configured GenerateDocumentUseCase instance
     */
    @Produces
    @ApplicationScoped
    public GenerateDocumentUseCase produceGenerateDocumentUseCase(
            DocumentGeneratorFactory factory,
            DocumentRepository repository,
            TemplateRepository templateRepository) {
        return new GenerateDocumentUseCase(factory, repository, templateRepository);
    }
}
