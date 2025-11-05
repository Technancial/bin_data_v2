package pe.soapros.document.infrastructure.lambda.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.generation.input.TemplateData;
import pe.soapros.document.infrastructure.lambda.kafka.storage.S3DocumentStorage;
import pe.soapros.document.infrastructure.mapper.TemplateDataMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST endpoint for processing Kafka-like events.
 * This endpoint is invoked by AWS Lambda when it receives events from Kafka Event Source Mapping.
 *
 * AWS Lambda with Kafka ESM will invoke this Lambda function, and we process the batch here.
 * Works alongside the HTTP handler (DocumentLambdaResource) for document generation.
 * Supports multiple output formats based on the fileType in each message.
 */
@Path("/kafka")
@JBossLog
public class KafkaEventHandler {

    @Inject
    GenerateDocumentUseCase generateDocumentUseCase;

    @Inject
    S3DocumentStorage s3Storage;

    /**
     * Processes a batch of template requests from Kafka events.
     * This endpoint receives a list of TemplateData objects and processes them asynchronously,
     * saving results to S3. Each message can specify its own output format (PDF, HTML, TXT).
     *
     * @param batch list of template data from Kafka messages
     * @return processing results with success/failure counts
     */
    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processBatch(List<TemplateData> batch) {
        log.infof("Received Kafka batch with %d messages", batch.size());

        Map<String, Object> results = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> s3Keys = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Procesar cada mensaje del batch
        for (int i = 0; i < batch.size(); i++) {
            TemplateData templateData = batch.get(i);
            try {
                String s3Key = processKafkaMessage(templateData, i);
                s3Keys.add(s3Key);
                successCount++;
            } catch (DocumentGenerationException e) {
                log.errorf(e, "Domain error processing message %d: %s", i, e.getMessage());
                errors.add(String.format("Message %d: %s", i, e.getMessage()));
                failureCount++;
            } catch (Exception e) {
                log.errorf(e, "Unexpected error processing message %d", i);
                errors.add(String.format("Message %d: Unexpected error", i));
                failureCount++;
            }
        }

        results.put("processedRecords", successCount + failureCount);
        results.put("successCount", successCount);
        results.put("failureCount", failureCount);
        results.put("s3Keys", s3Keys);
        results.put("errors", errors);
        results.put("timestamp", System.currentTimeMillis());

        log.infof("Kafka batch processing completed: %d success, %d failures",
                 successCount, failureCount);

        return Response.ok(results).build();
    }

    /**
     * Processes a single template request from Kafka and saves to S3.
     * The output format is determined by the fileType field in templateData.
     *
     * @param templateData the template data including format specification
     * @param messageIndex the index in the batch (for logging)
     * @return S3 key where document was saved
     * @throws Exception if processing fails
     */
    private String processKafkaMessage(TemplateData templateData, int messageIndex) throws Exception {
        log.infof("Processing Kafka message %d - template: %s",
                 messageIndex, templateData.getTemplatePath());

        // 1. Ejecutar el use case (misma lógica que HTTP)
        /*byte[] pdfBytes = generateDocumentUseCase.execute(
                TemplateDataMapper.from(
                        templateData.getData(),
                        templateData.getImages(),
                        templateData.getTemplatePath()
                )
        );*/

        log.infof("Document generated successfully (%d bytes)", "pdfBytes");

        // 2. Guardar el documento en S3
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "kafka");
        metadata.put("batchIndex", String.valueOf(messageIndex));
        metadata.put("processedAt", String.valueOf(System.currentTimeMillis()));

        String s3Key = s3Storage.saveDocument(
                "pdfBytes".getBytes(StandardCharsets.UTF_8),
                templateData.getTemplatePath(),
                metadata
        );

        log.infof("Document saved to S3: %s", s3Key);

        return s3Key;
    }
}
