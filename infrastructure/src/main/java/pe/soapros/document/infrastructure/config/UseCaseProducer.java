package pe.soapros.document.infrastructure.config;


import jakarta.ws.rs.Produces;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.infrastructure.qualifier.Pdf;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UseCaseProducer {

    @Produces
    @Pdf
    public GenerateDocumentUseCase produceGenerateDocumentUseCase(@Pdf DocumentGenerator generator) {
        return new GenerateDocumentUseCase(generator);
    }
}
