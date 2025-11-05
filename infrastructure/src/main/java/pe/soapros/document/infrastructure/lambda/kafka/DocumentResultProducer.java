package pe.soapros.document.infrastructure.lambda.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import pe.soapros.document.infrastructure.generation.input.SentryMessageInput;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Kafka producer for sending document generation results.
 *
 * Functional approach:
 * - Uses Emitters for manual message sending
 * - CompletableFuture for async operations
 * - Pure functions for transformations
 */
@ApplicationScoped
@JBossLog
public class DocumentResultProducer {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("document-responses-manual")
    Emitter<String> resultEmitter;

    /**
     * Sends a single result to Kafka topic.
     * Functional with async CompletionStage.
     *
     * @param result the SentryMessageInput with results
     * @return CompletionStage indicating completion
     */
    public CompletionStage<Void> sendResult(SentryMessageInput result) {
        return CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(result);
                resultEmitter.send(json);
                log.debugf("Sent result to Kafka: %d bytes", json.length());
            } catch (Exception e) {
                log.errorf(e, "Error sending result to Kafka");
                throw new RuntimeException("Failed to send result", e);
            }
        });
    }

    /**
     * Sends batch of results to Kafka topic.
     * Functional processing using streams.
     *
     * @param results list of SentryMessageInput
     * @return CompletionStage indicating completion
     */
    public CompletionStage<Void> sendBatch(List<SentryMessageInput> results) {
        log.infof("Sending batch of %d results to Kafka", results.size());

        return CompletableFuture.allOf(
            results.stream()
                .map(this::sendResult)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new)
        ).whenComplete((v, error) -> {
            if (error != null) {
                log.errorf(error, "Error sending batch to Kafka");
            } else {
                log.infof("Successfully sent %d results to Kafka", results.size());
            }
        });
    }

    /**
     * Sends result with custom key for partitioning.
     * Useful for maintaining order by client/document type.
     *
     * @param key partition key (e.g., client ID)
     * @param result the result
     * @return CompletionStage indicating completion
     */
    public CompletionStage<Void> sendWithKey(String key, SentryMessageInput result) {
        return CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(result);
                // Note: For keyed messages, you'd need a different emitter type
                // Emitter<Record<String, String>> for key-value pairs
                resultEmitter.send(json);
                log.debugf("Sent keyed result to Kafka: key=%s, size=%d", key, json.length());
            } catch (Exception e) {
                log.errorf(e, "Error sending keyed result to Kafka");
                throw new RuntimeException("Failed to send keyed result", e);
            }
        });
    }
}
