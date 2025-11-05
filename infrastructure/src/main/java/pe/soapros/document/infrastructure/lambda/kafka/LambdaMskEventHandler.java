package pe.soapros.document.infrastructure.lambda.kafka;

import com.amazonaws.services.lambda.runtime.events.KafkaEvent;
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
     * Se cambia la firma para recibir el evento nativo de AWS (KafkaEvent),
     * lo cual le da acceso al key, partition y offset.
     *
     * @param kafkaEvent el evento nativo de Kafka de AWS con los records y metadata
     * @return Response con estadísticas de procesamiento
     */
    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response processMskBatch(KafkaEvent kafkaEvent) {
        long totalRecords = kafkaEvent.getRecords().values().stream()
                .mapToLong(List::size)
                .sum();
        log.infof("Received MSK batch with %d total messages", totalRecords);

        long startTime = System.currentTimeMillis();

        // Procesamiento funcional paralelo:
        // 1. Aplanar Map<String, List<Record>> a Stream<Record>
        // 2. Procesar cada record con su metadata
        List<ProcessResult> results = kafkaEvent.getRecords().values().stream()
                .flatMap(List::stream)
                .parallel()
                .map(this::processKafkaRecord)
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
                            log.infof("Sent %d results to MSK output topic", successResults.size());
                        }
                    });
        }

        long duration = System.currentTimeMillis() - startTime;

        // Retornar estadísticas
        Map<String, Object> response = new HashMap<>();
        response.put("processedRecords", totalRecords);
        response.put("successCount", successResults.size());
        response.put("failureCount", errors.size());
        response.put("errors", errors);
        response.put("durationMs", duration);
        response.put("timestamp", System.currentTimeMillis());

        log.infof("MSK batch completed: %d success, %d failures in %d ms",
                successResults.size(), errors.size(), duration);

        return Response.ok(response).build();
    }

    /**
     * Función que procesa un solo record de Kafka, extrayendo la metadata
     * y el contenido antes de pasarlo al pipeline de negocio.
     */
    private ProcessResult processKafkaRecord(KafkaEvent.KafkaEventRecord record) {
        try {
            // 1. Extraer Metadata de Kafka (solución al problema de acceso a metadata)
            String key = record.getKey();
            int partition = record.getPartition();
            long offset = record.getOffset();
            String topic = record.getTopic();

            // 2. Decodificar el valor (está en Base64, como lo manda AWS)
            String valueJson = new String(java.util.Base64.getDecoder().decode(record.getValue()));

            // 3. Mapear el contenido del mensaje (SentryMessageInput)
            SentryMessageInput input = objectMapper.readValue(valueJson, SentryMessageInput.class);

            // 4. Ejecutar la lógica de negocio (manteniendo el pipeline funcional)
            ProcessResult result = processMessageFunctional(input);

            // 5. Loguear la metadata para trazabilidad
            log.infof("Processed KAFKA record: Topic=%s, Partition=%d, Offset=%d, Key=%s",
                    topic, partition, offset, key);

            return result;

        } catch (Exception e) {
            // Incluir metadata en el log de error
            log.errorf(e, "Error processing Kafka record: topic=%s, key=%s, offset=%d",
                    record.getTopic(), record.getKey(), record.getOffset());

            // Adjuntar metadata al error para trazabilidad
            return ProcessResult.failure(String.format("Record Topic %s, Key %s, Offset %d: %s",
                    record.getTopic(), record.getKey(), record.getOffset(), e.getMessage()));
        }
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
