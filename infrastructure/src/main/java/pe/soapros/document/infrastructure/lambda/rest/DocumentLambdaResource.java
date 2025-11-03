package pe.soapros.document.infrastructure.lambda.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.generation.input.TemplateData;
import pe.soapros.document.infrastructure.mapper.TemplateDataMapper;
import pe.soapros.document.infrastructure.qualifier.Pdf;

import java.util.Base64;
import java.util.Map;

/**
 * REST endpoint for document generation.
 * Accepts template data and returns a Base64-encoded PDF document.
 */
@Path("/generate")
@JBossLog
public class DocumentLambdaResource {

    @Inject
    @Pdf
    GenerateDocumentUseCase generateDocumentUseCase;

    /**
     * Generates a PDF document from template data.
     *
     * @param input the template data including path, variables, and images
     * @return Response containing Base64-encoded PDF in JSON format
     * @throws DocumentGenerationException if document generation fails (handled by DomainExceptionMapper)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generate(TemplateData input) throws DocumentGenerationException {
        log.infof("Starting document generation for template: %s", input.getTemplatePath());

        // Map infrastructure DTO to domain entity
        byte[] result = generateDocumentUseCase.execute(
                TemplateDataMapper.from(input.getData(), input.getImages(), input.getTemplatePath())
        );

        // Encode result as Base64
        String base64 = Base64.getEncoder().encodeToString(result);

        log.infof("Successfully generated document for template: %s (%d bytes)",
                input.getTemplatePath(), result.length);

        return Response.ok(Map.of("base64Document", base64))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .build();

        // Note: Exception handling is delegated to DomainExceptionMapper
    }
}
