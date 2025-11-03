package pe.soapros.document.infrastructure.lambda.kafka;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import pe.soapros.document.application.GenerateDocumentUseCase;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.generation.input.TemplateData;
import pe.soapros.document.infrastructure.mapper.TemplateDataMapper;
import pe.soapros.document.infrastructure.qualifier.Pdf;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Kafka consumer for asynchronous document generation.
 * Consumes TemplateData messages from 'document-requests' topic and publishes
 * results to 'document-results' topic.
 */
@ApplicationScoped
@JBossLog
public class DocumentKafkaConsumer {

    @Inject
    @Pdf
    GenerateDocumentUseCase generateDocumentUseCase;

    @Inject
    @Channel("document-results")
    Emitter<DocumentResult> resultEmitter;

    /**
     * Consumes document generation requests from Kafka.
     * Processes each request asynchronously and publishes the result.
     *
     * @param data the template data from Kafka message
     * @return CompletionStage for async processing
     */
    @Incoming("document-requests")
    @Blocking
    public CompletionStage<Void> consume(TemplateData data) {
        return CompletableFuture.runAsync(() -> {
            String requestId = data.getTemplatePath(); // Use a better ID in production
            log.infof("Received document generation request: %s", requestId);

            try {
                // Execute document generation
                byte[] pdfBytes = generateDocumentUseCase.execute(
                        TemplateDataMapper.from(data.getData(), data.getImages(), data.getTemplatePath())
                );

                // Encode as Base64 for transmission
                String base64Document = Base64.getEncoder().encodeToString(pdfBytes);

                // Create success result
                DocumentResult result = new DocumentResult(
                        requestId,
                        "SUCCESS",
                        base64Document,
                        null
                );

                // Publish result to output topic
                resultEmitter.send(result);

                log.infof("Successfully generated document for request: %s (%d bytes)",
                        requestId, pdfBytes.length);

            } catch (DocumentGenerationException e) {
                log.errorf(e, "Failed to generate document for request: %s", requestId);

                // Create error result
                DocumentResult result = new DocumentResult(
                        requestId,
                        "FAILED",
                        null,
                        e.getMessage()
                );

                // Publish error result
                resultEmitter.send(result);
            }
        });
    }
}

/**
 * DTO representing the result of a document generation request.
 */
class DocumentResult {
    public String requestId;
    public String status;
    public String base64Document;
    public String errorMessage;

    public DocumentResult(String requestId, String status, String base64Document, String errorMessage) {
        this.requestId = requestId;
        this.status = status;
        this.base64Document = base64Document;
        this.errorMessage = errorMessage;
    }
}
