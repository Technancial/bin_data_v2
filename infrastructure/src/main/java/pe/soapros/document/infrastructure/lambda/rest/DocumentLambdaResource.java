package pe.soapros.document.infrastructure.lambda.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.DocumentResult;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.generation.input.SentryMessageInput;
import pe.soapros.document.infrastructure.mapper.SentryMessageMapper;
import pe.soapros.document.infrastructure.util.LogSanitizer;

import java.io.File;
import java.util.List;

/**
 * REST endpoint for document generation.
 * Supports multiple output formats (PDF, HTML, TXT) based on request data.
 * Returns the same SentryMessageInput with result.location populated.
 */
@Path("/generate")
@JBossLog
public class DocumentLambdaResource {

    @Inject
    GenerateDocumentUseCase generateDocumentUseCase;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SentryMessageMapper sentryMessageMapper;

    @ConfigProperty(name = "app.generation.temp", defaultValue = "/temp")
    String tempDirectory;

    /**
     * Generates one or more documents from Sentry message template data.
     * The output format (PDF, HTML, TXT) is determined by the fileType field in each request.
     *
     * Returns the same SentryMessageInput structure with result.location populated
     * with the path of the generated document (local or S3 if persisted).
     *
     * @param input the Sentry message containing template data, variables, images, and formats
     * @return Response containing the same SentryMessageInput with result.location updated
     * @throws DocumentGenerationException if document generation fails (handled by DomainExceptionMapper)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response generate(SentryMessageInput input) throws DocumentGenerationException {
        int outputCount = 0;
        if (input.getData() != null &&
            input.getData().getItem_canonico() != null &&
            input.getData().getItem_canonico().getOutputs() != null) {
            outputCount = input.getData().getItem_canonico().getOutputs().size();
        }
        log.infof("Processing document generation request - outputs: %d", outputCount);

        // Mapear el DTO complejo del Sentry Business Message a una lista plana de templates
        List<TemplateRequest> templates = sentryMessageMapper.toTemplateRequest(input);
        log.infof("Generating %d documents", templates.size());

        // Generar cada documento (retorna DocumentResult con bytes y paths)
        List<DocumentResult> results = templates.stream()
                .map(this::generateDocument)
                .toList();

        log.infof("Successfully generated %d documents", results.size());

        // Actualizar el input original con las rutas de los documentos generados
        SentryMessageInput responseMessage = sentryMessageMapper.updateWithGeneratedDocuments(input, results);

        // Retornar el mismo mensaje con result.location actualizado
        //return Response.ok(responseMessage).build();
        try {
            log.debug("Iniciando serialización JSON manual para evitar el proveedor JAX-RS.");

            // Convertir el objeto final a un array de bytes
            byte[] jsonBytes = objectMapper.writeValueAsBytes(responseMessage);

            log.infof("JSON de respuesta serializado manualmente (%d bytes).", jsonBytes.length);

            // 3. Devolver los bytes directamente con el MediaType correcto
            return Response.ok(jsonBytes)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();

        } catch (Exception e) {
            log.errorf(e, "CRÍTICO: Fallo durante la serialización JSON manual de la respuesta.");

            // Retornar un error 500 simple si la serialización falla
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"SERVER_SERIALIZATION_ERROR\", \"message\": \"Fallo al serializar el cuerpo de la respuesta.\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
    }

    /**
     * Generates a single document from a TemplateRequest.
     *
     * @param template the template request with data, images, and configuration
     * @return DocumentResult with bytes and paths (local and repository if persisted)
     * @throws DocumentGenerationException if generation fails
     */
    private DocumentResult generateDocument(TemplateRequest template) {
        // Log only safe metadata, not the entire object with user data
        log.infof("Generating document - template: %s, fileType: %s, dataFields: %d, hasImages: %s, persist: %s",
                LogSanitizer.sanitizeTemplatePath(template.getTemplatePath()),
                template.getFileType(),
                template.getData() != null ? template.getData().size() : 0,
                template.getImages() != null && !template.getImages().isEmpty(),
                template.isPersist());

        String pathFile = generateFilename(template.getFileType());
        log.debugf("Generated filename: %s", LogSanitizer.sanitizePath(pathFile));

        DocumentResult result = generateDocumentUseCase.execute(template, pathFile);

        String filename = new File(pathFile).getName();
        log.debugf("Document generated: %s (%s)", filename,
            LogSanitizer.sanitizeByteCount(result.getDocumentBytes().length));

        // Log repository path if document was persisted
        if (result.getRepositoryPath() != null) {
            log.infof("Document persisted: %s", LogSanitizer.sanitizeS3Uri(result.getRepositoryPath()));
        }

        return result;
    }

    private String generateFilename(String extension) {
        Ulid name = UlidCreator.getUlid();
        return tempDirectory + File.separator + name.toString() + "." + extension;
    }
    /**
     * Extracts just the filename from a full template path to avoid logging sensitive directory structures.
     * Examples:
     * - "s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template.odt" -> "template.odt"
     * - "/path/to/template.docx" -> "template.docx"
     * - "template.html" -> "template.html"
     *
     * @param templatePath the full path to the template
     * @return just the filename without directory structure
     */
    private String extractFileName(String templatePath) {
        if (templatePath == null || templatePath.isEmpty()) {
            return "[unknown]";
        }

        // Remove protocol prefix if present (e.g., "s3@host:", "fs@", etc.)
        String pathWithoutProtocol = templatePath;
        if (templatePath.contains("@")) {
            int colonIndex = templatePath.indexOf(':');
            if (colonIndex != -1 && colonIndex < templatePath.length() - 1) {
                pathWithoutProtocol = templatePath.substring(colonIndex + 1);
            }
        }

        // Extract just the filename from the path
        int lastSlash = Math.max(
                pathWithoutProtocol.lastIndexOf('/'),
                pathWithoutProtocol.lastIndexOf('\\')
        );

        if (lastSlash >= 0 && lastSlash < pathWithoutProtocol.length() - 1) {
            return pathWithoutProtocol.substring(lastSlash + 1);
        }

        return pathWithoutProtocol;
    }
}
