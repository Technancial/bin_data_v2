package pe.soapros.document.infrastructure.lambda.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import pe.soapros.document.infrastructure.generation.input.SentryMessageInput;

@Data
public class KafkaMessageWrapper {

    // Contenido decodificado del mensaje de Kafka
    @JsonProperty("content")
    private SentryMessageInput content;

    // Metadata de Kafka
    @JsonProperty("partition")
    private int partition;

    @JsonProperty("offset")
    private long offset;

    @JsonProperty("key")
    private String key;

}
