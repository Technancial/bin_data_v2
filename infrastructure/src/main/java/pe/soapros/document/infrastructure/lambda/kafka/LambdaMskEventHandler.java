package pe.soapros.document.infrastructure.lambda.kafka;

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
import pe.soapros.document.infrastructure.generation.input.SentryMessageInput;
import pe.soapros.document.infrastructure.mapper.SentryMessageMapper;
import pe.soapros.document.infrastructure.util.LogSanitizer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AWS Lambda handler for MSK (Kafka) Event Source Mapping.
 *
 * Esta clase está diseñada específicamente para AWS Lambda con MSK Event Source Mapping:
 * - AWS MSK ESM consume mensajes de Kafka y los agrupa en batch
 * - Invoca esta Lambda función vía HTTP
 * - Procesa el batch en paralelo usando programación funcional
 * - Envía resultados a otro tópico de MSK usando Kafka Producer
 *
 * IMPORTANTE: NO usar @Incoming/@Outgoing porque Lambda es serverless.
 * El consumer está gestionado por AWS Lambda ESM, y el producer se invoca manualmente.
 *
 * Flujo completo:
 * 1. AWS MSK Topic (Input) → Lambda ESM → POST /msk/batch
 * 2. Procesamiento funcional paralelo (igual que DocumentLambdaResource)
 * 3. Envío manual a AWS MSK Topic (Output) vía KafkaProducer
 */
@Path("/msk")
@JBossLog
public class LambdaMskEventHandler {

    @Inject
    GenerateDocumentUseCase generateDocumentUseCase;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SentryMessageMapper sentryMessageMapper;

    @Inject
    DocumentResultProducer kafkaProducer;

    @ConfigProperty(name = "app.generation.temp", defaultValue = "/temp")
    String tempDirectory;

    /**
     * Procesa batch de eventos desde AWS MSK Event Source Mapping.
     *
     * Este endpoint es invocado por AWS Lambda ESM cuando llegan mensajes desde MSK.
     * Realiza la misma lógica que DocumentLambdaResource pero en batch y funcional.
     *
     * Características:
     * - Procesamiento PARALELO con streams funcionales
     * - Misma lógica que el handler HTTP (DocumentLambdaResource)
     * - Envía resultados a otro tópico MSK automáticamente
     * - Retorna estadísticas del procesamiento
     *
     * @param inputBatch batch de SentryMessageInput desde MSK
     * @return Response con estadísticas de procesamiento
     */
    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response processMskBatch(List<SentryMessageInput> inputBatch) {
        log.infof("📨 Received MSK batch with %d messages", inputBatch.size());
        long startTime = System.currentTimeMillis();

        // Procesamiento funcional paralelo
        List<ProcessResult> results = inputBatch.parallelStream()
            .map(this::processMessageFunctional)
            .collect(Collectors.toList());

        // Separar éxitos y errores
        List<SentryMessageInput> successResults = results.stream()
            .filter(r -> r.success)
            .map(r -> r.result)
            .collect(Collectors.toList());

        List<String> errors = results.stream()
            .filter(r -> !r.success)
            .map(r -> r.errorMessage)
            .collect(Collectors.toList());

        // Enviar resultados exitosos a MSK (async)
        if (!successResults.isEmpty()) {
            kafkaProducer.sendBatch(successResults)
                .whenComplete((v, error) -> {
                    if (error != null) {
                        log.errorf(error, "Error sending results to MSK");
                    } else {
                        log.infof("✅ Sent %d results to MSK output topic", successResults.size());
                    }
                });
        }

        long duration = System.currentTimeMillis() - startTime;

        // Retornar estadísticas
        Map<String, Object> response = new HashMap<>();
        response.put("processedRecords", inputBatch.size());
        response.put("successCount", successResults.size());
        response.put("failureCount", errors.size());
        response.put("errors", errors);
        response.put("durationMs", duration);
        response.put("timestamp", System.currentTimeMillis());

        log.infof("✅ MSK batch completed: %d success, %d failures in %d ms",
            successResults.size(), errors.size(), duration);

        return Response.ok(response).build();
    }

    /**
     * Procesa un mensaje individual usando programación funcional.
     * Esta función hace EXACTAMENTE lo mismo que DocumentLambdaResource.generate()
     *
     * Transformación funcional:
     * SentryMessageInput → TemplateRequests → DocumentResults → Updated Input
     *
     * @param input SentryMessageInput individual
     * @return ProcessResult con éxito o error
     */
    private ProcessResult processMessageFunctional(SentryMessageInput input) {
        try {
            int outputCount = input.getData() != null &&
                input.getData().getItem_canonico() != null &&
                input.getData().getItem_canonico().getOutputs() != null
                    ? input.getData().getItem_canonico().getOutputs().size()
                    : 0;

            log.debugf("Processing message with %d outputs", outputCount);

            // Pipeline funcional (igual que DocumentLambdaResource)
            List<TemplateRequest> templates = sentryMessageMapper.toTemplateRequest(input);

            List<DocumentResult> documentResults = templates.parallelStream()
                .map(this::generateDocument)
                .collect(Collectors.toList());

            // Actualizar input original con rutas generadas
            SentryMessageInput updatedInput = sentryMessageMapper
                .updateWithGeneratedDocuments(input, documentResults);

            return ProcessResult.success(updatedInput);

        } catch (Exception e) {
            log.errorf(e, "Error processing message");
            return ProcessResult.failure(e.getMessage());
        }
    }

    /**
     * Genera un documento individual.
     * Función pura - mismo input produce mismo output.
     *
     * @param template TemplateRequest
     * @return DocumentResult con bytes y paths
     */
    private DocumentResult generateDocument(TemplateRequest template) {
        log.debugf("Generating document - template: %s, format: %s",
            LogSanitizer.sanitizeTemplatePath(template.getTemplatePath()), template.getFileType());

        String pathFile = generateFilename(template.getFileType());
        DocumentResult result = generateDocumentUseCase.execute(template, pathFile);

        if (result.getRepositoryPath() != null) {
            log.debugf("Document persisted: %s", LogSanitizer.sanitizeS3Uri(result.getRepositoryPath()));
        }

        return result;
    }

    /**
     * Genera nombre único de archivo usando ULID.
     *
     * @param extension extensión del archivo
     * @return path completo
     */
    private String generateFilename(String extension) {
        Ulid name = UlidCreator.getUlid();
        return tempDirectory + File.separator + name.toString() + "." + extension;
    }

    /**
     * Clase interna para resultado de procesamiento.
     * Inmutable para programación funcional.
     */
    private static class ProcessResult {
        final boolean success;
        final SentryMessageInput result;
        final String errorMessage;

        private ProcessResult(boolean success, SentryMessageInput result, String errorMessage) {
            this.success = success;
            this.result = result;
            this.errorMessage = errorMessage;
        }

        static ProcessResult success(SentryMessageInput result) {
            return new ProcessResult(true, result, null);
        }

        static ProcessResult failure(String errorMessage) {
            return new ProcessResult(false, null, errorMessage);
        }
    }
}
